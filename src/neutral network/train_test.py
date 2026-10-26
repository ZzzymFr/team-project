"""冻结生成本体，训练直接读取前缀 token 与图片特征的因果检测器。

1 = 已读入的整个前缀与标准答案一致；0 = 已经出现错误。
标签对齐当前输入位置，不向后错位；BOS/PAD 不计损失。
对 softmax 的正确类概率计算 BCELoss，标签为浮点数 0/1。
默认读取并原位更新 checkpoints/model.pt，每轮另存同目录的 last.pt。
独立检测解码器通过因果自注意力读取前缀、交叉注意力读取图片。
"""

import argparse
import random
from pathlib import Path

import torch
import torch.nn.functional as F
from torch.nn.utils.rnn import pad_sequence
from torch.utils.data import DataLoader

from model import (bos_id, eos_id, pad_id, max_len, legacy_vocab_sizes, load_vocab,
                   vital_mini, complete_judge_weights)
from train import (MathWriting, SourceMixedBatchSampler, BucketSampler, collate,
                   resolve_data_root, IMAGE_INVERT)

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[1]
IGNORE = -100
TASK = "exact_prefix_correctness_v1"


def prefix_labels(candidates: torch.Tensor, references: torch.Tensor) -> torch.Tensor:
    """[N,T] -> [N,T]；首次不一致后一直为 0，包括错误的提前/延后结束。"""
    if candidates.shape != references.shape:
        raise ValueError("候选和答案必须补齐到相同形状")
    labels = candidates.eq(references).long().cumprod(dim=1)
    # 结束标记本身参与判断；其后的所有位置均忽略。
    ended_before = candidates.eq(eos_id).long().cumsum(1) - candidates.eq(eos_id).long()
    ignore = candidates.eq(pad_id) | candidates.eq(bos_id) | ended_before.gt(0)
    return labels.masked_fill(ignore, IGNORE)


def make_candidates(gold: torch.Tensor, next_logits: torch.Tensor,
                    rng: random.Random) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    """每张图保留标准答案，并构造一条模型高分替换和一条结构错误候选。

    next_logits[:, t-1] 只用于提出位置 t 的替代 token；检测器读取候选 token
    和图片特征，不能用提出候选之前的旧分数来判断新前缀。
    """
    rows, references, owners = [], [], []
    for i, row in enumerate(gold.tolist()):
        reference = row[:row.index(eos_id) + 1]
        position = rng.randrange(1, len(reference))  # 也可替换 EOS，学习漏结束错误
        scores = next_logits[i, position - 1].detach().float().cpu().clone()
        scores[[pad_id, bos_id, reference[position]]] = -torch.inf
        alternatives = scores.topk(min(3, scores.numel() - 3)).indices.tolist()
        alternative = rng.choice(alternatives)
        replaced = reference.copy()
        replaced[position] = alternative
        if alternative == eos_id:
            replaced = replaced[:position + 1]

        mode = rng.choice(("delete", "insert", "early_eos"))
        structure = reference.copy()
        if len(reference) == 2:
            mode = "insert"
        if mode == "delete":
            del structure[rng.randrange(1, len(reference) - 1)]
        elif mode == "early_eos":
            position = rng.randrange(1, len(reference) - 1)
            structure = reference[:position] + [eos_id]
        else:
            position = rng.randrange(1, len(reference))
            inserted = rng.choice([t for t in alternatives if t != eos_id])
            structure.insert(position, inserted)
            structure = structure[:max_len]  # 达到长度上限不伪造 EOS

        for candidate in (reference, replaced, structure):
            rows.append(torch.tensor(candidate, dtype=torch.long))
            references.append(torch.tensor(reference, dtype=torch.long))
            owners.append(i)
    padded = pad_sequence(rows + references, batch_first=True, padding_value=pad_id)
    candidates, expected = padded[:len(rows)], padded[len(rows):]
    return candidates, prefix_labels(candidates, expected), torch.tensor(owners)


def load_generator(path: Path, device: str) -> tuple[vital_mini, dict]:
    if not path.is_file():
        raise FileNotFoundError(f"需要已经训练好的生成模型，权重不存在: {path}")
    checkpoint = torch.load(path, map_location="cpu", weights_only=True)
    state = dict(checkpoint.get("model", checkpoint))
    size = state["token_embedding.weight"].shape[0]
    if size not in (*legacy_vocab_sizes, len(load_vocab())):
        raise ValueError(f"不支持的词表大小: {size}")
    if checkpoint.get("image_invert", IMAGE_INVERT) != IMAGE_INVERT:
        raise ValueError("权重的图片反色设置与当前数据预处理不一致")
    model = vital_mini(num_tokens=size)
    if not any(key.startswith("judge_decoder.") for key in state):
        print("正在迁移旧检测结构：完整保留生成本体，重新初始化图片/前缀检测器。")
    model.load_state_dict(complete_judge_weights(model, state), strict=True)
    model.to(device)
    model.requires_grad_(False)
    for parameter in model.judge_parameters():
        parameter.requires_grad_(True)
    model.eval()  # 冻结 BatchNorm 统计量，并关闭本体 dropout。
    return model, checkpoint


def run_batch(model, batch, device, rng):
    images, memory_mask, _, _, target = batch
    # target 为 y1..yn EOS，补回 BOS，让候选被完整读入后再判断。
    gold = torch.cat((torch.full((target.size(0), 1), bos_id, dtype=torch.long), target), dim=1)
    images = images.to(device)
    memory_mask = memory_mask.to(device)
    with torch.no_grad():
        gold_device = gold.to(device)
        next_logits = model(images, gold_device, gold_device.eq(pad_id), memory_mask)
        candidates, labels, owners = make_candidates(gold, next_logits, rng)
        candidates, owners = candidates.to(device), owners.to(device)
    # 检测接口内部编码图片；本体参数已冻结，但检测分支必须正常记录梯度。
    logits = model.judge_prefix(images[owners], candidates,
                                memory_key_padding_mask=memory_mask[owners], return_logits=True)
    labels = labels.to(device)
    valid = labels.ne(IGNORE)
    truth = labels[valid]
    # 保留两类输出及已有权重；BCELoss 接收正确类概率，而非原始 logits。
    correct_probability = F.softmax(logits[valid].float(), dim=-1)[:, 1]
    losses = F.binary_cross_entropy(correct_probability, truth.float(), reduction="none")
    # 正误两类分别平均，避免大量重复的正确前缀淹没错误样本。
    class_losses = [losses[truth.eq(c)].mean() for c in (0, 1) if truth.eq(c).any()]
    loss = torch.stack(class_losses).mean()
    prediction = logits[valid].argmax(-1)
    stats = {}
    for c in (0, 1):
        selected = truth.eq(c)
        stats[f"count_{c}"] = int(selected.sum())
        stats[f"correct_{c}"] = int((prediction[selected] == c).sum())
        stats[f"loss_{c}"] = float(losses[selected].detach().sum())
    return loss, stats


def run_epoch(model, loader, device, seed, optimizer=None):
    model.eval()
    for module in model.judge_modules():
        module.train(optimizer is not None)
    rng = random.Random(seed)
    totals = {f"{key}_{c}": 0 for key in ("count", "correct", "loss") for c in (0, 1)}
    for step, batch in enumerate(loader, 1):
        with torch.set_grad_enabled(optimizer is not None):
            loss, stats = run_batch(model, batch, device, rng)
            if optimizer is not None:
                optimizer.zero_grad(set_to_none=True)
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.judge_parameters(), 1.0)
                optimizer.step()
        for key, value in stats.items():
            totals[key] += value
        if step % 50 == 0:
            print(f"  {'训练' if optimizer else '验证'} {step}/{len(loader)} | 损失 {loss.item():.4f}", flush=True)
    if not all(totals[f"count_{c}"] for c in (0, 1)):
        raise ValueError("训练或验证必须同时包含正确、错误前缀")
    recalls = [totals[f"correct_{c}"] / totals[f"count_{c}"] for c in (0, 1)]
    return dict(totals,
                loss=sum(totals[f"loss_{c}"] / totals[f"count_{c}"] for c in (0, 1)) / 2,
                balanced_accuracy=sum(recalls) / 2,
                error_recall=recalls[0], correct_recall=recalls[1])


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights", type=Path, default=PROJECT / "checkpoints" / "model.pt",
                        help="读取并原位更新的同一个权重文件")
    parser.add_argument("--data", default=str(ROOT / "data" / "mixed"))
    parser.add_argument("--personal-train", type=Path, default=ROOT / "train")
    parser.add_argument("--no-personal-train", action="store_true")
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--batch-size", type=int, default=8, help="每张图扩展为三条候选")
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--workers", type=int, default=0)
    parser.add_argument("--max-samples", type=int, default=0, help="限制每个数据划分的图片数，0 表示不限")
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--device", choices=("cpu", "cuda", "mps"))
    args = parser.parse_args()
    if min(args.epochs, args.batch_size) < 1 or args.lr <= 0 or min(args.workers, args.max_samples) < 0:
        parser.error("epochs、batch-size、lr 必须为正；workers、max-samples 不得为负")
    return args


def main():
    args = parse_args()
    torch.manual_seed(args.seed)
    random.seed(args.seed)
    device = args.device or ("cuda" if torch.cuda.is_available() else
                            "mps" if torch.backends.mps.is_available() else "cpu")
    args.weights = args.weights.resolve()
    model, loaded_checkpoint = load_generator(args.weights, device)
    # 保留生成训练的 epoch、val_loss、验证集签名等；判断训练记录放入独立字段。
    checkpoint_metadata = ({key: value for key, value in loaded_checkpoint.items() if key != "model"}
                           if "model" in loaded_checkpoint else {})
    del loaded_checkpoint
    vocab = {token: i for i, token in enumerate(load_vocab()[:model.head.out_features])}
    root = str(resolve_data_root(args.data))
    train_ds = MathWriting(root, ["train"], vocab, train=True, max_samples=args.max_samples,
                           personal_train=None if args.no_personal_train else args.personal_train.resolve())
    val_ds = MathWriting(root, ["valid"], vocab, train=False, max_samples=args.max_samples)
    if not len(train_ds) or not len(val_ds):
        raise ValueError("训练集或验证集为空")
    overlap = {item[1] for item in train_ds.items} & {item[1] for item in val_ds.items}
    if overlap:
        raise ValueError(f"训练集与验证集引用同一图片: {next(iter(overlap))}")
    sampler = SourceMixedBatchSampler([len(x[2]) for x in train_ds.items], train_ds.sources,
                                      args.batch_size, seed=args.seed)
    loader_options = dict(collate_fn=collate, num_workers=args.workers)
    if args.workers:
        loader_options["prefetch_factor"] = 1
    train_loader = DataLoader(train_ds, batch_sampler=sampler, **loader_options)
    val_loader = DataLoader(val_ds, batch_sampler=BucketSampler(
        [len(x[2]) for x in val_ds.items], args.batch_size, shuffle=False), **loader_options)
    optimizer = torch.optim.AdamW(model.judge_parameters(), lr=args.lr, weight_decay=0.01)
    best = float("inf")
    print(f"设备: {device} | 已冻结本体 | 可训练参数: {sum(p.numel() for p in model.judge_parameters())}")
    print("判断对象为已读入前缀；标签按标准 token 序列匹配。验证候选使用固定随机种子。", flush=True)
    for epoch in range(1, args.epochs + 1):
        sampler.set_epoch(epoch - 1)
        training = run_epoch(model, train_loader, device, args.seed + epoch, optimizer)
        validation = run_epoch(model, val_loader, device, args.seed)
        print(f"轮次 {epoch}/{args.epochs} | 训练损失 {training['loss']:.4f} | "
              f"验证损失 {validation['loss']:.4f} | 均衡准确率 {validation['balanced_accuracy']:.3f} | "
              f"错误检出率 {validation['error_recall']:.3f} | 正确前缀保留率 {validation['correct_recall']:.3f}",
              flush=True)
        checkpoint = {**checkpoint_metadata, "model": model.state_dict(), "image_invert": IMAGE_INVERT,
                      "judge": {"optimizer": optimizer.state_dict(), "epoch": epoch, "task": TASK,
                                "judge_input": "prefix_tokens_and_images",
                                "architecture": "causal_prefix_image_v1",
                                "class_names": ["错误", "正确"],
                                "training": training, "validation": validation,
                                "config": {key: str(value) if isinstance(value, Path) else value
                                           for key, value in vars(args).items()}}}
        torch.save(checkpoint, args.weights.parent / "last.pt")
        if validation["loss"] < best:
            best = validation["loss"]
            torch.save(checkpoint, args.weights)
            print(f"已原位更新权重: {args.weights}")
    print(f"训练完成，权重位置: {args.weights}")


if __name__ == "__main__":
    main()
