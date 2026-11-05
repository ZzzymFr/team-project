"""用实际生成接口评估整式精确匹配；保留固定抽样、权重快照和逐条预测。"""
import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
import math
from pathlib import Path
import shutil
import time

import torch

import train
from model import complete_judge_weights, ids_to_latex, load_vocab, max_len, vital_mini


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write_json(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def wilson(correct, total):
    p, z = correct / total, 1.959963984540054
    denominator = 1 + z * z / total
    center = (p + z * z / (2 * total)) / denominator
    radius = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / denominator
    return [center - radius, center + radius]


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--weights', type=Path, default=root / 'checkpoints/model.pt')
    parser.add_argument('--data', type=Path, default=Path(__file__).parent / 'data/mixed')
    parser.add_argument('--split', choices=['valid', 'test'], default='test')
    parser.add_argument('--samples', type=int, default=2000, help='分来源均衡抽样；0 表示全部合格样本')
    parser.add_argument('--seed', type=int, default=20261104)
    parser.add_argument('--beam-width', type=int, default=3)
    parser.add_argument('--out', type=Path, required=True, help='新的输出目录')
    args = parser.parse_args()
    if args.samples < 0 or args.beam_width < 1:
        parser.error('samples 必须 >= 0，beam-width 必须 >= 1')
    args.out.mkdir(parents=True, exist_ok=False)
    snapshot = args.out / 'model.pt'
    shutil.copy2(args.weights, snapshot)
    shutil.copy2(Path(__file__).with_name('tokeniser.json'), args.out / 'tokeniser.json')
    checkpoint = torch.load(snapshot, map_location='cpu', weights_only=True)
    table = load_vocab()
    state = checkpoint.get('model', checkpoint)
    if state['token_embedding.weight'].shape[0] != len(table):
        raise ValueError('权重词表与当前 tokeniser.json 不匹配')
    train.IMAGE_INVERT = checkpoint.get('image_invert')
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    torch.set_num_threads(4)
    torch.manual_seed(args.seed)
    model = vital_mini(num_tokens=len(table))
    model.load_state_dict(complete_judge_weights(model, state), strict=True)
    model.to(device).eval()
    dataset = train.MathWriting(str(args.data), [args.split], {t: i for i, t in enumerate(table)},
                               train=False, oov='skip', augment=False)
    groups = train.source_groups(dataset)
    indices = (list(range(len(dataset))) if args.samples == 0 else
               train.fixed_exp_indices(dataset, args.samples, args.seed))
    if not indices:
        raise ValueError('没有可评估样本')
    raw_counts = Counter()
    with (args.data / f'{args.split}.jsonl').open(encoding='utf-8') as stream:
        for line in stream:
            row = json.loads(line)
            raw_counts[train.source_name(row.get('source'), row.get('format', 'image'))] += 1
    selected = [{'source': dataset.sources[i], 'format': dataset.items[i][0],
                 'path': dataset.items[i][1], 'target_ids': dataset.items[i][2]} for i in indices]
    write_json(args.out / 'samples.json', selected)
    protocol = {'created_at': datetime.now().astimezone().isoformat(),
                'original_weights': str(args.weights.resolve()), 'weights_sha256': digest(snapshot),
                'checkpoint_epoch': checkpoint.get('epoch'), 'vocabulary_size': len(table),
                'data': str(args.data.resolve()), 'split': args.split, 'seed': args.seed,
                'selection': 'all_eligible' if args.samples == 0 else 'source_balanced_sha256_v1',
                'requested_samples': args.samples, 'sample_count': len(indices),
                'beam_width': args.beam_width, 'max_steps': max_len, 'length_alpha': 0.7,
                'device': device, 'precision': 'float32', 'image_invert': train.IMAGE_INVERT,
                'metric': 'exact normalized token sequence match; no algebraic equivalence',
                'raw_counts': dict(raw_counts), 'eligible_counts': {k: len(v) for k, v in groups.items()},
                'samples_sha256': digest(args.out / 'samples.json'),
                'manifest_sha256': digest(args.data / f'{args.split}.jsonl'),
                'code_sha256': {name: digest(Path(__file__).with_name(name)) for name in
                                ('model.py', 'train.py', 'preprocess.py', 'labels.py', 'evaluate_generation.py')}}
    write_json(args.out / 'protocol.json', protocol)
    print(json.dumps(protocol, ensure_ascii=False, indent=2), flush=True)
    counts, correct, errors = Counter(), Counter(), Counter()
    started = time.perf_counter()
    with (args.out / 'predictions.jsonl').open('x', encoding='utf-8') as output, torch.inference_mode():
        for position, (index, sample) in enumerate(zip(indices, selected), 1):
            error = None
            before = time.perf_counter()
            try:
                image, reference = dataset[index]
            except (OSError, ValueError, IndexError) as exc:
                error, prediction = str(exc), []
            else:
                prediction = model.generate(train.convert([image])[0].to(device),
                                            beam_width=args.beam_width, max_steps=max_len, length_alpha=0.7)[0]
            source = sample['source']
            matched = error is None and prediction == sample['target_ids']
            counts[source] += 1
            correct[source] += int(matched)
            errors[source] += int(error is not None)
            result = {**sample, 'prediction_ids': prediction, 'correct': matched, 'error': error,
                      'reference': ids_to_latex(sample['target_ids'], table),
                      'prediction': ids_to_latex(prediction, table), 'seconds': time.perf_counter() - before}
            output.write(json.dumps(result, ensure_ascii=False) + '\n')
            if position % 50 == 0 or position == len(indices):
                output.flush()
                print(f"完成 {position}/{len(indices)}，正确 {sum(correct.values())}/{position}，"
                      f"耗时 {time.perf_counter() - started:.1f}s", flush=True)
    by_source = {s: {'correct': correct[s], 'samples': counts[s], 'rate': correct[s] / counts[s],
                     'wilson_95': wilson(correct[s], counts[s]), 'input_errors': errors[s],
                     'eligible': len(groups[s]), 'raw': raw_counts[s]} for s in counts}
    weighted = sum(v['rate'] * v['eligible'] for v in by_source.values()) / len(dataset)
    summary = {'protocol': protocol, 'by_source': by_source,
               'correct': sum(correct.values()), 'sample_count': len(indices),
               'balanced_sample_rate': sum(correct.values()) / len(indices),
               'eligible_population_weighted_estimate': weighted,
               'seconds': time.perf_counter() - started}
    write_json(args.out / 'summary.json', summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)


if __name__ == '__main__':
    main()
