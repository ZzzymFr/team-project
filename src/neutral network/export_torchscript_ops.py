"""仅导出卷积、归一化、SiLU、线性层和完整 decoder；不导出 generate。

运行：.venv/bin/python 'src/neutral network/export_torchscript_ops.py'
生成 checkpoints/model_ops.pt 和 model_ops.json（方法清单）。
"""

import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys
import tempfile
from typing import Optional

import torch
from torch import nn
import torch.nn.functional as F

from model import attention_heads, d_model, vital_mini


ROOT = Path(__file__).resolve().parents[2]
LAYER_TYPES = (nn.Conv2d, nn.BatchNorm2d, nn.Linear, nn.LayerNorm)
DECODER_PREFIXES = ('decoder1', 'decoder2', 'decoder3', 'judge_decoder')


def layer_methods(model):
    """仅导出实际基础层，避免递归编译原模型的搜索和 Python 工具方法。"""
    return {name.replace('.', '_'): (name, layer)
            for name, layer in model.named_modules()
            if isinstance(layer, LAYER_TYPES)}


class ExportAttention(nn.Module):
    """完整的原 attention_module：含 pre-norm、投影、mask、Dropout 和残差。"""
    def __init__(self, source):
        super().__init__()
        self.heads = attention_heads
        self.width = d_model
        self.head_dim = source.head_dim
        self.q_proj = source.q_proj
        self.k_proj = source.k_proj
        self.v_proj = source.v_proj
        self.out_proj = source.out_proj
        self.norm = source.norm
        self.dropout = source.dropout

    def forward(self, x: torch.Tensor, memory: Optional[torch.Tensor] = None,
                attn_mask: Optional[torch.Tensor] = None,
                key_padding_mask: Optional[torch.Tensor] = None) -> torch.Tensor:
        residual = x
        x = self.norm(x)
        kv = x if memory is None else memory
        batch, tq, tk = x.size(0), x.size(1), kv.size(1)
        q = self.q_proj(x).reshape(batch, tq, self.heads, self.head_dim).transpose(1, 2)
        k = self.k_proj(kv).reshape(batch, tk, self.heads, self.head_dim).transpose(1, 2)
        v = self.v_proj(kv).reshape(batch, tk, self.heads, self.head_dim).transpose(1, 2)
        scores = torch.matmul(q, k.transpose(-2, -1)) / math.sqrt(float(self.head_dim))
        # Java 的 NDList 不能传 None；零元素 BOOL 张量也表示不应用 mask。
        if attn_mask is not None and attn_mask.numel() > 0:
            scores = scores.masked_fill(attn_mask, float('-inf'))
        if key_padding_mask is not None and key_padding_mask.numel() > 0:
            scores = scores.masked_fill(key_padding_mask[:, None, None, :], float('-inf'))
        weights = self.dropout(F.softmax(scores, dim=-1))
        out = torch.matmul(weights, v).transpose(1, 2).reshape(batch, tq, self.width)
        return residual + self.dropout(self.out_proj(out))


class ExportFFN(nn.Module):
    """完整 FFN，SwiGLU 在内部执行，不生成独立导出入口。"""
    def __init__(self, source):
        super().__init__()
        self.norm = source.norm
        self.gate = source.gate
        self.up = source.up
        self.down = source.down
        self.dropout = source.dropout

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        normalized = self.norm(x)
        hidden = F.silu(self.gate(normalized)) * self.up(normalized)
        return self.dropout(self.down(hidden)) + x


class ExportDecoder(nn.Module):
    """与 model.py 的 decoder 对齐：因果自注意力 -> 交叉注意力 -> FFN。"""
    def __init__(self, source):
        super().__init__()
        self.self_attention = ExportAttention(source.self_attention)
        self.cross_attention = ExportAttention(source.cross_attention)
        self.ffn = ExportFFN(source.ffn)

    def forward(self, x: torch.Tensor, memory: torch.Tensor,
                tgt_key_padding_mask: Optional[torch.Tensor] = None,
                memory_key_padding_mask: Optional[torch.Tensor] = None) -> torch.Tensor:
        length = x.size(1)
        causal = torch.triu(torch.ones((length, length), dtype=torch.bool, device=x.device), diagonal=1)
        x = self.self_attention(x, None, causal, tgt_key_padding_mask)
        x = self.cross_attention(x, memory, None, memory_key_padding_mask)
        return self.ffn(x)


class TensorOps(nn.Module):
    def __init__(self, model):
        super().__init__()
        for name, (_, layer) in layer_methods(model).items():
            self.add_module('layer_' + name, layer)
        for name in DECODER_PREFIXES:
            self.add_module('block_' + name, ExportDecoder(getattr(model, name)))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        raise RuntimeError('Use a named exported method, not forward.')

    @torch.jit.export
    def silu(self, x: torch.Tensor) -> torch.Tensor:
        return F.silu(x)


def load_model(checkpoint_path):
    checkpoint = torch.load(checkpoint_path, map_location='cpu', weights_only=True)
    state = checkpoint.get('model', checkpoint)
    model = vital_mini(num_tokens=state['token_embedding.weight'].size(0))
    # 不用随机值补缺失权重；导出必须严格匹配已有模型，包括判断分支。
    model.load_state_dict(state, strict=True)
    return model.eval()


def build_scripted(model):
    """为每个具名层生成简单包装方法，不手工维护数十份重复代码。

    TorchScript 需要可读取的 Python 源码，因此先写入临时 .py 文件。
    导出后的 .pt 自带程序和权重，不依赖该临时文件或 Python 环境。
    """
    module_name = '_generated_model_ops'
    source = 'import torch\nfrom typing import Optional\nfrom export_torchscript_ops import TensorOps\n\nclass ModelOps(TensorOps):\n'
    for name in layer_methods(model):
        source += (f'    @torch.jit.export\n'
                   f'    def {name}(self, x: torch.Tensor) -> torch.Tensor:\n'
                   f'        return self.layer_{name}(x)\n\n')
    for name in DECODER_PREFIXES:
        source += (f'    @torch.jit.export\n'
                   f'    def {name}(self, x: torch.Tensor, memory: torch.Tensor, '
                   f'tgt_key_padding_mask: Optional[torch.Tensor] = None, '
                   f'memory_key_padding_mask: Optional[torch.Tensor] = None) -> torch.Tensor:\n'
                   f'        return self.block_{name}(x, memory, tgt_key_padding_mask, memory_key_padding_mask)\n\n')
    with tempfile.TemporaryDirectory(prefix='model-ops-') as directory:
        path = Path(directory) / (module_name + '.py')
        path.write_text(source, encoding='utf-8')
        spec = importlib.util.spec_from_file_location(module_name, path)
        generated = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = generated
        try:
            spec.loader.exec_module(generated)
            return torch.jit.script(generated.ModelOps(model).eval())
        finally:
            sys.modules.pop(module_name, None)


def export(checkpoint_path, output_path):
    model = load_model(checkpoint_path)
    scripted = build_scripted(model)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    # 不调用 freeze：保留所有按名称调用的方法。
    torch.jit.save(scripted, str(output_path))
    reloaded = torch.jit.load(str(output_path), map_location='cpu').eval()
    methods = {name: str(reloaded._c._get_method(name).schema)
               for name in reloaded._c._method_names() if not name.startswith('_') and name != 'forward'}
    manifest = {
        'checkpoint': checkpoint_path.name,
        'checkpoint_sha256': hashlib.sha256(checkpoint_path.read_bytes()).hexdigest(),
        'torch_version': torch.__version__,
        'mode': 'eval (inference only; dropout is identity, BN uses saved statistics)',
        'vocab_size': model.token_embedding.num_embeddings,
        'layers': {name: {'python_path': path, 'type': type(layer).__name__}
                   for name, (path, layer) in layer_methods(model).items()},
        'decoder_blocks': {name: {'python_path': name, 'includes': 'causal self-attention, cross-attention, FFN (including SwiGLU), all normalization and residual connections'}
                           for name in DECODER_PREFIXES},
        'mask_convention': 'True = blocked; omitted/None or an empty BOOL tensor = no padding mask. Each decoder creates its own causal mask.',
        'scope': 'All Conv2d, BatchNorm2d, LayerNorm and Linear layers (including decoder internals), one shared silu, and four complete decoders. No separate SwiGLU, attention or FFN block methods.',
        'methods': methods,
    }
    output_path.with_suffix('.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Exported {len(methods)} methods to {output_path}')
    return reloaded


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint', type=Path, default=ROOT / 'checkpoints/model.pt')
    parser.add_argument('--output', type=Path, default=ROOT / 'checkpoints/model_ops.pt')
    args = parser.parse_args()
    if args.checkpoint.resolve() == args.output.resolve():
        parser.error('Output must not overwrite the training checkpoint.')
    export(args.checkpoint, args.output)
