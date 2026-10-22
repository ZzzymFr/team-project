"""下载官方 MathWriting 并准备 train.py 使用的本地目录（仅依赖标准库）。

python data.py                         # 完整版，约 2.9 GB
python data.py --sample                # 示例版，约 1.5 MB
python data.py --splits train valid test synthetic

来源：https://github.com/google-research/google-research/tree/master/mathwriting
"""

import argparse
import shutil
import sys
import tarfile
import time
import urllib.error
import urllib.request
from pathlib import Path, PurePosixPath


BASE_URL = "https://storage.googleapis.com/mathwriting_data/"
DATA_DIR = Path(__file__).resolve().parent / "data"
CHUNK_SIZE = 1024 * 1024


def download(url: str, destination: Path) -> Path:
    """分块下载；中断后保留 .part，下次用 HTTP Range 继续。"""
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, method="HEAD")
    with urllib.request.urlopen(request, timeout=60) as response:
        total = int(response.headers["Content-Length"])
        etag = response.headers.get("ETag")
    if destination.is_file() and destination.stat().st_size == total:
        print(f"复用已下载压缩包: {destination}")
        return destination

    partial = destination.with_suffix(destination.suffix + ".part")
    for attempt in range(3):
        offset = partial.stat().st_size if partial.exists() else 0
        if offset > total:
            raise RuntimeError(f"临时文件超过远端大小，请检查并移走后重试: {partial}")
        if offset == total:
            partial.replace(destination)
            return destination
        headers = {"Accept-Encoding": "identity"}
        if offset:
            headers["Range"] = f"bytes={offset}-"
            if etag:
                headers["If-Range"] = etag
        try:
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=60) as response:
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
            return destination
        except (OSError, urllib.error.URLError) as error:
            if attempt == 2:
                raise
            print(f"下载中断，将重试 ({attempt + 1}/2): {error}")
            time.sleep(2)
    raise RuntimeError("下载失败")


def extract(archive_path: Path, output: Path, splits: list[str]) -> dict[str, int]:
    """逐项读取压缩包，只写选定分区的普通 InkML 文件及说明文档。"""
    output.mkdir(parents=True, exist_ok=True)
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
    return counts


def main() -> None:
    parser = argparse.ArgumentParser(description="下载并解压官方 MathWriting 数据集")
    parser.add_argument("--sample", action="store_true", help="只下载示例版，用于验证训练流程")
    parser.add_argument("--out", type=Path, help="数据集根目录，默认位于脚本旁的 data/ 下")
    parser.add_argument("--splits", nargs="+", choices=["train", "valid", "test", "synthetic"],
                        default=["train", "valid"], help="要解压的分区，默认 train valid")
    args = parser.parse_args()
    name = "mathwriting-2024-excerpt" if args.sample else "mathwriting-2024"
    output = (args.out if args.out is not None else DATA_DIR / name).resolve()
    archive_path = DATA_DIR / "downloads" / f"{name}.tgz"
    print(f"数据集: {name}\n输出目录: {output}")
    print("官方按整包提供下载；--splits 仅控制解压内容，不减少下载量。")
    archive_path = download(BASE_URL + archive_path.name, archive_path)
    counts = extract(archive_path, output, args.splits)
    for split, count in counts.items():
        print(f"{split}: {count} 个样本")
    print("数据准备完成。")
    if "train" in counts and "valid" in counts:
        print(f'训练命令: python "{Path(__file__).with_name("train.py")}" --data "{output}"')


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n已中断，再次运行相同命令可继续下载。", file=sys.stderr)
        sys.exit(130)
    except (OSError, ValueError, RuntimeError, tarfile.TarError) as error:
        print(f"数据准备失败: {error}", file=sys.stderr)
        sys.exit(1)
