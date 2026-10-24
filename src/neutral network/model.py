import json
import math
from dataclasses import dataclass, field
from pathlib import Path
import torch
import torch.nn as nn
import torch.nn.functional as F

d_model = 256
attention_heads = 4
vocab_size = 148                      # 末尾追加空格；原有 0..146 的 token ID 不变
pad_id, bos_id, eos_id = 67, 68, 69   # 与 tokeniser.json 中的特殊 token 对应
max_len = 300                        # LaTeX token 序列的最大长度(含 <bos>/<eos>)

'''输入的照片应该满足以下格式
宽度像素是16的倍数，高度是128像素
原始照片可以通过缩放或补齐背景来满足要求，补齐背景的颜色最好是白色
'''

def SwiGLU(gate: torch.Tensor, content: torch.Tensor) -> torch.Tensor:
    """gate/content 均为 [B,T,m]；SiLU 门控与内容逐元素相乘，不混合位置。"""
    return F.silu(gate) * content

def load_vocab(path: Path = Path(__file__).with_name("tokeniser.json")) -> list[str]:
    # tokeniser.json 里是 token -> id,翻转成下标即 id 的列表
    with open(path, encoding="utf-8") as f:
        vocab = json.load(f)["base"]["vocab"]
    assert len(vocab) == vocab_size, f"tokeniser.json 有 {len(vocab)} 个 token,但 vocab_size = {vocab_size}"
    table = [""] * len(vocab)
    for token, i in vocab.items():
        table[i] = token
    return table

def ids_to_latex(ids: list[int], table: list[str]) -> str:
    # 仅在命令后紧接英文字母时补分隔符，保留 \text{...} 内真正的空格。
    out = []
    for position, i in enumerate(ids):
        t = table[i]
        next_token = table[ids[position + 1]] if position + 1 < len(ids) else ""
        needs_separator = (t.startswith("\\") and t[1:].isascii() and t[1:].isalpha()
                           and next_token and next_token[0].isascii() and next_token[0].isalpha())
        out.append(t + " " if needs_separator else t)
    return "".join(out).strip()


def load_training_weights(model: nn.Module, state_dict: dict) -> bool:
    """兼容追加空格前的 147 词权重；只扩展共享词嵌入/输出矩阵的一行。"""
    keys = ("token_embedding.weight", "head.weight")
    expanded = all(state_dict[key].shape == (147, d_model) for key in keys)
    if expanded:
        if load_vocab()[147:] != [" "]:
            raise ValueError("旧权重仅支持在原有词表末尾追加空格")
        state_dict = dict(state_dict)
        # 两个层共享权重；新行用原有字符嵌入均值初始化，之后参与训练。
        if not torch.equal(state_dict[keys[0]], state_dict[keys[1]]):
            raise ValueError("旧权重的词嵌入与输出层不一致")
        old = state_dict[keys[0]]
        extended = torch.cat((old, old[:67].mean(dim=0, keepdim=True)), dim=0)
        for key in keys:
            state_dict[key] = extended
    model.load_state_dict(state_dict, strict=True)
    return expanded

@dataclass
class BeamState:
    """beam search 在两步之间要带着走的全部状态"""
    tokens: torch.Tensor                           # [k,T] 活跃假设,第 0 列是 <bos>
    scores: torch.Tensor                           # [k] 每条假设的累计 log 概率
    finished: list = field(default_factory=list)   # 已生成 <eos> 的序列: [(归一化得分, [token id])]
    done: bool = False
    length_alpha: float = 0.7

    @staticmethod
    def start(device, length_alpha: float = 0.7) -> "BeamState":
        return BeamState(tokens=torch.full((1, 1), bos_id, dtype=torch.long, device=device),
                         scores=torch.zeros(1, device=device),
                         length_alpha=length_alpha)

    def norm_score(self, score: float, n: int) -> float:
        # 总 log 概率每多一个 token 就更低,除以 n^alpha 抵消偏向短序列的倾向;只用于比较序列
        return score / (max(n, 1) ** self.length_alpha)

    def best(self) -> list[int]:
        """得分最高的序列(不含 <bos>/<eos>);一个 <eos> 都没生成时取最好的活跃假设"""
        cands = list(self.finished)
        if not cands:
            i = int(self.scores.argmax())
            cands.append((self.norm_score(float(self.scores[i]), self.tokens.size(1) - 1),
                          self.tokens[i, 1:].tolist()))
        return max(cands, key=lambda c: c[0])[1]

def parse_tokens(logits: torch.Tensor, state: BeamState | None = None, beam_width: int = 3,
                 max_steps: int = max_len, length_alpha: float = 0.7) -> BeamState:
    """beam search 的一步: 吃进 decode(或 forward)的 logits,吐出新的 BeamState
    logits [k,V] 或 [k,T,V](取最后一位): 当前 k 条活跃假设各自的"下一个 token"分布
    state: 上一步返回的状态,第一步传 None
    beam_width: 束宽,同时保留的候选序列数,1 时等价于贪心
    max_steps: 序列最长长度(含 <bos>/<eos>),不能超过 max_len
    length_alpha: 长度归一化指数,0 表示不归一化;只在 state 为 None 时生效"""
    assert beam_width >= 1 and max_steps <= max_len
    if logits.dim() == 3:
        logits = logits[:, -1]
    dev = logits.device
    if state is None:
        state = BeamState.start(dev, length_alpha)
    k = state.tokens.size(0)
    assert logits.size(0) == k, "logits 的行数必须等于活跃假设数"

    # 1) 新得分 = 旧得分 + log 概率;所有假设 × 所有 token 摊平后一起比较 -> [k*V]
    logp = F.log_softmax(logits.float(), dim=-1)
    logp[:, [pad_id, bos_id]] = float("-inf")      # 这两个只用于输入,不允许被生成
    cand = (state.scores[:, None] + logp).reshape(-1)
    top_scores, top_idx = cand.topk(min(2 * beam_width, cand.numel()))   # 多取一倍,用来补 <eos> 占掉的名额
    src = (top_idx // vocab_size).tolist()         # 这个候选来自第几条假设
    tok = (top_idx % vocab_size).tolist()          # 这个候选选了哪个 token
    sc = top_scores.tolist()

    # 2) 按得分从高到低扫描: <eos> 放进完成列表,其余的补满 beam_width 条活跃假设
    finished = list(state.finished)
    keep: list[int] = []
    for rank in range(len(tok)):
        if tok[rank] == eos_id:
            if rank < beam_width:                  # 排名靠后的 <eos> 不算数
                seq = state.tokens[src[rank], 1:].tolist()                 # 去掉 <bos>
                finished.append((state.norm_score(sc[rank], len(seq) + 1), seq))
        else:
            keep.append(rank)
            if len(keep) == beam_width:
                break
    if not keep:                                   # 前几名全是 <eos>,没有可以继续的假设
        return BeamState(state.tokens, state.scores, finished, True, state.length_alpha)

    # 3) 活跃假设 = 来源假设的前缀 + 新选的 token
    parent = torch.tensor([src[r] for r in keep], device=dev)
    tokens = torch.cat([state.tokens[parent], torch.tensor([[tok[r]] for r in keep], device=dev)], dim=1)
    scores = top_scores[torch.tensor(keep, device=dev)]
    new = BeamState(tokens, scores, finished, False, state.length_alpha)

    # 4) 停止条件: 到达最大长度;或(启发式)已有 beam_width 条完成,且最好的活跃假设已追不上最好的完成序列
    if tokens.size(1) >= max_steps:
        new.done = True
    elif len(finished) >= beam_width:
        best_alive = new.norm_score(scores.max().item(), tokens.size(1) - 1)
        new.done = best_alive <= max(f[0] for f in finished)
    return new

class vital_pixel(nn.Module):
    conv : nn.Conv2d
    conv2 : nn.Conv2d
    conv3 : nn.Conv2d
    conv4 : nn.Conv2d
    norm : nn.BatchNorm2d
    norm2 : nn.BatchNorm2d
    norm3 : nn.BatchNorm2d

    def __init__(self):
        super(vital_pixel, self).__init__()
        self.conv = nn.Conv2d(3, 16, kernel_size=5, stride=1, padding=2, bias=False)
        self.norm = nn.BatchNorm2d(16)
        self.conv2 = nn.Conv2d(16, 32, kernel_size=5, stride=1, padding=2, bias=False)
        self.norm2 = nn.BatchNorm2d(32)
        self.conv3 = nn.Conv2d(32, 64, kernel_size=5, stride=1, padding=2, bias=False)
        self.norm3 = nn.BatchNorm2d(64)
        self.con4 = nn.Conv2d(64, 128, kernel_size=5, stride=2, padding=2, bias=False)
        self.con5 = nn.Conv2d(128, d_model, kernel_size=3, stride=1, padding=1, bias=False)
        self.norm4 = nn.BatchNorm2d(128)
        self.norm5 = nn.BatchNorm2d(d_model)


    def position_embedding(self, x: torch.Tensor) -> torch.Tensor:
        # 2D sinusoidal encoding: first half of channels encodes the row, second half the column
        _, c, h, w = x.shape
        half = c // 2

        def encode(n: int) -> torch.Tensor:  # [n, half]
            pos = torch.arange(n, device=x.device, dtype=x.dtype).unsqueeze(1)
            freq = torch.exp(torch.arange(0, half, 2, device=x.device, dtype=x.dtype) * (-math.log(10000.0) / half))
            ang = pos * freq
            return torch.stack([torch.sin(ang), torch.cos(ang)], dim=2).flatten(1)

        row = encode(h).t().unsqueeze(2).expand(half, h, w)
        col = encode(w).t().unsqueeze(1).expand(half, h, w)
        return x + torch.cat([row, col], dim=0).unsqueeze(0)
 
        
    
    def forward(self, x: torch.Tensor):
        x = self.conv(x)
        x = self.norm(x)
        x = F.silu(x)
        x = F.max_pool2d(x, kernel_size=2, stride=2)
        x = self.conv2(x)
        x = self.norm2(x)
        x = F.silu(x)
        x = F.max_pool2d(x, kernel_size=2, stride=2)
        x = self.conv3(x)
        x = self.norm3(x)
        x = F.silu(x)
        x = F.max_pool2d(x, kernel_size=2, stride=2)
        x = self.con4(x)
        x = self.norm4(x)
        x = F.silu(x)
        x = self.con5(x)
        x = self.norm5(x)
        x = self.position_embedding(x)
        return x


class attention_module(nn.Module):
    def __init__(self, dropout: float = 0.1):
        super(attention_module, self).__init__()
        assert d_model % attention_heads == 0
        self.head_dim = d_model // attention_heads
        self.q_proj = nn.Linear(d_model, d_model)
        self.k_proj = nn.Linear(d_model, d_model)
        self.v_proj = nn.Linear(d_model, d_model)
        self.out_proj = nn.Linear(d_model, d_model)
        self.dropout = nn.Dropout(dropout)
        self.norm = nn.LayerNorm(d_model)

    def forward(self, x: torch.Tensor, memory: torch.Tensor | None = None,
                attn_mask: torch.Tensor | None = None,
                key_padding_mask: torch.Tensor | None = None) -> torch.Tensor:
        # x [B,Tq,d]; memory [B,Tk,d] for cross-attention (None -> self-attention)
        # attn_mask [Tq,Tk] and key_padding_mask [B,Tk] are bool, True = do not attend

        residual = x
        x = self.norm(x)
        kv = x if memory is None else memory
        b, tq, _ = x.shape
        tk = kv.size(1)

        q = self.q_proj(x).view(b, tq, attention_heads, self.head_dim).transpose(1, 2)   # [B,H,Tq,hd]
        k = self.k_proj(kv).view(b, tk, attention_heads, self.head_dim).transpose(1, 2)  # [B,H,Tk,hd]
        v = self.v_proj(kv).view(b, tk, attention_heads, self.head_dim).transpose(1, 2)  # [B,H,Tk,hd]

        scores = q @ k.transpose(-2, -1) / math.sqrt(self.head_dim)                     # [B,H,Tq,Tk] q*k^T / sqrt(d_k)

        if attn_mask is not None:
            scores = scores.masked_fill(attn_mask, float("-inf"))
        if key_padding_mask is not None:
            scores = scores.masked_fill(key_padding_mask[:, None, None, :], float("-inf"))

        attn = self.dropout(F.softmax(scores, dim=-1))
        out = (attn @ v).transpose(1, 2).reshape(b, tq, d_model)                        # merge heads
        out = self.dropout(self.out_proj(out))
        return residual + out  # residual connection

class FFN(nn.Module):

    fc1 : nn.Linear
    fc2 : nn.Linear
    def __init__(self):
        super(FFN, self).__init__()
        m = 1024
        self.norm = nn.LayerNorm(d_model)
        self.gate = nn.Linear(d_model, m)
        self.up = nn.Linear(d_model, m)
        self.down = nn.Linear(m, d_model)
        self.dropout = nn.Dropout(0.2)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        normalised = self.norm(x)
        hidden = SwiGLU(self.gate(normalised), self.up(normalised))
        return self.dropout(self.down(hidden)) + x

class decoder(nn.Module):

    self_attention : attention_module
    cross_attention : attention_module
    ffn : FFN

    def __init__(self):
        super(decoder, self).__init__()
        self.self_attention = attention_module()
        self.ffn = FFN()
        self.cross_attention = attention_module()

    def forward(self, x: torch.Tensor, memory: torch.Tensor,
                tgt_key_padding_mask: torch.Tensor | None = None,
                memory_key_padding_mask: torch.Tensor | None = None) -> torch.Tensor:
        # x [B,T,d]: token 序列; memory [B,S,d]: 图像特征(S = h*w)
        # tgt_key_padding_mask [B,T]: <pad> token 的位置; memory_key_padding_mask [B,S]: 图像补边的格子; True = 屏蔽
        T = x.size(1)
        # 因果 mask: 第 i 个 token 只能看 0..i,上三角(不含对角线)为 True 即屏蔽未来
        causal = torch.triu(torch.ones(T, T, dtype=torch.bool, device=x.device), diagonal=1)
        # 自注意力: 看自己已经写了什么(括号配对、语法)
        x = self.self_attention(x, attn_mask=causal, key_padding_mask=tgt_key_padding_mask)
        # 交叉注意力: 去图像里找下一个该写什么;图像一次给全,所以不用因果 mask
        x = self.cross_attention(x, memory=memory, key_padding_mask=memory_key_padding_mask)
        return self.ffn(x)


class vital_mini(nn.Module):

    cnn : vital_pixel
    decoder1 : decoder
    decoder2 : decoder
    decoder3 : decoder
    token_embedding : nn.Embedding
    final_norm : nn.LayerNorm
    head : nn.Linear

    def __init__(self, num_tokens: int = vocab_size):
        super(vital_mini, self).__init__()
        self.cnn = vital_pixel()
        self.decoder1 = decoder()
        self.decoder2 = decoder()
        self.decoder3 = decoder()

        # token id -> 向量; <pad> 对应的行保持为 0
        self.token_embedding = nn.Embedding(num_tokens, d_model, padding_idx=pad_id)
        nn.init.normal_(self.token_embedding.weight, mean=0.0, std=d_model ** -0.5)  # 配合 forward 里乘 sqrt(d)
        with torch.no_grad():
            self.token_embedding.weight[pad_id].zero_()
        self.embed_dropout = nn.Dropout(0.1)
        # token 序列的 1D 正弦位置表(固定值,不训练,不进 state_dict);图像的 2D 位置编码在 vital_pixel 里
        self.register_buffer("token_pos", self.build_token_pos(max_len), persistent=False)

        self.final_norm = nn.LayerNorm(d_model)
        self.head = nn.Linear(d_model, num_tokens, bias=False)
        self.head.weight = self.token_embedding.weight

    @staticmethod
    def build_token_pos(n: int) -> torch.Tensor:  # -> [1, n, d_model]
        pos = torch.arange(n, dtype=torch.float).unsqueeze(1)
        freq = torch.exp(torch.arange(0, d_model, 2, dtype=torch.float) * (-math.log(10000.0) / d_model))
        ang = pos * freq
        return torch.stack([torch.sin(ang), torch.cos(ang)], dim=2).flatten(1).unsqueeze(0)

    def decoder_layers(self) -> tuple[decoder,...]:
        return (self.decoder1, self.decoder2, self.decoder3)

    def encode(self, x: torch.Tensor) -> torch.Tensor:#[B,C,H,W] -> [B,H*W,C]
        # [B,C,h,w] -> [B,h*w,C]: flatten h,w row-major, then move channels last
        return x.flatten(2).transpose(1, 2)

    def encode_image(self, image: torch.Tensor) -> torch.Tensor:
        # 图像 [B,3,H,W] -> memory [B,h*w,d];导出 ONNX 时这一段就是 encoder.onnx
        return self.encode(self.cnn(image))

    def decode(self, tokens: torch.Tensor, memory: torch.Tensor,
               tgt_key_padding_mask: torch.Tensor | None = None,
               memory_key_padding_mask: torch.Tensor | None = None) -> torch.Tensor:
        # tokens [B,T] (long), memory [B,S,d] -> logits [B,T,vocab];导出 ONNX 时这一段就是 decoder_step.onnx
        T = tokens.size(1)
        assert T <= max_len, "token sequence is longer than max_len"
        # 嵌入乘 sqrt(d) 再加位置编码,让两者量级相当
        x = self.token_embedding(tokens) * math.sqrt(d_model) + self.token_pos[:, :T]
        x = self.embed_dropout(x)
        for layer in self.decoder_layers():
            x = layer(x, memory, tgt_key_padding_mask, memory_key_padding_mask)
        return self.head(self.final_norm(x))#[B,T,vocab]

    def forward(self, image: torch.Tensor, tokens: torch.Tensor,
                tgt_key_padding_mask: torch.Tensor | None = None,
                memory_key_padding_mask: torch.Tensor | None = None) -> torch.Tensor:
        # 训练(teacher forcing): tokens 是 <bos> y1..yn,logits[:, t] 预测的是 y_{t+1}
        memory = self.encode_image(image)
        decoded = self.decode(tokens, memory, tgt_key_padding_mask, memory_key_padding_mask)
        return decoded 


    @torch.no_grad()
    def generate(self, image: torch.Tensor, beam_width: int = 3, max_steps: int = max_len,
                 length_alpha: float = 0.7) -> list[list[int]]:
        # image [B,3,H,W] -> B 条 token id 序列(不含 <bos>/<eos>);转成字符串用 ids_to_latex
        # 每一轮循环: decode 出 logits -> parse_tokens 选出新的 k 条假设 -> 下一轮把它们整条重新喂进去
        was_training = self.training
        self.eval()                                           # 关 dropout;BatchNorm 使用滑动统计
        results = []
        for i in range(image.size(0)):                        # 每张图各做一次 beam search
            memory = self.encode_image(image[i:i + 1])        # 每张图只过一次 CNN
            tokens = torch.full((1, 1), bos_id, dtype=torch.long, device=image.device)
            state = None
            while True:
                k = tokens.size(0)                            # 当前活跃假设数,k 条拼成一个 batch
                logits = self.decode(tokens, memory.expand(k, -1, -1))[:, -1]
                state = parse_tokens(logits, state, beam_width, max_steps, length_alpha)
                if state.done:
                    break
                tokens = state.tokens
            results.append(state.best())
        self.train(was_training)
        return results
