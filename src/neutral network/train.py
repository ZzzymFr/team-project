import argparse
from contextlib import contextmanager, redirect_stderr, redirect_stdout
from datetime import datetime
import html
import hashlib
import heapq
import json
import math
import random
import re
import sys
import time
import traceback
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image, ImageDraw
from torch.utils.data import DataLoader, Dataset, Sampler, Subset

from model import vital_mini, load_vocab, load_training_weights, vocab_size, pad_id, bos_id, eos_id, max_len
from preprocess import EmptyFormulaImageError, check_formula_image, load_formula_image

Learning_rate = 8e-5
minimum_learning_rate = 3e-6
EPOCHS = 5

IMG_H = 128                  # 图像统一高度;vital_pixel 总步长 16,所以是 8 行格子
STRIDE = 16                  # vital_pixel 的总下采样倍数,图像宽度要补成它的倍数
MARGIN, MAX_W, MIN_W = 8, 1024, 64
IMAGE_INVERT = False                 # 图片保留原始明暗，不自动反色。
INKML = "{http://www.w3.org/2003/InkML}"
VALIDATION_SOURCES = ("MathWriting", "UniMER", "HME100K")


def source_name(value: str | None, kind: str) -> str:
    """优先使用清单来源；未标注的图片单列，原始 InkML 属于 MathWriting。"""
    if value is None:
        return "MathWriting" if kind == "inkml" else "未标注"
    if not isinstance(value, str) or not value.strip():
        raise ValueError("source 必须为非空字符串")
    aliases = {"mathwriting": "MathWriting", "unimer": "UniMER",
               "hme100k": "HME100K", "hme100k-hf": "HME100K"}
    return aliases.get(value.strip().lower(), value.strip())

# ---------- 标签 -> token ----------
TOKEN_RE = re.compile(r"\\[a-zA-Z]+|\\.|\S", re.S)             # \frac 这类命令是一个 token,其余按单字符
# MathWriting 的 normalizedLabel 把函数名写成普通字母(sin、log...),词表里是 \sin 这样的命令
FUNC_RE = re.compile(r"(?<![a-zA-Z\\])(sin|cos|tan|log|ln|lim)")
ALIASES = {r"\le": r"\leq", r"\ge": r"\geq", r"\ne": r"\neq", r"\to": r"\rightarrow"}   # 同义写法统一成词表里的那个
ALIASES.update({r"\Box": r"\square", "□": r"\square"})
ALIASES.update({"⟨": r"\langle", "⟩": r"\rangle", "⊥": r"\perp",
                "‖": r"\|", r"\Vert": r"\|", r"\lVert": r"\|", r"\rVert": r"\|"})
LABEL_RE = re.compile(r'<annotation type="normalizedLabel">(.*?)</annotation>', re.S)
GREEK_ALIASES = dict(zip(
    "αβγδεζηθικλμνξοπρστυφχψω",
    ("\\" + name for name in "alpha beta gamma delta varepsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon varphi chi psi omega".split())))
GREEK_ALIASES.update(dict(zip("ΓΔΘΛΞΠΣΥΦΨΩ", ("\\" + name for name in
    "Gamma Delta Theta Lambda Xi Pi Sigma Upsilon Phi Psi Omega".split()))))
GREEK_ALIASES.update({"ϵ": r"\epsilon", "ϑ": r"\vartheta", "ϰ": r"\varkappa", "ϖ": r"\varpi",
                      "ϱ": r"\varrho", "ς": r"\varsigma", "ϕ": r"\phi"})
ALIASES.update(GREEK_ALIASES)
TEXT_START_RE = re.compile(r"\\text\s*\{")
TEXT_TOKEN_RE = re.compile(r"\\[a-zA-Z]+|\\.|\s+|\S", re.S)

def tokenize_label(label: str) -> list[str]:
    def math_tokens(text: str) -> list[str]:
        text = FUNC_RE.sub(lambda m: "\\" + m.group(1) + " ", text)
        # 自动大小定界符不改变符号含义，统一省略；文字区域仍保持原样。
        return [ALIASES.get(t, t) for t in TOKEN_RE.findall(text)
                if t not in {r"\left", r"\right"}]

    result, position = [], 0
    while match := TEXT_START_RE.search(label, position):
        result.extend(math_tokens(label[position:match.start()]))
        result.extend([r"\text", "{"])
        depth = 1
        for token_match in TEXT_TOKEN_RE.finditer(label, match.end()):
            token = token_match.group()
            if token == "{":
                depth += 1
            elif token == "}":
                depth -= 1
            # LaTeX 文字区域中的连续空白表示一个词间空格。
            result.append(" " if token.isspace() else token)
            if depth == 0:
                position = token_match.end()
                break
        else:
            raise ValueError(f"标签的 \\text 区域缺少右花括号: {label}")
    result.extend(math_tokens(label[position:]))
    return result

def build_index(root: Path, split: str) -> list[tuple[str, str]]:
    """扫描 split 目录,返回 [(文件名, normalizedLabel)];文件有几十万个,只扫一次,之后读缓存"""
    if not root.is_dir():
        raise FileNotFoundError(
            f"数据集根目录不存在: {root.resolve()}。--data 应指向包含 train/ 和 valid/ 的目录。"
        )
    split_dir = root / split
    if not split_dir.is_dir():
        raise FileNotFoundError(
            f"数据集子目录不存在: {split_dir.resolve()}。--data 应指向这些子目录的上一级目录。"
        )
    cache = root / f"index_{split}.json"
    if cache.exists():
        return [tuple(x) for x in json.loads(cache.read_text(encoding="utf-8"))]
    items = []
    for f in sorted(split_dir.glob("*.inkml")):
        with open(f, "rb") as fh:
            head = fh.read(4096).decode("utf-8", errors="ignore")   # 标签在文件开头,不用读后面的笔迹
        m = LABEL_RE.search(head)
        if m:
            items.append((f.name, html.unescape(m.group(1)).strip()))
    cache.write_text(json.dumps(items), encoding="utf-8")
    return items

# ---------- 笔迹 -> 图像 ----------
def read_strokes(path: Path) -> list[np.ndarray]:
    """InkML -> 每笔一个 [n,2] 数组;每个点是 'x y t',只用前两个值"""
    strokes = []
    for tr in ET.parse(path).getroot().iter(INKML + "trace"):
        pts = [p.split() for p in tr.text.strip().split(",")]
        strokes.append(np.array([[float(p[0]), float(p[1])] for p in pts if len(p) >= 2], dtype=np.float32))
    return strokes

def augment_strokes(strokes: list[np.ndarray], rng: random.Random) -> list[np.ndarray]:
    """随机旋转、剪切、缩放(在笔迹坐标上做,比在图像上做干净)"""
    a = math.radians(rng.uniform(-5, 5))
    rot = np.array([[math.cos(a), -math.sin(a)], [math.sin(a), math.cos(a)]], dtype=np.float32)
    shear = np.array([[1, rng.uniform(-0.2, 0.2)], [0, 1]], dtype=np.float32)
    scale = np.diag([rng.uniform(0.85, 1.15), rng.uniform(0.85, 1.15)]).astype(np.float32)
    m = (rot @ shear @ scale).T
    return [s @ m for s in strokes]

def render(strokes: list[np.ndarray], stroke_px: float = 3.0, ss: int = 2) -> torch.Tensor:
    """笔迹 -> [1,IMG_H,W],墨迹为 1、背景为 0。裁剪到墨迹外接矩形,等比缩放到固定高度;
    笔画粗细按输出像素固定(不随缩放变),ss 倍超采样后缩小做抗锯齿"""
    pts = np.concatenate(strokes)
    x0, y0 = pts.min(0)
    x1, y1 = pts.max(0)
    w0, h0 = max(float(x1 - x0), 1e-3), max(float(y1 - y0), 1e-3)
    h_eff = max(h0, 0.2 * w0)                                  # 只有一条横线时,避免被放大到离谱
    scale = min((IMG_H - 2 * MARGIN) / h_eff, (MAX_W - 2 * MARGIN) / w0)
    width = max(int(math.ceil((w0 * scale + 2 * MARGIN) / STRIDE)) * STRIDE, MIN_W)   # 宽度补成步长的倍数
    off = np.array([(width - w0 * scale) / 2, (IMG_H - h0 * scale) / 2], dtype=np.float32)   # 居中

    canvas = Image.new("L", (width * ss, IMG_H * ss), 0)
    draw = ImageDraw.Draw(canvas)
    r = stroke_px * ss / 2
    for s in strokes:
        p = ((s - np.array([x0, y0], dtype=np.float32)) * scale + off) * ss
        xy = [(float(a), float(b)) for a, b in p]
        if len(xy) > 1:
            draw.line(xy, fill=255, width=max(int(round(stroke_px * ss)), 1), joint="curve")
        for a, b in (xy[0], xy[-1]):                           # 两端补圆头,单点笔画也能画出来
            draw.ellipse((a - r, b - r, a + r, b + r), fill=255)
    canvas = canvas.resize((width, IMG_H), Image.BOX)
    return torch.from_numpy(np.asarray(canvas, dtype=np.float32) / 255.0).unsqueeze(0)

# ---------- 图片有效性缓存 ----------
IMAGE_CHECK_VERSION = 1              # 修改 check_formula_image 的判定规则时递增，使旧缓存失效

def load_image_check_cache(path: Path) -> dict[str, list]:
    """{图片路径: [字节数, mtime_ns, 是否有效, 错误信息]}；缓存缺失、损坏或版本不符时当作空"""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("version") == IMAGE_CHECK_VERSION and isinstance(data.get("entries"), dict):
            return data["entries"]
    except (OSError, ValueError):
        pass
    return {}

def save_image_check_cache(path: Path, entries: dict[str, list]) -> None:
    # 先写临时文件再替换，中途中断不会留下半个缓存；目录只读时放弃缓存，不影响训练。
    temp = path.with_name(path.name + ".tmp")
    try:
        temp.write_text(json.dumps({"version": IMAGE_CHECK_VERSION, "entries": entries}, ensure_ascii=False),
                        encoding="utf-8")
        temp.replace(path)
    except OSError as error:
        print(f"无法写入图片检查缓存 {path}: {error}", flush=True)

# ---------- 数据集 ----------
class MathWriting(Dataset):
    """兼容原 InkML 目录、混合 JSONL 清单及个人图片＋LaTeX 数据。"""

    def __init__(self, root: str, splits: list[str], vocab: dict[str, int], train: bool,
                 oov: str = "skip", max_samples: int | None = None,
                 image_roots: list[str] | None = None, personal_train: Path | None = None):
        self.root, self.train = Path(root).resolve(), train
        if personal_train is not None:
            personal_train = personal_train.resolve()
        if (self.root / ".preparing").exists():
            raise RuntimeError("混合数据还在准备中，请等待 data.py 完成")
        unk = vocab["<unk>"]
        self.items: list[tuple[str, str, list[int]]] = []      # (format, 绝对路径, token id)
        total = too_long = has_oov = 0
        seen_paths: dict[str, str] = {}
        source_by_path: dict[str, str] = {}
        roots = [self.root] + [Path(p).resolve() for p in (image_roots or [])]
        if self.train and personal_train is not None:
            if personal_train.is_dir() and any(
                p.name == "labels.jsonl" or p.suffix.lower() in {".png", ".jpg", ".jpeg", ".bmp", ".webp", ".tif", ".tiff"}
                for p in personal_train.rglob("*") if p.is_file()
            ):
                if not (personal_train / "labels.jsonl").is_file():
                    raise FileNotFoundError(f"个人训练目录缺少标签文件: {personal_train / 'labels.jsonl'}")
                roots.append(personal_train)
        for dataset_root in dict.fromkeys(roots):
            for split in splits:
                manifest = dataset_root / f"{split}.jsonl"
                if not manifest.is_file() and (dataset_root / split / "labels.jsonl").is_file():
                    manifest = dataset_root / split / "labels.jsonl"
                # 本地识别目录名为 val；保留原有 valid.jsonl / valid/ 的优先级。
                if split == "valid" and not manifest.is_file() and (dataset_root / "val" / "labels.jsonl").is_file():
                    manifest = dataset_root / "val" / "labels.jsonl"
                if dataset_root == personal_train:
                    if split != "train":
                        continue
                    manifest = dataset_root / "labels.jsonl"
                if manifest.is_file():
                    records = read_manifest(manifest)
                elif dataset_root == self.root:
                    records = (("inkml", str((dataset_root / split / name).resolve()), label, "MathWriting")
                               for name, label in build_index(dataset_root, split))
                else:
                    # 个人数据可以只提供 train.jsonl；不擅自拿训练图片作验证。
                    if split == "train":
                        raise FileNotFoundError(f"个人训练清单不存在: {manifest}")
                    continue
                for kind, path, label, source in records:
                    if path in seen_paths:
                        if seen_paths[path] != label:
                            raise ValueError(f"同一训练文件有不同标签: {path}")
                        if source_by_path[path] != source:
                            raise ValueError(f"同一文件有不同来源: {path}")
                        continue
                    seen_paths[path] = label
                    source_by_path[path] = source
                    total += 1
                    toks = tokenize_label(label)
                    if len(toks) + 2 > max_len:                    # 加上 <bos>/<eos>
                        too_long += 1
                        continue
                    if oov == "skip" and any(t not in vocab for t in toks):
                        has_oov += 1
                        continue
                    self.items.append((kind, path, [vocab.get(t, unk) for t in toks]))
        if max_samples:
            random.Random(0).shuffle(self.items)
            self.items = self.items[:max_samples]
        # 在创建 sampler、验证集签名和启动 worker 前排除无效图片。
        # 保留抽样结果，不用其他样本替换坏图，以免悄悄改变验证集抽样规则。
        self.invalid_images: list[tuple[str, str]] = []
        image_count = sum(kind == "image" for kind, _, _ in self.items)
        if image_count:
            print(f"{'train' if train else 'eval '} {splits}: 检查 {image_count} 张图片...", flush=True)
        # 检查结果按 (路径, 大小, mtime) 缓存；只有新增或改动过的图片才会重新解码。
        cache_path = self.root / "image_check_cache.json"
        cache = load_image_check_cache(cache_path) if image_count else {}
        cache_dirty = False
        usable = []
        for item in self.items:
            kind, path, _ = item
            if kind == "image":
                stat = Path(path).stat()
                stamp = [stat.st_size, stat.st_mtime_ns]
                entry = cache.get(path)
                if entry is None or entry[:2] != stamp:
                    try:
                        check_formula_image(Path(path))
                        entry = stamp + [True, ""]
                    except (EmptyFormulaImageError, OSError) as error:
                        entry = stamp + [False, str(error)]
                    cache[path] = entry
                    cache_dirty = True
                if not entry[2]:
                    self.invalid_images.append((path, entry[3]))
                    if len(self.invalid_images) <= 5:
                        print(f"跳过无效图片: {path} ({entry[3]})", flush=True)
                    continue
            usable.append(item)
        if cache_dirty:
            save_image_check_cache(cache_path, cache)
        self.items = usable
        # 在抽样、过滤之后按最终 items 对齐，保持原有三元组与 __getitem__ 接口。
        self.sources = [source_by_path[item[1]] for item in self.items]
        print(f"{'train' if train else 'eval '} {splits}: {len(self.items)}/{total} samples used "
              f"(too long: {too_long}, tokens outside tokeniser.json: {has_oov}, "
              f"invalid images: {len(self.invalid_images)}, oov={oov})")

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, i: int) -> tuple[torch.Tensor, list[int]]:
        kind, path, ids = self.items[i]
        if kind == "image":
            return load_formula_image(Path(path), invert=IMAGE_INVERT), ids
        strokes = read_strokes(Path(path))
        stroke_px = 3.0
        if self.train:                                         # 训练时随机扰动笔迹形状和粗细,验证时不扰动
            rng = random.Random()
            strokes = augment_strokes(strokes, rng)
            stroke_px = rng.uniform(2.0, 4.0)
        return render(strokes, stroke_px), ids


def read_manifest(path: Path):
    """每行 {image/path, latex, format?, source?}；文件路径相对于清单所在目录。"""
    with path.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
                if not isinstance(row, dict):
                    raise ValueError("每行必须是一个 JSON 对象")
                filename = row.get("image") or row["path"]
                label = row["latex"]
                kind = row.get("format", "image")
                if kind not in {"image", "inkml"} or not isinstance(label, str) or not label.strip():
                    raise ValueError("format 必须为 image/inkml，latex 必须为非空字符串")
                sample_path = (path.parent / filename).resolve()
                if not sample_path.is_file():
                    raise FileNotFoundError(sample_path)
                yield kind, str(sample_path), label, source_name(row.get("source"), kind)
            except (KeyError, TypeError, ValueError, OSError) as error:
                raise ValueError(f"无效数据清单 {path}:{line_number}: {error}") from error

def convert(images: list[torch.Tensor]) -> tuple[torch.Tensor, torch.Tensor]:
    """B 张 [1,H,W_i] -> ([B,3,H,W_max], 每张的真实宽度 [B])
    宽度不同的沿用各自背景色补到最宽;灰度复制成 3 通道,因为 vital_pixel 第一层是 3 通道"""
    widths = torch.tensor([im.size(-1) for im in images])
    batch = torch.zeros(len(images), 1, IMG_H, int(widths.max()))
    for i, im in enumerate(images):
        # 两条预处理路径都留有纯色边距，左上角就是该样本的补边颜色。
        batch[i].fill_(im[0, 0, 0].item())
        batch[i, :, :, :im.size(-1)] = im
    return batch.expand(-1, 3, -1, -1).contiguous(), widths

def collate(batch):
    images, ids = zip(*batch)
    image, widths = convert(list(images))
    B = len(batch)

    # memory 的 padding mask: 补出来的列 -> True。展平顺序必须和 model.vital_mini.encode 一致(先行后列)
    gh, gw = IMG_H // STRIDE, image.size(-1) // STRIDE
    valid_cols = (widths + STRIDE - 1) // STRIDE
    col_pad = torch.arange(gw)[None, :] >= valid_cols[:, None]                     # [B,gw]
    memory_mask = col_pad[:, None, :].expand(B, gh, gw).reshape(B, gh * gw)        # [B,gh*gw]

    # <bos> y1..yn <eos>,右侧补 <pad>;输入去掉最后一位,目标去掉第一位(错开一位)
    L = max(len(x) for x in ids) + 2
    seq = torch.full((B, L), pad_id, dtype=torch.long)
    for i, x in enumerate(ids):
        seq[i, :len(x) + 2] = torch.tensor([bos_id] + list(x) + [eos_id])
    tokens_in, target = seq[:, :-1], seq[:, 1:]
    return image, memory_mask, tokens_in, tokens_in == pad_id, target

class SourceMixedBatchSampler(Sampler):
    """各来源按原始占比贯穿整轮；每条样本恰好一次，来源内保留局部长度分桶。"""

    def __init__(self, lengths: list[int], sources: list[str], batch_size: int, seed: int = 0):
        if len(lengths) != len(sources) or batch_size < 1:
            raise ValueError("lengths 与 sources 必须等长，batch_size 必须 >= 1")
        self.lengths, self.batch_size, self.seed = lengths, batch_size, seed
        self.epoch = 0
        self.groups = {}
        for i, source in enumerate(sources):
            self.groups.setdefault(source, []).append(i)

    def set_epoch(self, epoch: int):
        self.epoch = epoch

    def __iter__(self):
        rng = random.Random(self.seed + self.epoch)
        queues, schedule = {}, []
        for source, original in sorted(self.groups.items()):
            indices = original.copy()
            rng.shuffle(indices)
            blocks = []
            pool = self.batch_size * 50
            for start in range(0, len(indices), pool):
                chunk = sorted(indices[start:start + pool], key=lambda i: self.lengths[i])
                blocks.extend(chunk[pos:pos + self.batch_size] for pos in range(0, len(chunk), self.batch_size))
            rng.shuffle(blocks)
            queues[source] = [i for block in blocks for i in block]
            # 将该来源的样本等距分布到 [0, 1)，随机相位避免固定从同一来源开始。
            offset = rng.random()
            heapq.heappush(schedule, (offset / len(indices), source, 0, offset))

        batch = []
        while schedule:
            _, source, position, offset = heapq.heappop(schedule)
            queue = queues[source]
            batch.append(queue[position])
            position += 1
            if position < len(queue):
                heapq.heappush(schedule, ((position + offset) / len(queue), source, position, offset))
            if len(batch) == self.batch_size:
                # 只在已混合的 batch 内打乱，不能再全局按长度排序而聚回单一来源。
                rng.shuffle(batch)
                yield batch
                batch = []
        if batch:
            rng.shuffle(batch)
            yield batch

    def __len__(self):
        return math.ceil(len(self.lengths) / self.batch_size)


class BucketSampler(Sampler):
    """把标签长度相近的样本凑成一个 batch,减少补边的浪费(标签长度和图像宽度大致正相关)"""

    def __init__(self, lengths: list[int], batch_size: int, shuffle: bool):
        self.lengths, self.batch_size, self.shuffle = lengths, batch_size, shuffle

    def __iter__(self):
        idx = list(range(len(self.lengths)))
        if self.shuffle:
            random.shuffle(idx)
        pool = self.batch_size * 50                            # 每 50 个 batch 的样本内部按长度排序
        batches = []
        for s in range(0, len(idx), pool):
            chunk = sorted(idx[s:s + pool], key=lambda i: self.lengths[i])
            batches += [chunk[b:b + self.batch_size] for b in range(0, len(chunk), self.batch_size)]
        if self.shuffle:
            random.shuffle(batches)
        return iter(batches)

    def __len__(self):
        return math.ceil(len(self.lengths) / self.batch_size)

# ---------- 训练 / 评估 ----------
class TrainingOutput:
    """同时输出到终端和日志，每次写入后刷新，保留中断前的记录。"""

    def __init__(self, terminal, log):
        self.terminal, self.log = terminal, log

    def write(self, text):
        self.log.write(text)
        self.log.flush()
        return self.terminal.write(text)

    def flush(self):
        self.log.flush()
        self.terminal.flush()


@contextmanager
def training_log(args):
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    path = out / "training.log"
    # 追加而非覆盖，同一个输出目录下的历次训练集中保存。
    with path.open("a", encoding="utf-8") as log:
        with redirect_stdout(TrainingOutput(sys.stdout, log)), redirect_stderr(TrainingOutput(sys.stderr, log)):
            started = time.monotonic()
            print(f"\n{'=' * 72}\n训练开始: {datetime.now().astimezone().isoformat(timespec='seconds')}")
            print(f"训练日志: {path.resolve()}")
            print("训练参数: " + json.dumps(vars(args), ensure_ascii=False, default=str))
            print(f"初始学习率: {Learning_rate} | 最低学习率: {minimum_learning_rate}")
            status = "完成"
            try:
                yield
            except BaseException as error:
                status = "中断" if isinstance(error, KeyboardInterrupt) else "失败"
                # 外层仍会抛出异常；这里单独将完整堆栈保存到文件。
                traceback.print_exc(file=log)
                raise
            finally:
                print(f"训练结束: {datetime.now().astimezone().isoformat(timespec='seconds')} | "
                      f"状态: {status} | 总耗时: {time.monotonic() - started:.1f}s")


def run_batch(model: nn.Module, batch, device: str, amp: bool) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    image, memory_mask, tokens_in, tgt_mask, target = [t.to(device, non_blocking=True) for t in batch]
    with torch.autocast(device_type=device, dtype=torch.bfloat16, enabled=amp):
        logits = model(image, tokens_in, tgt_key_padding_mask=tgt_mask, memory_key_padding_mask=memory_mask)
    logits = logits.float()
    # pad 位置不算损失;label smoothing 让概率更平缓,逐位候选的排名更可信
    loss = F.cross_entropy(logits.reshape(-1, vocab_size), target.reshape(-1),
                           ignore_index=pad_id, label_smoothing=0.1)
    return loss, logits, target

def validation_metrics(loss_sum: float, correct: int, tokens: int, samples: int) -> dict:
    if tokens == 0:
        raise ValueError("验证数据没有有效 token")
    return {"loss": loss_sum / tokens, "token_acc": correct / tokens,
            "correct_tokens": correct, "token_count": tokens, "sample_count": samples}


@torch.no_grad()
def evaluate(model: nn.Module, loader: DataLoader, device: str, amp: bool) -> dict:
    """teacher forcing 指标按非 PAD token 加权，包含 EOS，与训练损失定义一致。"""
    model.eval()
    total_loss = 0.0
    correct = count = samples = 0
    for batch in loader:
        loss, logits, target = run_batch(model, batch, device, amp)
        keep = target != pad_id
        total_loss += loss.item() * keep.sum().item()
        correct += ((logits.argmax(-1) == target) & keep).sum().item()
        count += keep.sum().item()
        samples += target.size(0)
    return validation_metrics(total_loss, correct, count, samples)


def source_groups(dataset: MathWriting) -> dict[str, list[int]]:
    groups = {}
    for i, source in enumerate(dataset.sources):
        groups.setdefault(source, []).append(i)
    order = [s for s in VALIDATION_SOURCES if s in groups]
    order += sorted(set(groups) - set(VALIDATION_SOURCES))
    return {s: groups[s] for s in order}


def evaluate_sources(model: nn.Module, loaders: dict[str, DataLoader], device: str, amp: bool) -> tuple[dict, dict]:
    by_source = {source: evaluate(model, loader, device, amp) for source, loader in loaders.items()}
    # 总体指标按 token 数聚合，不能简单平均不同来源或不同 batch 的百分比。
    overall = validation_metrics(
        sum(m["loss"] * m["token_count"] for m in by_source.values()),
        sum(m["correct_tokens"] for m in by_source.values()),
        sum(m["token_count"] for m in by_source.values()),
        sum(m["sample_count"] for m in by_source.values()))
    return overall, by_source


def fixed_exp_indices(dataset: MathWriting, n: int, seed: int) -> list[int]:
    """按来源均衡、不放回抽样；稳定哈希排序不受清单顺序或训练随机状态影响。"""
    groups = source_groups(dataset)
    if not groups or n < len(groups):
        raise ValueError(f"eval-n 必须至少为来源数 {len(groups)}，且验证集不能为空")
    ranked = {}
    for source, indices in groups.items():
        def rank(i):
            key = json.dumps([seed, source, dataset.items[i]], ensure_ascii=False)
            return hashlib.sha256(key.encode("utf-8")).hexdigest(), key
        ranked[source] = sorted(indices, key=rank)
    selected = []
    # 小来源用完后把剩余名额分配给其他来源；不足 n 条时使用全部可用样本。
    position = 0
    target = min(n, len(dataset))
    while len(selected) < target:
        for indices in ranked.values():
            if position < len(indices) and len(selected) < target:
                selected.append(indices[position])
        position += 1
    return selected


def save_exp_sample(dataset: MathWriting, indices: list[int], out: Path, seed: int, requested: int) -> dict:
    samples = [{"source": dataset.sources[i], "format": dataset.items[i][0],
                "path": dataset.items[i][1], "target_ids": dataset.items[i][2]} for i in indices]
    counts = {}
    for sample in samples:
        counts[sample["source"]] = counts.get(sample["source"], 0) + 1
    protocol = {"strategy": "source_balanced_sha256_v1", "seed": seed,
                "requested": requested, "sample_count": len(indices), "source_counts": counts,
                "beam_width": 1, "max_steps": max_len}
    payload = {**protocol, "samples": samples}
    content = json.dumps(payload, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    signature = hashlib.sha256(content.encode("utf-8")).hexdigest()
    path = out / f"exp_rate_samples_{signature}.json"
    out.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_text(encoding="utf-8") != content:
            raise ValueError(f"固定抽样清单内容不一致: {path}")
    else:
        path.write_text(content, encoding="utf-8")
    print(f"ExpRate 固定分层均衡抽样: {len(indices)}/{requested} 条 | {counts} | "
          f"seed={seed} | beam=1 | max_steps={max_len}")
    print(f"ExpRate 样本清单: {path.resolve()}")
    return {**protocol, "sample_signature": signature, "sample_file": str(path.resolve())}

@torch.no_grad()
def exp_rate(model: vital_mini, dataset: MathWriting, device: str, indices: list[int]) -> tuple[dict, dict]:
    """固定分层样本上的精确 token 序列匹配；总体比例只代表该均衡样本。"""
    if not indices:
        raise ValueError("ExpRate 抽样不能为空")
    model.eval()
    by_source = {}
    for i in indices:
        image, _ = dataset[i]
        pred = model.generate(convert([image])[0].to(device), beam_width=1, max_steps=max_len)[0]
        result = by_source.setdefault(dataset.sources[i], {"correct": 0, "sample_count": 0})
        result["correct"] += int(pred == dataset.items[i][2])
        result["sample_count"] += 1
    for result in by_source.values():
        result["rate"] = result["correct"] / result["sample_count"]
    correct = sum(result["correct"] for result in by_source.values())
    return {"correct": correct, "sample_count": len(indices), "rate": correct / len(indices)}, by_source

def resolve_data_root(value: str) -> Path:
    """相对路径优先使用工作目录，不存在时再查找脚本所在目录。"""
    path = Path(value).expanduser()
    if path.is_absolute():
        return path.resolve()
    working_path = (Path.cwd() / path).resolve()
    script_path = (Path(__file__).resolve().parent / path).resolve()
    if not working_path.exists() and script_path.is_dir():
        return script_path
    return working_path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=str(Path(__file__).resolve().parent / "data" / "mixed"),
                    help="默认读取 data/mixed 中的多来源混合数据；相对路径先查工作目录，再查脚本目录")
    ap.add_argument("--image-data", nargs="+", default=[], help="额外的个人图片数据目录（train.jsonl，可选 valid.jsonl）")
    ap.add_argument("--personal-train", type=Path, default=Path(__file__).resolve().parent / "train",
                    help="个人训练图片目录，读取其中 labels.jsonl；默认脚本旁的 train/")
    ap.add_argument("--no-personal-train", action="store_true", help="不自动加入个人 train/ 目录")
    ap.add_argument("--splits", default="train", help="训练用的子目录,逗号分隔,如 train,synthetic")
    ap.add_argument("--epochs", type=int, default=EPOCHS)
    ap.add_argument("--batch-size", type=int, default=32)
    ap.add_argument("--workers", type=int, default=0 if sys.platform == "win32" else 4,
                    help="数据加载子进程数；Windows 默认 0，避免复制大数据集导致系统内存耗尽")
    ap.add_argument("--pin-memory", action=argparse.BooleanOptionalAction,
                    default=sys.platform != "win32",
                    help="启用/关闭 CUDA 数据传输使用的固定主机内存；Windows 默认关闭")
    ap.add_argument("--oov", choices=["skip", "unk"], default="skip",
                    help="标签里有词表外的 token: skip=丢掉这条样本, unk=替换成 <unk>")
    ap.add_argument("--max-samples", type=int, default=0, help="限制训练/验证样本数用于调试，0=使用全部可用数据")
    ap.add_argument("--eval-n", type=int, default=1000, help="整式正确率的固定分层均衡抽样总数；不足时使用全部可用验证样本")
    ap.add_argument("--eval-seed", type=int, default=0, help="固定验证抽样种子，独立于训练 seed")
    ap.add_argument("--out", default=str(Path(__file__).resolve().parents[2] / "checkpoints"),
                    help="日志和验证抽样记录目录；默认权重为该目录下的 model.pt")
    ap.add_argument("--weights", type=Path, help="读取并原位更新的权重文件，默认 --out/model.pt")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()
    if args.max_samples < 0 or args.workers < 0 or args.epochs < 1 or args.batch_size < 1 or args.eval_n < 1:
        ap.error("max-samples、workers 必须 >= 0；epochs、batch-size、eval-n 必须 >= 1")
    args.data = str(resolve_data_root(args.data))
    args.weights = (args.weights or Path(args.out) / "model.pt").resolve()
    with training_log(args):
        train(args)


def train(args):
    print(f"数据目录: {args.data} | batch_size={args.batch_size} | workers={args.workers}", flush=True)

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    device = "cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu"
    amp = device == "cuda"                                    # CUDA 上用 bf16 混合精度
    pin_memory = args.pin_memory and device == "cuda"
    print(f"固定主机内存 pin_memory={pin_memory}", flush=True)
    if sys.platform == "win32" and args.workers > 0:
        print("提示：Windows 多进程会复制数据集信息；若出现 1455 或系统内存不足，"
              "请使用 --workers 0 --no-pin-memory。", flush=True)

    vocab = {t: i for i, t in enumerate(load_vocab())}
    train_ds = MathWriting(args.data, args.splits.split(","), vocab, train=True, oov=args.oov,
                           max_samples=args.max_samples, image_roots=args.image_data,
                           personal_train=None if args.no_personal_train else args.personal_train.resolve())
    val_ds = MathWriting(args.data, ["valid"], vocab, train=False, oov=args.oov,
                         max_samples=args.max_samples, image_roots=args.image_data)
    if not len(train_ds) or not len(val_ds):
        raise ValueError("过滤后的训练集或验证集为空，请检查清单、词表和数据路径")
    overlap = {item[1] for item in train_ds.items} & {item[1] for item in val_ds.items}
    if overlap:
        raise ValueError(f"训练和验证清单引用了相同文件，例如: {next(iter(overlap))}")
    validation_hash = hashlib.sha256()
    validation_hash.update(json.dumps({"image_invert": IMAGE_INVERT}).encode("utf-8"))
    validation_hash.update(json.dumps(load_vocab(), ensure_ascii=False).encode("utf-8"))
    for source, item in zip(val_ds.sources, val_ds.items):
        validation_hash.update(json.dumps([source, item], ensure_ascii=False).encode("utf-8") + b"\n")
    validation_signature = validation_hash.hexdigest()
    groups = source_groups(val_ds)
    for source in VALIDATION_SOURCES:
        if source not in groups:
            print(f"验证来源 {source}: 不可用（过滤后无样本；请确认 --data 指向混合数据目录）")
    exp_indices = fixed_exp_indices(val_ds, args.eval_n, args.eval_seed)
    out = Path(args.out)
    exp_protocol = save_exp_sample(val_ds, exp_indices, out, args.eval_seed, args.eval_n)
    # 不让训练、验证的两组 worker 常驻；显式启用多进程时也限制预取数量。
    loader_args = dict(collate_fn=collate, num_workers=args.workers, pin_memory=pin_memory)
    if args.workers > 0:
        loader_args["prefetch_factor"] = 1
    train_sampler = SourceMixedBatchSampler(
        [len(x[2]) for x in train_ds.items], train_ds.sources, args.batch_size, seed=args.seed)
    training_source_counts = {source: len(indices) for source, indices in train_sampler.groups.items()}
    print(f"训练来源混合: {training_source_counts} | "
          "按样本占比交错，每轮不重复、不遗漏")
    train_loader = DataLoader(train_ds, batch_sampler=train_sampler, **loader_args)
    val_loaders = {source: DataLoader(Subset(val_ds, indices), batch_sampler=BucketSampler(
        [len(val_ds.items[i][2]) for i in indices], args.batch_size, shuffle=False), **loader_args)
        for source, indices in groups.items()}

    model = vital_mini().to(device)
    best = float("inf")
    weights_path = Path(args.weights)
    checkpoint_metadata = {}
    if weights_path.exists():
        # 只恢复模型权重;优化器、学习率调度和训练轮数仍从本次运行开始。
        checkpoint = torch.load(weights_path, map_location="cpu", weights_only=True)
        state_dict = checkpoint["model"] if "model" in checkpoint else checkpoint
        if "model" in checkpoint:
            checkpoint_metadata = {key: value for key, value in checkpoint.items() if key != "model"}
        if load_training_weights(model, state_dict):
            old_size = state_dict["token_embedding.weight"].shape[0]
            print(f"已兼容旧的 {old_size} 词权重并扩展到 {vocab_size} 词；新增符号将在本次训练中学习")
        if "model" in checkpoint and checkpoint.get("validation_signature") == validation_signature:
            best = float(checkpoint.get("val_loss", best))
        else:
            print("验证集已变化或旧权重未记录验证集信息，本次重新比较最佳验证损失")
        del state_dict, checkpoint
        print(f"已加载模型权重: {weights_path.resolve()} (优化器和学习率调度重新初始化)")
    else:
        print(f"未找到模型权重: {weights_path.resolve()}, 从头训练")
    # 生成训练只更新本体，已经训练好的判断头一并加载、保存但不参与优化。
    for parameter in model.judge_parameters():
        parameter.requires_grad_(False)
    print(f"device: {device} | parameters: {sum(p.numel() for p in model.parameters())}")
    optimizer = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad),
                                  lr=Learning_rate, weight_decay=0.01)

    # 学习率: 线性预热,再余弦衰减到 minimum_learning_rate(按 step 调度,整个训练只衰减一次)
    total_steps = args.epochs * len(train_loader)
    warmup = min(500, max(total_steps // 10, 1))
    floor = minimum_learning_rate / Learning_rate
    def lr_scale(step: int) -> float:
        if step < warmup:
            return (step + 1) / warmup
        progress = (step - warmup) / max(total_steps - warmup, 1)
        return floor + (1 - floor) * 0.5 * (1 + math.cos(math.pi * progress))
    scheduler = torch.optim.lr_scheduler.LambdaLR(optimizer, lr_scale)

    # 开训前检查一次: memory 的格子数必须和 padding mask 对得上,否则 mask 会错位
    # 单个样本即可验证形状，避免额外启动一整组 worker 并预取多个 batch。
    first = collate([train_ds[0]])
    with torch.no_grad():
        assert model.eval().encode_image(first[0][:1].to(device)).size(1) == first[1].size(1), \
            "memory 的格子数与 padding mask 不一致,检查 IMG_H / STRIDE"
    del first

    out.mkdir(parents=True, exist_ok=True)
    weights_path.parent.mkdir(parents=True, exist_ok=True)
    for epoch in range(1, args.epochs + 1):
        train_sampler.set_epoch(epoch - 1)
        model.train()
        start, running = time.time(), 0.0
        train_loss_sum = train_token_count = 0
        for step, batch in enumerate(train_loader, 1):
            loss, _, _ = run_batch(model, batch, device, amp)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()
            batch_loss = loss.item()
            running += batch_loss
            token_count = (batch[-1] != pad_id).sum().item()
            train_loss_sum += batch_loss * token_count
            train_token_count += token_count
            if step % 50 == 0:
                print(f"  epoch {epoch} step {step}/{len(train_loader)} loss {running / 50:.4f} "
                      f"lr {scheduler.get_last_lr()[0]:.2e}")
                running = 0.0

        validation, validation_by_source = evaluate_sources(model, val_loaders, device, amp)
        expression, expression_by_source = exp_rate(model, val_ds, device, exp_indices)
        val_loss, val_acc, rate = validation["loss"], validation["token_acc"], expression["rate"]
        print(f"epoch {epoch}/{args.epochs} | train loss {train_loss_sum / train_token_count:.4f} | "
              f"val loss {val_loss:.4f} | token acc {val_acc:.3f} | "
              f"ExpRate(分层均衡 n={expression['sample_count']}) {rate:.3f} | {time.time() - start:.0f}s")
        for source in dict.fromkeys((*VALIDATION_SOURCES, *validation_by_source)):
            if source not in validation_by_source:
                print(f"  {source} | val loss N/A | token acc N/A | ExpRate N/A | samples=0")
                continue
            metrics, exact = validation_by_source[source], expression_by_source[source]
            print(f"  {source} | val loss {metrics['loss']:.4f} | token acc {metrics['token_acc']:.3f} | "
                  f"samples={metrics['sample_count']} | tokens={metrics['token_count']} | "
                  f"ExpRate(n={exact['sample_count']}) {exact['rate']:.3f} "
                  f"({exact['correct']}/{exact['sample_count']})")

        ckpt = {**checkpoint_metadata,
                "model": model.state_dict(), "epoch": epoch, "val_loss": val_loss, "exp_rate": rate,
                "validation_signature": validation_signature, "image_invert": IMAGE_INVERT,
                "validation": validation, "validation_by_source": validation_by_source,
                "exp_rate_count": expression["sample_count"], "exp_rate_by_source": expression_by_source,
                "exp_rate_protocol": exp_protocol}
        torch.save(ckpt, weights_path.parent / "last.pt")
        if val_loss < best:
            best = val_loss
            torch.save(ckpt, weights_path)
            print(f"已原位更新权重: {weights_path}")

if __name__ == "__main__":
    main()
