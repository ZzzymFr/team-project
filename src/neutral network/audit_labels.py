"""只读检查训练清单的标签结构、词表覆盖和长度；不判断图片转录或数学真假。"""
import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

from labels import tokenize_label

ROOT = Path(__file__).resolve().parents[2]
ENVIRONMENT = re.compile(r'\\(begin|end)\{([^{}]+)\}')


def inspect_label(label, vocab, max_length=300):
    if not isinstance(label, str) or not label.strip():
        return [], {'invalid_or_empty_label': True}
    flags = {}
    try:
        tokens = tokenize_label(label)
    except ValueError as error:
        return [], {'tokenization_error': str(error)}
    depth = 0
    for token in tokens:
        if token == '{':
            depth += 1
        elif token == '}':
            depth -= 1
            if depth < 0:
                flags['unbalanced_braces'] = True
    if depth:
        flags['unbalanced_braces'] = True
    stack = []
    for match in ENVIRONMENT.finditer(''.join(tokens)):
        action, name = match.groups()
        if action == 'begin':
            stack.append(name)
        elif not stack or stack.pop() != name:
            flags['unbalanced_environments'] = True
    if stack:
        flags['unbalanced_environments'] = True
    unknown = sorted(set(tokens) - vocab)
    if unknown:
        flags['oov'] = unknown
    if len(tokens) + 2 > max_length:
        flags['too_long'] = len(tokens) + 2
    if '%' in tokens:
        flags['bare_percent_review'] = True
    if tokens.count('$') % 2:
        flags['unpaired_dollar_review'] = True
    if any(ord(char) < 32 and char not in '\n\r\t' for char in label):
        flags['control_character'] = True
    if any(token in label for token in ('<pad>', '<bos>', '<eos>', '<unk>', '<boms>')):
        flags['literal_special_token_review'] = True
    return tokens, flags


def audit(manifests, output):
    document = json.loads(Path(__file__).with_name('tokeniser.json').read_text(encoding='utf-8'))
    vocab = document['base']['vocab']
    new_tokens = {t for t, i in vocab.items() if i >= 200}
    output.mkdir(parents=True, exist_ok=True)
    vocab_set = set(vocab)
    counts, reasons, unknown, new_coverage, eligible_new_coverage = Counter(), Counter(), Counter(), Counter(), Counter()
    by_source = defaultdict(Counter)
    examples = defaultdict(list)
    inputs = []
    with (output / 'findings.jsonl').open('w', encoding='utf-8') as findings:
        for manifest in manifests:
            before = manifest.stat()
            hasher = hashlib.sha256()
            with manifest.open('rb') as stream:
                for line_number, raw in enumerate(stream, 1):
                    hasher.update(raw)
                    if not raw.strip():
                        continue
                    counts['rows'] += 1
                    try:
                        row = json.loads(raw)
                        if not isinstance(row, dict):
                            raise ValueError('record is not an object')
                        label = row.get('latex')
                        tokens, flags = inspect_label(label, vocab_set)
                        source = row.get('source', 'personal')
                    except (ValueError, UnicodeError) as error:
                        row, label, tokens, source = {}, None, [], 'invalid_record'
                        flags = {'invalid_json_record': str(error)}
                    by_source[source]['rows'] += 1
                    for token in set(tokens) & new_tokens:
                        new_coverage[token] += 1
                    unknown.update(flags.get('oov', []))
                    for reason in flags:
                        reasons[reason] += 1
                        by_source[source][reason] += 1
                    structural = any(key in flags for key in ('invalid_or_empty_label', 'tokenization_error',
                        'unbalanced_braces', 'unbalanced_environments', 'control_character', 'invalid_json_record'))
                    if structural:
                        counts['structural_error_rows'] += 1
                    encodable = not any(key in flags for key in ('invalid_or_empty_label', 'tokenization_error', 'oov', 'too_long', 'invalid_json_record'))
                    if encodable:
                        counts['vocab_length_eligible_rows'] += 1
                        for token in set(tokens) & new_tokens:
                            eligible_new_coverage[token] += 1
                        if set(tokens) & new_tokens:
                            counts['newly_eligible_vs_200_rows'] += 1
                        if structural:
                            counts['eligible_structural_error_rows'] += 1
                    if flags:
                        counts['flagged_rows'] += 1
                        item = {'manifest': str(manifest.relative_to(ROOT)), 'line': line_number,
                                'source': source, 'image': row.get('path', row.get('image')), 'latex': label,
                                'eligible_before_image_checks': encodable, 'flags': flags}
                        findings.write(json.dumps(item, ensure_ascii=False) + '\n')
                        for reason in flags:
                            if len(examples[reason]) < 8:
                                examples[reason].append(item)
                    if counts['rows'] % 100000 == 0:
                        print(f"已检查 {counts['rows']} 条标签", flush=True)
            after = manifest.stat()
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                raise RuntimeError(f'审计期间清单发生改变: {manifest}')
            inputs.append({'path': str(manifest.relative_to(ROOT)), 'bytes': before.st_size, 'sha256': hasher.hexdigest()})
    report = {'scope': '全部指定清单；只读结构/词表/长度检查；未逐图核对，不判断数学等式真假。标志计数可重叠。',
              'inputs': inputs, 'counts': counts, 'flags': reasons, 'by_source': dict(by_source),
              'oov_token_sample_counts': dict(unknown.most_common()),
              'new_token_sample_counts': {t: new_coverage[t] for t in sorted(new_tokens, key=vocab.get)},
              'eligible_new_token_sample_counts': {t: eligible_new_coverage[t] for t in sorted(new_tokens, key=vocab.get)},
              'examples': dict(examples)}
    (output / 'summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'counts': counts, 'flags': reasons, 'output': str(output)}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifests', type=Path, nargs='+')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    audit([p.resolve() for p in args.manifests], args.output.resolve())
