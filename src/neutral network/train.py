import argparse
import html
import json
import math
import random
import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image, ImageDraw
from torch.utils.data import DataLoader, Dataset, Sampler

from model import vital_mini, load_vocab, vocab_size, pad_id, bos_id, eos_id, max_len

Learning_rate = 8e-5
minimum_learning_rate = 3e-6
EPOCHS = 5

IMG_H = 128                  # 图像统一高度;vital_pixel 总步长 16,所以是 8 行格子
STRIDE = 16                  # vital_pixel 的总下采样倍数,图像宽度要补成它的倍数
MARGIN, MAX_W, MIN_W = 8, 1024, 64
INKML = "{http://www.w3.org/2003/InkML}"

# ---------- 标签 -> token ----------
TOKEN_RE = re.compile(r"\\[a-zA-Z]+|\\.|\S", re.S)             # \frac 这类命令是一个 token,其余按单字符
# MathWriting 的 normalizedLabel 把函数名写成普通字母(sin、log...),词表里是 \sin 这样的命令
FUNC_RE = re.compile(r"(?<![a-zA-Z\\])(sin|cos|tan|log|ln|lim)")
ALIASES = {r"\le": r"\leq", r"\ge": r"\geq", r"\ne": r"\neq", r"\to": r"\rightarrow"}   # 同义写法统一成词表里的那个
LABEL_RE = re.compile(r'<annotation type="normalizedLabel">(.*?)</annotation>', re.S)

def tokenize_label(label: str) -> list[str]:
    label = FUNC_RE.sub(lambda m: "\\" + m.group(1) + " ", label)   # 补空格,避免 sinx 变成 \sinx 一个命令
    return [ALIASES.get(t, t) for t in TOKEN_RE.findall(label)]

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

# ---------- 数据集 ----------
class MathWriting(Dataset):
    """MathWriting 的 InkML 目录(含 train/ valid/ test/ synthetic/)。标签用 normalizedLabel"""

    def __init__(self, root: str, splits: list[str], vocab: dict[str, int], train: bool,
                 oov: str = "skip", max_samples: int | None = None):
        self.root, self.train = Path(root), train
        unk = vocab["<unk>"]
        self.items: list[tuple[str, str, list[int]]] = []      # (split, 文件名, token id)
        total = too_long = has_oov = 0
        for split in splits:
            for name, label in build_index(self.root, split):
                total += 1
                toks = tokenize_label(label)
                if len(toks) + 2 > max_len:                    # 加上 <bos>/<eos>
                    too_long += 1
                    continue
                if oov == "skip" and any(t not in vocab for t in toks):
                    has_oov += 1
                    continue
                self.items.append((split, name, [vocab.get(t, unk) for t in toks]))
        if max_samples:
            random.Random(0).shuffle(self.items)
            self.items = self.items[:max_samples]
        print(f"{'train' if train else 'eval '} {splits}: {len(self.items)}/{total} samples used "
              f"(too long: {too_long}, tokens outside tokeniser.json: {has_oov}, oov={oov})")

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, i: int) -> tuple[torch.Tensor, list[int]]:
        split, name, ids = self.items[i]
        strokes = read_strokes(self.root / split / name)
        stroke_px = 3.0
        if self.train:                                         # 训练时随机扰动笔迹形状和粗细,验证时不扰动
            rng = random.Random()
            strokes = augment_strokes(strokes, rng)
            stroke_px = rng.uniform(2.0, 4.0)
        return render(strokes, stroke_px), ids

def convert(images: list[torch.Tensor]) -> tuple[torch.Tensor, torch.Tensor]:
    """B 张 [1,H,W_i] -> ([B,3,H,W_max], 每张的真实宽度 [B])
    宽度不同的用 0(背景)补到最宽;灰度复制成 3 通道,因为 vital_pixel 第一层是 3 通道"""
    widths = torch.tensor([im.size(-1) for im in images])
    batch = torch.zeros(len(images), 1, IMG_H, int(widths.max()))
    for i, im in enumerate(images):
        batch[i, :, :, :im.size(-1)] = im
    return batch.expand(-1, 3, -1, -1).contiguous(), widths

def collate(batch):
    images, ids = zip(*batch)
    image, widths = convert(list(images))
    B = len(batch)

    # memory 的 padding mask: 补出来的列 -> True。展平顺序必须和 vital_mini.encode 一致(先行后列)
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
def run_batch(model: nn.Module, batch, device: str, amp: bool) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    image, memory_mask, tokens_in, tgt_mask, target = [t.to(device, non_blocking=True) for t in batch]
    with torch.autocast(device_type=device, dtype=torch.bfloat16, enabled=amp):
        logits = model(image, tokens_in, tgt_key_padding_mask=tgt_mask, memory_key_padding_mask=memory_mask)
    logits = logits.float()
    # pad 位置不算损失;label smoothing 让概率更平缓,逐位候选的排名更可信
    loss = F.cross_entropy(logits.reshape(-1, vocab_size), target.reshape(-1),
                           ignore_index=pad_id, label_smoothing=0.1)
    return loss, logits, target

@torch.no_grad()
def evaluate(model: nn.Module, loader: DataLoader, device: str, amp: bool) -> tuple[float, float]:
    """验证集的 teacher forcing 损失和逐 token 准确率"""
    model.eval()
    total_loss = correct = count = 0.0
    for batch in loader:
        loss, logits, target = run_batch(model, batch, device, amp)
        keep = target != pad_id
        total_loss += loss.item() * keep.sum().item()
        correct += ((logits.argmax(-1) == target) & keep).sum().item()
        count += keep.sum().item()
    return total_loss / count, correct / count

@torch.no_grad()
def exp_rate(model: vital_mini, dataset: MathWriting, device: str, n: int) -> float:
    """整式完全正确的比例:对前 n 个验证样本用 generate(beam=1)识别,与标准答案逐 token 比较"""
    ok = 0
    n = min(n, len(dataset))
    for i in range(n):
        image, _ = dataset[i]
        pred = model.generate(convert([image])[0].to(device), beam_width=1, max_steps=min(max_len, 120))[0]
        ok += pred == dataset.items[i][2]
    return ok / n

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="MathWriting 目录(里面有 train/ valid/ ...)")
    ap.add_argument("--splits", default="train", help="训练用的子目录,逗号分隔,如 train,synthetic")
    ap.add_argument("--epochs", type=int, default=EPOCHS)
    ap.add_argument("--batch-size", type=int, default=32)
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--oov", choices=["skip", "unk"], default="skip",
                    help="标签里有词表外的 token: skip=丢掉这条样本, unk=替换成 <unk>")
    ap.add_argument("--max-samples", type=int, default=1000, help="只用前 N 条训练样本(调试用)")
    ap.add_argument("--eval-n", type=int, default=100, help="计算整式正确率用的验证样本数")
    ap.add_argument("--out", default="checkpoints")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    device = "cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu"
    amp = device == "cuda"                                    # CUDA 上用 bf16 混合精度

    vocab = {t: i for i, t in enumerate(load_vocab())}
    train_ds = MathWriting(args.data, args.splits.split(","), vocab, train=True, oov=args.oov,
                           max_samples=args.max_samples)
    val_ds = MathWriting(args.data, ["valid"], vocab, train=False, oov=args.oov,
                         max_samples=args.max_samples)
    loader_args = dict(collate_fn=collate, num_workers=args.workers, pin_memory=device == "cuda",
                       persistent_workers=args.workers > 0)
    train_loader = DataLoader(train_ds, batch_sampler=BucketSampler(
        [len(x[2]) for x in train_ds.items], args.batch_size, shuffle=True), **loader_args)
    val_loader = DataLoader(val_ds, batch_sampler=BucketSampler(
        [len(x[2]) for x in val_ds.items], args.batch_size, shuffle=False), **loader_args)

    out = Path(args.out)
    model = vital_mini().to(device)
    best = float("inf")
    weights_path = out / "model.pt"
    if weights_path.exists():
        # 只恢复模型权重;优化器、学习率调度和训练轮数仍从本次运行开始。
        checkpoint = torch.load(weights_path, map_location="cpu", weights_only=True)
        state_dict = checkpoint["model"] if "model" in checkpoint else checkpoint
        model.load_state_dict(state_dict, strict=True)
        if "model" in checkpoint:
            best = float(checkpoint.get("val_loss", best))
        del state_dict, checkpoint
        print(f"已加载模型权重: {weights_path.resolve()} (优化器和学习率调度重新初始化)")
    else:
        print(f"未找到模型权重: {weights_path.resolve()}, 从头训练")
    print(f"device: {device} | parameters: {sum(p.numel() for p in model.parameters())}")
    optimizer = torch.optim.AdamW(model.parameters(), lr=Learning_rate, weight_decay=0.01)

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
    first = next(iter(train_loader))
    with torch.no_grad():
        assert model.eval().encode_image(first[0][:1].to(device)).size(1) == first[1].size(1), \
            "memory 的格子数与 padding mask 不一致,检查 IMG_H / STRIDE"

    out.mkdir(parents=True, exist_ok=True)
    for epoch in range(1, args.epochs + 1):
        model.train()
        start, running = time.time(), 0.0
        for step, batch in enumerate(train_loader, 1):
            loss, _, _ = run_batch(model, batch, device, amp)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()
            running += loss.item()
            if step % 50 == 0:
                print(f"  epoch {epoch} step {step}/{len(train_loader)} loss {running / 50:.4f} "
                      f"lr {scheduler.get_last_lr()[0]:.2e}")
                running = 0.0

        val_loss, val_acc = evaluate(model, val_loader, device, amp)
        rate = exp_rate(model, val_ds, device, args.eval_n)
        print(f"epoch {epoch}/{args.epochs} | val loss {val_loss:.4f} | token acc {val_acc:.3f} | "
              f"ExpRate({args.eval_n}) {rate:.3f} | {time.time() - start:.0f}s")

        ckpt = {"model": model.state_dict(), "epoch": epoch, "val_loss": val_loss, "exp_rate": rate}
        torch.save(ckpt, out / "last.pt")
        if val_loss < best:
            best = val_loss
            torch.save(ckpt, out / "model.pt")

if __name__ == "__main__":
    main()
