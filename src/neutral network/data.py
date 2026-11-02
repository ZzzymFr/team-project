"""下载并混合 OCR 图文数据，或独立下载 DeepMind 数学文本；支持缓存与断点续传。

python data.py                         # 下载四个数据源（含 CROHME 2023），输出 data/mixed
python data.py --sample                # 示例版，约 1.5 MB
python data.py --sources mathwriting   # 只准备 MathWriting（含 synthetic）
python data.py --sources deepmind      # 下载独立数学问答文本，不加入 OCR 混合清单
python data.py --sources crohme2023    # 下载/解压 CROHME，独立输出 data/crohme2023-mixed

来源：https://github.com/google-research/google-research/tree/master/mathwriting
"""

import argparse
import hashlib
import html
import json
import os
import re
import shutil
import ssl
import sys
import tarfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path, PurePosixPath


BASE_URL = "https://storage.googleapis.com/mathwriting_data/"
DEEPMIND_FILENAME = "mathematics_dataset-v1.0.tar.gz"
DEEPMIND_URL = ("https://storage.googleapis.com/mathematics-dataset/" + DEEPMIND_FILENAME
                + "?generation=1565196705731724")
DEEPMIND_MD5 = "c5f9590beaf2d2445b55240ce0dab9a5"
CROHME_URL = "https://zenodo.org/records/8428035/files/CROHME23.zip?download=1"
CROHME_MD5 = "9eb899ddb0e87c80a17f489bf6162c33"
DATA_DIR = Path(__file__).resolve().parent / "data"
CHUNK_SIZE = 1024 * 1024
HF_SOURCES = {
    "unimer": ("wanderkid/UniMER_Dataset", "2343ddd963290469da36ca83e3a56c66e068add9", ["*.zip", "README.md"]),
    "hme100k-hf": ("ConstantHao/HME100K", "68ea96bd2527aac3799f94803a5f86b92fd9c2d2", ["data/*.parquet", "README.md"]),
}


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def file_stamp(path: Path) -> dict:
    stat = path.stat()
    return {"size": stat.st_size, "mtime_ns": stat.st_mtime_ns}


def download(url: str, destination: Path) -> Path:
    """分块下载；中断后保留 .part，下次用 HTTP Range 继续。"""
    destination.parent.mkdir(parents=True, exist_ok=True)
    receipt = destination.with_suffix(destination.suffix + ".download.json")
    if destination.is_file() and receipt.exists():
        saved = json.loads(receipt.read_text())
        if saved == {"url": url, "file": file_stamp(destination)}:
            print(f"复用已下载文件: {destination}")
            return destination
    context = ssl.create_default_context()
    if Path("/etc/ssl/cert.pem").is_file():
        context.load_verify_locations("/etc/ssl/cert.pem")
    request = urllib.request.Request(url, method="HEAD")
    with urllib.request.urlopen(request, timeout=60, context=context) as response:
        total = int(response.headers["Content-Length"])
        etag = response.headers.get("ETag")
    if destination.is_file() and destination.stat().st_size == total:
        print(f"复用已下载压缩包: {destination}")
        write_json(receipt, {"url": url, "file": file_stamp(destination)})
        return destination

    partial = destination.with_suffix(destination.suffix + ".part")
    for attempt in range(3):
        offset = partial.stat().st_size if partial.exists() else 0
        if offset > total:
            raise RuntimeError(f"临时文件超过远端大小，请检查并移走后重试: {partial}")
        if offset == total:
            partial.replace(destination)
            write_json(receipt, {"url": url, "file": file_stamp(destination)})
            return destination
        headers = {"Accept-Encoding": "identity"}
        if offset:
            headers["Range"] = f"bytes={offset}-"
            if etag:
                headers["If-Range"] = etag
        try:
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=60, context=context) as response:
                if response.status == 206:
                    expected = f"bytes {offset}-{total - 1}/{total}"
                    if response.headers.get("Content-Range") != expected:
                        raise RuntimeError("服务器返回了不匹配的下载范围，已停止下载。")
                elif response.status == 200:
                    offset = 0  # 服务器不支持续传或文件已更新，重新下载。
                    total = int(response.headers["Content-Length"])
                else:
                    raise RuntimeError(f"意外的 HTTP 状态: {response.status}")
                received = offset
                last_print = 0.0
                with partial.open("ab" if offset else "wb") as output:
                    while chunk := response.read(CHUNK_SIZE):
                        output.write(chunk)
                        received += len(chunk)
                        now = time.monotonic()
                        if now - last_print >= 1 or received == total:
                            print(f"\r下载: {received / total:.1%} "
                                  f"({received / 1024**2:.1f}/{total / 1024**2:.1f} MiB)",
                                  end="", flush=True)
                            last_print = now
                print()
                if received != total:
                    raise OSError(f"下载不完整: {received}/{total} 字节")
            partial.replace(destination)
            write_json(receipt, {"url": url, "file": file_stamp(destination)})
            return destination
        except (OSError, urllib.error.URLError) as error:
            if attempt == 2:
                raise
            print(f"下载中断，将重试 ({attempt + 1}/2): {error}")
            time.sleep(2)
    raise RuntimeError("下载失败")


def download_ranges(url: str, destination: Path, workers: int = 8, part_size: int = 32 * 1024**2) -> Path:
    """并发下载支持 Range 的大文件；每段校验范围，可中断续传。"""
    receipt = destination.with_suffix(destination.suffix + ".download.json")
    if receipt.exists() and destination.is_file():
        if json.loads(receipt.read_text()) == {"url": url, "file": file_stamp(destination)}:
            return destination
    with urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=60) as response:
        total, etag = int(response.headers["Content-Length"]), response.headers.get("ETag")
    folder = destination.with_suffix(destination.suffix + ".ranges")
    folder.mkdir(parents=True, exist_ok=True)
    marker = folder / "source.json"
    identity = {"url": url, "size": total, "etag": etag, "part_size": part_size}
    if marker.exists() and json.loads(marker.read_text()) != identity:
        raise ValueError(f"分段下载来源已变化，请检查旧缓存: {folder}")
    write_json(marker, identity)
    ranges = [(start, min(start + part_size, total) - 1) for start in range(0, total, part_size)]
    parts = [folder / f"{i:05d}.part" for i in range(len(ranges))]
    # 复用此前单连接下载完成的整段。
    partial = destination.with_suffix(destination.suffix + ".part")
    if partial.is_file() and partial.stat().st_size <= total:
        with partial.open("rb") as source:
            for path, (start, end) in zip(parts, ranges):
                if end >= partial.stat().st_size:
                    break
                if not path.exists():
                    source.seek(start)
                    path.write_bytes(source.read(end - start + 1))

    def fetch(index):
        start, end = ranges[index]
        path = parts[index]
        size = end - start + 1
        for attempt in range(3):
            offset = path.stat().st_size if path.exists() else 0
            if offset == size:
                return size
            if offset > size:
                raise ValueError(f"分段大小异常: {path}")
            headers = {"Range": f"bytes={start + offset}-{end}", "Accept-Encoding": "identity"}
            if etag:
                headers["If-Range"] = etag
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60) as response:
                    expected = f"bytes {start + offset}-{end}/{total}"
                    if response.status != 206 or response.headers.get("Content-Range") != expected:
                        raise ValueError(f"分段下载范围不匹配: {response.headers.get('Content-Range')}")
                    with path.open("ab" if offset else "wb") as output:
                        shutil.copyfileobj(response, output, CHUNK_SIZE)
                if path.stat().st_size != size:
                    raise OSError(f"分段未完成: {path}")
                return size
            except OSError:
                if attempt == 2:
                    raise
                time.sleep(2)

    completed = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for future in as_completed([pool.submit(fetch, i) for i in range(len(parts))]):
            completed += future.result()
            print(f"已完成下载分段: {completed / total:.1%}", flush=True)
    assembled = destination.with_suffix(destination.suffix + ".assembled")
    with assembled.open("wb") as output:
        for path in parts:
            with path.open("rb") as source:
                shutil.copyfileobj(source, output, CHUNK_SIZE)
    if assembled.stat().st_size != total:
        raise OSError("合并后的下载大小不一致")
    assembled.replace(destination)
    write_json(receipt, {"url": url, "file": file_stamp(destination)})
    for path in parts:
        path.unlink()
    marker.unlink()
    folder.rmdir()
    partial.unlink(missing_ok=True)
    return destination


def download_crohme2023() -> Path:
    """下载官方 CROHME 2023 发布包，按发布页 MD5 校验并记录 SHA-256。"""
    archive = DATA_DIR / "downloads" / "CROHME23.zip"
    marker = archive.with_suffix(".verified.json")
    if marker.exists() and archive.is_file():
        saved = json.loads(marker.read_text(encoding="utf-8"))
        if (saved.get("url") == CROHME_URL and saved.get("md5") == CROHME_MD5
                and saved.get("file") == file_stamp(archive)):
            print(f"复用已校验 CROHME 2023: {archive}", flush=True)
            return archive
    download_ranges(CROHME_URL, archive)
    print("校验 CROHME 2023 压缩包……", flush=True)
    md5, sha256 = hashlib.md5(), hashlib.sha256()
    with archive.open("rb") as stream:
        while chunk := stream.read(CHUNK_SIZE):
            md5.update(chunk)
            sha256.update(chunk)
    if md5.hexdigest() != CROHME_MD5:
        raise ValueError(f"CROHME 2023 压缩包 MD5 不匹配: {archive}")
    write_json(marker, {"url": CROHME_URL, "md5": md5.hexdigest(),
                        "sha256": sha256.hexdigest(), "file": file_stamp(archive)})
    return archive


def prepare_deepmind() -> dict:
    """下载并校验官方数学问答压缩包；它没有手写图片，不能作为 OCR 清单。"""
    archive = DATA_DIR / "downloads" / DEEPMIND_FILENAME
    marker = DATA_DIR / "deepmind" / "download.json"
    if marker.exists() and archive.is_file():
        saved = json.loads(marker.read_text(encoding="utf-8"))
        if (saved.get("url") == DEEPMIND_URL and saved.get("md5") == DEEPMIND_MD5
                and saved.get("file") == file_stamp(archive)):
            print(f"复用已校验 DeepMind 数学文本: {archive}", flush=True)
            return saved
    download(DEEPMIND_URL, archive)
    print("校验 DeepMind 压缩包 MD5 / SHA-256……", flush=True)
    md5, sha256 = hashlib.md5(), hashlib.sha256()
    with archive.open("rb") as stream:
        while chunk := stream.read(CHUNK_SIZE):
            md5.update(chunk)
            sha256.update(chunk)
    if md5.hexdigest() != DEEPMIND_MD5:
        raise ValueError(f"DeepMind 压缩包校验失败，文件已保留供检查: {archive}")
    report = {"dataset": "DeepMind Mathematics Dataset v1.0", "modality": "text",
              "url": DEEPMIND_URL, "archive": str(archive.resolve()),
              "file": file_stamp(archive), "md5": md5.hexdigest(), "sha256": sha256.hexdigest(),
              "source": "https://github.com/google-deepmind/mathematics_dataset",
              "format": "tar.gz; original text files contain alternating question/answer lines",
              "ocr_compatible": False, "extracted": False}
    write_json(marker, report)
    print(f"DeepMind 文本下载完成: {archive}\n这是数学问答文本，未解压或加入 OCR 训练清单。", flush=True)
    return report


def extract(archive_path: Path, output: Path, splits: list[str]) -> dict[str, int]:
    """逐项读取压缩包，只写选定分区的普通 InkML 文件及说明文档。"""
    output.mkdir(parents=True, exist_ok=True)
    marker = output / ".extracted.json"
    saved = json.loads(marker.read_text()) if marker.exists() else {}
    if saved.get("archive") == file_stamp(archive_path):
        counts = saved.get("counts", {})
        if all(split in counts and sum(1 for _ in (output / split).glob("*.inkml")) == counts[split]
               for split in splits):
            print(f"复用已解压 MathWriting: {output}")
            return {split: counts[split] for split in splits}
    root = output.resolve()
    counts = dict.fromkeys(splits, 0)
    for split in splits:
        folder = output / split
        if folder.is_symlink() or not folder.resolve().is_relative_to(root):
            raise ValueError(f"数据目录不能指向输出目录之外: {folder}")
        folder.mkdir(exist_ok=True)
        # 防止上次基于不完整数据建立的索引继续被 train.py 复用。
        (output / f"index_{split}.json").unlink(missing_ok=True)

    print(f"正在解压 {', '.join(splits)}，完整版可能需要几分钟……")
    expected_root = archive_path.name.removesuffix(".tgz")
    with tarfile.open(archive_path, mode="r|gz") as archive:
        for member in archive:
            parts = PurePosixPath(member.name).parts
            if not member.isfile() or not parts or parts[0] != expected_root:
                continue
            relative = parts[1:]
            is_ink = (len(relative) == 2 and relative[0] in counts
                      and relative[1].endswith(".inkml"))
            is_readme = len(relative) == 1 and relative[0].lower() in {
                "readme", "readme.md", "readme.txt", "license", "license.txt"
            }
            if not (is_ink or is_readme):
                continue
            if any(p in {".", ".."} or "\\" in p or ":" in p for p in relative):
                raise ValueError(f"压缩包中的路径不合法: {member.name}")
            target = output.joinpath(*relative)
            temporary = target.with_suffix(target.suffix + ".part")
            for path in (target, temporary):
                if path.is_symlink() or not path.resolve().is_relative_to(root):
                    raise ValueError(f"解压目标超出数据目录: {path}")
            if not target.is_file() or target.stat().st_size != member.size:
                with archive.extractfile(member) as source, temporary.open("wb") as dest:
                    shutil.copyfileobj(source, dest, CHUNK_SIZE)
                if temporary.stat().st_size != member.size:
                    raise OSError(f"解压不完整: {member.name}")
                temporary.replace(target)
            if is_ink:
                counts[relative[0]] += 1
                if sum(counts.values()) % 10000 == 0:
                    print(f"已处理 {sum(counts.values())} 个样本", flush=True)
    missing = [split for split, count in counts.items() if count == 0]
    if missing:
        raise RuntimeError(f"压缩包中未找到所需数据分区: {', '.join(missing)}")
    previous = saved.get("counts", {}) if saved.get("archive") == file_stamp(archive_path) else {}
    write_json(marker, {"archive": file_stamp(archive_path), "counts": previous | counts})
    return counts


def download_hf(name: str) -> Path:
    """固定版本下载；完整缓存不访问网络，未完成文件由 HF SDK 续传。"""
    from huggingface_hub import snapshot_download
    repo, revision, patterns = HF_SOURCES[name]
    destination = DATA_DIR / "downloads" / name
    marker = destination / ".download-complete.json"
    if marker.exists():
        saved = json.loads(marker.read_text())
        if saved.get("revision") == revision and all(
            (destination / filename).is_file() and file_stamp(destination / filename) == stamp
            for filename, stamp in saved["files"].items()
        ):
            print(f"复用已下载 {name}: {destination}", flush=True)
            return destination
    snapshot_download(repo_id=repo, repo_type="dataset", revision=revision,
                      allow_patterns=patterns, local_dir=destination, max_workers=2, token=False)
    files = {str(p.relative_to(destination)): file_stamp(p)
             for pattern in patterns for p in destination.glob(pattern) if p.is_file()}
    if not files:
        raise RuntimeError(f"{name} 下载没有产生数据文件")
    write_json(marker, {"repo": repo, "revision": revision, "files": files})
    return destination


def safe_target(root: Path, relative: str) -> Path:
    parts = PurePosixPath(relative).parts
    if not parts or PurePosixPath(relative).is_absolute() or any(
        part in {".", ".."} or "\\" in part or ":" in part for part in parts
    ):
        raise ValueError(f"无效压缩包路径: {relative}")
    target = root.joinpath(*parts)
    if target.is_symlink() or not target.resolve().is_relative_to(root.resolve()):
        raise ValueError(f"压缩包路径越界: {relative}")
    return target


def extract_zip(archive_path: Path, output: Path, rebuild: bool = False, workers: int = 1) -> None:
    marker = output / f".{archive_path.stem}.extracted.json"
    if not rebuild and marker.exists() and json.loads(marker.read_text()) == file_stamp(archive_path):
        print(f"复用已解压 {archive_path.name}", flush=True)
        return
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive_path) as archive:
        def extract_member(member):
            target = safe_target(output, member.filename)
            if member.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                return
            if (member.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError(f"不支持压缩包符号链接: {member.filename}")
            if target.is_file() and target.stat().st_size == member.file_size:
                return
            target.parent.mkdir(parents=True, exist_ok=True)
            temporary = target.with_suffix(target.suffix + ".part")
            if temporary.is_symlink():
                raise ValueError(f"临时文件不能是符号链接: {temporary}")
            with archive.open(member) as source, temporary.open("wb") as dest:
                shutil.copyfileobj(source, dest, CHUNK_SIZE)
            temporary.replace(target)
        members = archive.infolist()
        if workers == 1:
            for i, member in enumerate(members, 1):
                extract_member(member)
                if i % 50000 == 0:
                    print(f"解压 {archive_path.name}: {i}/{len(members)} 个文件", flush=True)
        else:
            # 先建立父目录，避免 Windows 在并发创建目录时解析尚不存在的路径。
            for parent in sorted({str(PurePosixPath(m.filename).parent) for m in members
                                  if not m.is_dir()}):
                if parent != ".":
                    safe_target(output, parent).mkdir(parents=True, exist_ok=True)
            # 限制待处理任务数，避免为数十万个小文件一次创建所有 Future。
            with ThreadPoolExecutor(max_workers=workers) as pool:
                for start in range(0, len(members), 1000):
                    list(pool.map(extract_member, members[start:start + 1000]))
                    if start % 10000 == 0:
                        print(f"解压 {archive_path.name}: {min(start + 1000, len(members))}/{len(members)} 个文件", flush=True)
    write_json(marker, file_stamp(archive_path))


def label_key(label: str) -> str:
    # 与训练使用同一规则，防止 cos / c o s / \cos 分落训练集和验证集。
    from labels import tokenize_label
    try:
        tokens = tokenize_label(label)
    except ValueError:
        # 损坏标签仍由训练加载器过滤；保留其原始身份，不与合法公式混同。
        tokens = ["<invalid-label>", label]
    return hashlib.sha256("\x1f".join(tokens).encode()).hexdigest()


def image_hash(path: Path) -> str:
    from PIL import Image, ImageOps
    with Image.open(path) as source:
        rgba = ImageOps.exif_transpose(source).convert("RGBA")
        gray = Image.alpha_composite(Image.new("RGBA", rgba.size, "white"), rgba).convert("L")
        return hashlib.sha256(str(gray.size).encode() + gray.tobytes()).hexdigest()


def image_record(path: Path, label: str, source: str, original_split: str) -> dict:
    if not path.is_file():
        raise FileNotFoundError(path)
    return {"path": str(path.resolve()), "format": "image", "latex": label,
            "source": source, "original_split": original_split,
            "content_hash": "image:" + image_hash(path), "label_hash": label_key(label)}


def heldout_split(label: str, valid_fraction: float) -> str:
    # 相同公式的不同图片一起划分，重跑不改变划分。
    return "valid" if int(label_key(label)[:16], 16) / 2**64 < valid_fraction else "train"


def prepare_manifests(name: str, fingerprint: dict, records, rebuild: bool = False) -> dict[str, Path]:
    folder = (DATA_DIR / "manifests" / name).resolve()
    marker = folder / "complete.json"
    paths = {split: folder / f"{split}.jsonl" for split in ("train", "valid", "test")}
    if not rebuild and marker.exists():
        saved = json.loads(marker.read_text())
        if saved.get("fingerprint") == fingerprint and all(
            p.is_file() and file_stamp(p) == saved.get("files", {}).get(split) for split, p in paths.items()
        ):
            print(f"复用已转换 {name}: {saved['counts']}", flush=True)
            return paths
    folder.mkdir(parents=True, exist_ok=True)
    handles = {split: path.with_suffix(".jsonl.tmp").open("w", encoding="utf-8") for split, path in paths.items()}
    counts = Counter()
    try:
        for split, row in records():
            row["path"] = os.path.relpath(row["path"], folder)
            handles[split].write(json.dumps(row, ensure_ascii=False) + "\n")
            counts[split] += 1
            if sum(counts.values()) % 50000 == 0:
                print(f"转换 {name}: {dict(counts)}", flush=True)
    finally:
        for handle in handles.values():
            handle.close()
    for path in paths.values():
        path.with_suffix(".jsonl.tmp").replace(path)
    write_json(marker, {"fingerprint": fingerprint, "counts": dict(counts),
                        "files": {split: file_stamp(p) for split, p in paths.items()}})
    return paths


def crohme_ink_label(path: Path) -> str | None:
    """只读取表达式级标签；不把符号级 annotation 当作完整公式。"""
    root = ET.parse(path).getroot()
    if not any((node.text or "").strip() for node in root.iter("{http://www.w3.org/2003/InkML}trace")):
        return None
    annotations = {node.get("type", "").lower(): "".join(node.itertext()).strip()
                   for node in root.findall("{*}annotation")}
    for name in ("normalizedlabel", "truth", "latex"):
        label = annotations.get(name, "")
        if label:
            if label.startswith("$$") and label.endswith("$$"):
                label = label[2:-2].strip()
            elif label.startswith("$") and label.endswith("$"):
                label = label[1:-1].strip()
            return label or None
    return None


def prepare_crohme2023(rebuild: bool = False):
    archive = download_crohme2023()
    output = DATA_DIR / "crohme2023"
    extract_zip(archive, output, rebuild, workers=8)
    root = output / "TC11_CROHME23"
    if not (root / "INKML").is_dir():
        raise ValueError(f"CROHME 发布包缺少 INKML 目录: {root}")

    def records():
        counts, skipped = Counter(), Counter()
        for original, split in (("train", "train"), ("val", "valid"), ("test", "test")):
            paths = sorted((root / "INKML" / original).rglob("*.inkml"))
            if not paths:
                raise ValueError(f"CROHME 缺少官方 {original} 分区")
            for path in paths:
                try:
                    label = crohme_ink_label(path)
                except ET.ParseError:
                    skipped[f"{split}/invalid_xml"] += 1
                    continue
                if label is None:
                    skipped[f"{split}/missing_label_or_traces"] += 1
                    continue
                counts[split] += 1
                # 每个表达式只使用一次轨迹，避免把配套 PNG 再当成额外样本。
                yield split, {"path": str(path.resolve()), "format": "inkml", "latex": label,
                              "source": "crohme2023", "original_split": original,
                              "content_hash": "inkml:" + hashlib.sha256(path.read_bytes()).hexdigest(),
                              "label_hash": label_key(label)}
        write_json(output / "conversion.json", {"counts": dict(counts), "skipped": dict(skipped),
                   "input": "INKML with expression-level LaTeX and traces; rendered by train.py",
                   "excluded_modality": "PNG-only / SymLG-only samples are downloaded but not imported"})
    return prepare_manifests("crohme2023", {"version": 1, "archive": file_stamp(archive)}, records, rebuild)


def prepare_mathwriting(root: Path, splits: list[str], rebuild: bool = False):
    def records():
        for split in splits:
            for path in sorted((root / split).glob("*.inkml")):
                content = path.read_bytes()
                match = re.search(r'<annotation type="normalizedLabel">(.*?)</annotation>',
                                  content.decode("utf-8"), re.S)
                if not match:
                    raise ValueError(f"InkML 缺少 normalizedLabel: {path}")
                label = html.unescape(match.group(1)).strip()
                yield ("train" if split == "synthetic" else split), {
                    "path": str(path.resolve()), "format": "inkml", "latex": label,
                    "source": "mathwriting", "original_split": split,
                    "content_hash": "inkml:" + hashlib.sha256(content).hexdigest(), "label_hash": label_key(label),
                }
    # 解压清单变化时重建，原始目录和文件不会移动。
    fingerprint = {"version": 1, "root": str(root), "splits": splits,
                   "extraction": json.loads((root / ".extracted.json").read_text())}
    return prepare_manifests("mathwriting", fingerprint, records, rebuild)


def prepare_unimer(download_root: Path, valid_fraction: float, rebuild: bool = False):
    root = DATA_DIR / "unimer"
    archives = [download_root / "UniMER-1M.zip", download_root / "UniMER-Test.zip"]
    for archive in archives:
        extract_zip(archive, root, rebuild)

    def records():
        groups = [(root / "UniMER-1M" / "images", root / "UniMER-1M" / "train.txt", "train")]
        groups += [(root / "UniMER-Test" / kind, root / "UniMER-Test" / f"{kind}.txt", "test")
                   for kind in ("spe", "cpe", "sce", "hwe")]
        for images, labels_path, split in groups:
            # 官方加载器用图片文件名的整数作为标签数组的零基下标；不能按目录顺序 zip。
            labels = labels_path.read_text(encoding="utf-8").split("\n")
            paths = sorted(images.glob("*.png"))
            if not paths:
                raise ValueError(f"UniMER 图片目录为空: {images}")
            empty = 0
            for path in paths:
                index = int(path.stem)
                if index >= len(labels):
                    raise ValueError(f"UniMER 图片编号超出标签范围: {path}")
                label = labels[index].strip()
                if not label:
                    empty += 1
                    continue
                row = image_record(path, label, "unimer", split)
                yield heldout_split(label, valid_fraction) if split == "train" else "test", row
            if empty:
                print(f"UniMER {images.name}: 跳过 {empty} 个空标签", flush=True)
    fingerprint = {"version": 1, "revision": HF_SOURCES["unimer"][1], "valid_fraction": valid_fraction,
                   "archives": {p.name: file_stamp(p) for p in archives}}
    return prepare_manifests("unimer", fingerprint, records, rebuild)


def prepare_hme100k(download_root: Path, valid_fraction: float, rebuild: bool = False):
    import pyarrow.parquet as pq
    root = DATA_DIR / "hme100k-hf"
    shards = sorted((download_root / "data").glob("*.parquet"))
    if not shards:
        raise ValueError("HME100K 没有 Parquet 数据文件")

    def records():
        for shard in shards:
            split = shard.name.split("-")[0]
            if split not in {"train", "test"}:
                raise ValueError(f"未知的 HME100K 分区: {shard}")
            for batch in pq.ParquetFile(shard).iter_batches(batch_size=128):
                for row_index, sample in enumerate(batch.to_pylist()):
                    # 用内容哈希命名，不依赖源数据中 Windows 风格的路径。
                    payload = sample["image"]["bytes"]
                    if not payload:
                        raise ValueError(f"Parquet 图片没有内嵌字节: {shard}, {row_index}")
                    digest = hashlib.sha256(payload).hexdigest()
                    path = root / split / digest[:2] / f"{digest}.jpg"
                    path.parent.mkdir(parents=True, exist_ok=True)
                    if not path.exists() or path.stat().st_size != len(payload):
                        temporary = path.with_suffix(".jpg.part")
                        temporary.write_bytes(payload)
                        temporary.replace(path)
                    label = sample["label"].strip()
                    if not label:
                        raise ValueError(f"HME100K 空标签: {shard}")
                    row = image_record(path, label, "hme100k-hf", split)
                    yield heldout_split(label, valid_fraction) if split == "train" else "test", row
    fingerprint = {"version": 1, "revision": HF_SOURCES["hme100k-hf"][1], "valid_fraction": valid_fraction,
                   "shards": {p.name: file_stamp(p) for p in shards}}
    return prepare_manifests("hme100k-hf", fingerprint, records, rebuild)


def mix_manifests(sources: dict, output: Path) -> dict:
    """保留测试/验证边界，去除像素相同的图片及字节相同的 InkML。"""
    from labels import NORMALIZATION_VERSION
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    inputs = {str(p): file_stamp(p) for paths in sources.values() for p in paths.values()}
    summary_path = output / "summary.json"
    if summary_path.exists():
        cached = json.loads(summary_path.read_text())
        if cached.get("normalization_version") == NORMALIZATION_VERSION and cached.get("inputs") == inputs and all(
            (output / name).is_file() and file_stamp(output / name) == stamp
            for name, stamp in cached.get("outputs", {}).items()
        ) and len(cached.get("outputs", {})) == 3:
            (output / ".preparing").unlink(missing_ok=True)
            print(f"复用已有混合清单: {output}", flush=True)
            return cached
    preparing = output / ".preparing"
    preparing.write_text("正在构建混合清单；请等待 data.py 完成。", encoding="utf-8")
    seen_content = set()
    heldout_labels = set()
    counts, skipped = Counter(), Counter()
    # 测试优先，再验证，再训练；后续分区不能包含前面已保留的内容或相同标签。
    for split in ("test", "valid", "train"):
        path = output / f"{split}.jsonl"
        temporary = path.with_suffix(".jsonl.tmp")
        split_labels = set()
        with temporary.open("w", encoding="utf-8") as dest:
            for source, manifests in sources.items():
                manifest = manifests[split]
                with manifest.open(encoding="utf-8") as stream:
                    for line in stream:
                        row = json.loads(line)
                        # 来源清单可能是旧缓存，始终按当前训练规则重新计算标签身份。
                        row["label_hash"] = label_key(row["latex"])
                        # 清单由上面的转换器从规范化路径生成；这里只需消除 ..，
                        # 避免对百万条记录反复逐级解析同一批父目录。
                        sample_path = Path(os.path.abspath(manifest.parent / row["path"]))
                        if not sample_path.is_file():
                            raise FileNotFoundError(f"缓存数据缺失: {sample_path}；请运行 data.py --rebuild")
                        if row["content_hash"] in seen_content:
                            skipped[f"{split}/{source}/duplicate"] += 1
                            continue
                        if row["label_hash"] in heldout_labels:
                            skipped[f"{split}/{source}/heldout_formula"] += 1
                            continue
                        seen_content.add(row["content_hash"])
                        split_labels.add(row["label_hash"])
                        row["path"] = os.path.relpath(sample_path, output)
                        dest.write(json.dumps(row, ensure_ascii=False) + "\n")
                        counts[f"{split}/{source}"] += 1
        temporary.replace(path)
        heldout_labels.update(split_labels)
        print(f"合并完成 {split}: {dict(counts)}", flush=True)
    report = {"normalization_version": NORMALIZATION_VERSION,
              "counts": dict(counts), "skipped": dict(skipped),
              "inputs": inputs,
              "outputs": {f"{split}.jsonl": file_stamp(output / f"{split}.jsonl")
                          for split in ("train", "valid", "test")},
              "deduplication": "decoded grayscale pixels for images; bytes for InkML; tokenized label overlap excluded across splits",
              "sources": {name: {split: str(p) for split, p in paths.items()} for name, paths in sources.items()}}
    write_json(output / "summary.json", report)
    preparing.unlink()
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="下载 OCR 图文数据并混合，或独立下载 DeepMind 数学文本")
    parser.add_argument("--sources", nargs="+", choices=["mathwriting", "unimer", "hme100k-hf", "deepmind", "crohme2023"],
                        help="默认包含 MathWriting、UniMER、HME100K 和 CROHME 2023")
    parser.add_argument("--sample", action="store_true", help="仅下载 MathWriting 小样本")
    parser.add_argument("--out", type=Path, help="混合数据清单输出目录，默认 data/mixed")
    parser.add_argument("--splits", nargs="+", choices=["train", "valid", "test", "synthetic"],
                        default=["train", "valid", "test", "synthetic"], help="MathWriting 要解压的分区")
    parser.add_argument("--valid-fraction", type=float, default=0.05,
                        help="从 UniMER/HME100K 训练集按公式固定划分验证集的比例")
    parser.add_argument("--rebuild", action="store_true", help="重建解压/转换缓存，不重新下载完整文件")
    args = parser.parse_args()
    if args.sources is None:
        args.sources = (["mathwriting"] if args.sample else
                        ["mathwriting", "unimer", "hme100k-hf", "crohme2023"])
    if args.sample and set(args.sources) & {"deepmind", "crohme2023"}:
        parser.error("--sample 仅适用于 MathWriting，不能与 deepmind/crohme2023 一起使用")
    if args.out and set(args.sources) == {"deepmind"}:
        parser.error("--out 是 OCR 混合清单目录，不适用于独立 DeepMind 文本下载")
    if not 0 < args.valid_fraction < 1:
        parser.error("valid-fraction 必须介于 0 和 1 之间")
    sources = ["mathwriting"] if args.sample else list(dict.fromkeys(args.sources))
    default_output = ("mixed-sample" if args.sample else "crohme2023-mixed"
                      if set(sources) - {"deepmind"} == {"crohme2023"} else "mixed")
    output = (args.out or DATA_DIR / default_output).resolve()
    manifests = {}
    for source in sources:
        print(f"准备数据源: {source}", flush=True)
        if source == "deepmind":
            prepare_deepmind()
        elif source == "crohme2023":
            manifests[source] = prepare_crohme2023(args.rebuild)
        elif source == "mathwriting":
            name = "mathwriting-2024-excerpt" if args.sample else "mathwriting-2024"
            archive = download(BASE_URL + name + ".tgz", DATA_DIR / "downloads" / (name + ".tgz"))
            root = DATA_DIR / name
            extract(archive, root, args.splits)
            manifests[source] = prepare_mathwriting(root, args.splits, args.rebuild)
        else:
            downloaded = download_hf(source)
            prepare = prepare_unimer if source == "unimer" else prepare_hme100k
            manifests[source] = prepare(downloaded, args.valid_fraction, args.rebuild)
    if not manifests:
        return
    report = mix_manifests(manifests, output)
    print(json.dumps(report["counts"], ensure_ascii=False, indent=2), flush=True)
    print(f'数据准备完成。训练命令: python "{Path(__file__).with_name("train.py")}" --data "{output}" --max-samples 0')


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n已中断；再次运行相同命令会复用完整文件并继续未完成的下载。", file=sys.stderr)
        sys.exit(130)
    except (OSError, ValueError, RuntimeError, tarfile.TarError, zipfile.BadZipFile, ImportError) as error:
        print(f"数据准备失败: {error}", file=sys.stderr)
        sys.exit(1)
