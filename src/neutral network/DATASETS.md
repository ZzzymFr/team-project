# 公式训练数据

在项目根目录运行（使用已安装依赖的 Python）：

```bash
python -m pip install -r "src/neutral network/requirement-data.txt"
python "src/neutral network/data.py"
python "src/neutral network/train.py" --data "src/neutral network/data/mixed"
```

`data.py` 默认准备三个数据源，原始数据保留在各自目录；混合清单位于
`data/mixed/train.jsonl`、`valid.jsonl`、`test.jsonl`，实际条数与去重统计写入
`data/mixed/summary.json`。`train.py` 默认使用全部可用样本；调试时可以传
`--max-samples 1000`。

## 下载与复用

- MathWriting 使用 Google 官方压缩包，复用原来的 `data/mathwriting-2024/`，补齐 `synthetic` 和 `test`。
- UniMER 使用 `wanderkid/UniMER_Dataset`，HME100K 使用 `ConstantHao/HME100K`。
  HF 版本固定在 `data.py` 的 `HF_SOURCES`，避免重跑时自动切换数据版本。
- 完整下载有本地完成记录，大小或修改时间不变时不再请求下载；下载中断时保留临时文件并续传。
- 已完成的解压和转换使用完成记录复用。`--rebuild` 重建转换清单，仍复用完整下载。
- 可用 `--sources mathwriting` 或 `--sources unimer hme100k-hf` 仅准备指定来源。
  输出混合清单只包含本次选择的来源；再次运行默认命令可恢复三者混合。
- `--sample` 只准备 MathWriting 示例包，输出到独立的 `data/mixed-sample`。
- 全量运行需要 `huggingface_hub`、`pyarrow` 和 Pillow；这组依赖单独使用 PyPI，
  不受 CUDA 版 PyTorch 的下载源影响。

## 混合与验证划分

MathWriting 的 train 和 synthetic 用于训练，保留官方 valid/test。
UniMER、HME100K 的官方 test 只进入 test；从它们的训练集按公式哈希固定划分
5% 为 valid（`--valid-fraction` 可调整），因此重跑不会随机改变划分。

去重按图片解码后的灰度像素或 InkML 文件内容进行。跨分区相同 token 序列的
公式采用 test > valid > train 的优先级，避免相同公式进入训练与评估两边。
这会减少可用训练量，也会使混合 test 与原始官方基准的样本集合不同。
不同缩放、裁切或不同 LaTeX 写法的近似重复，不保证全部识别。

UniMER 的图片名整数对应标签文件的零基行号；保留空行的索引位置，空标签样本跳过。
混合时不会改写词表；训练仍按 `--oov skip` 和模型长度上限过滤，所以清单条数
不等于最终可训练条数。图片和 InkML 共用 batch 与损失函数，InkML 保留原有随机笔迹增强。
图片统一裁切、缩放和反色为笔迹亮、背景暗，与推理共用 `preprocess.py`。

## 个人手写训练图片

**默认检测 `train.py` 同级的 `train/` 文件夹，而不是 `val/`。**
每张图片放一个完整公式，推荐清晰的白底深色笔迹；文件夹可包含子目录。

```text
src/neutral network/train/
├── formula_0001.png
├── formula_0002.jpg
└── labels.jsonl
```

`labels.jsonl` 每行一个 JSON 对象，图片路径相对于该标签文件所在目录。
JSON 中 LaTeX 的反斜杠必须写成两个：

```jsonl
{"image":"formula_0001.png","latex":"x+1=2"}
{"image":"formula_0002.jpg","latex":"\\frac{a}{b}"}
```

默认训练会把这些标注图片加入混合训练数据；空目录会跳过，有图片却没有标签文件
会明确报错。`val/` 不会自动加入训练。可用 `--personal-train 路径` 指定另一个
个人训练目录，用 `--no-personal-train` 禁用自动加入。

还支持额外数据根目录 `--image-data my_dataset`，其结构可以是：

```text
my_dataset/
├── train/labels.jsonl
└── valid/labels.jsonl   # 可选；没有时只加入训练数据
```

或在该根目录直接放 `train.jsonl`、`valid.jsonl`。清单中的 `image` 也可写作 `path`；
InkML 记录使用 `"format":"inkml"`，图片默认是 `"format":"image"`。
同一原始页面的裁切图片应放在同一个分区，不要把训练图片的副本放进验证集。

## 数据来源

- [MathWriting 官方](https://github.com/google-research/google-research/tree/master/mathwriting)
- [UniMER 数据集](https://huggingface.co/datasets/wanderkid/UniMER_Dataset)
- [UniMER 官方图片/标签加载规则](https://github.com/opendatalab/UniMERNet/blob/main/unimernet/datasets/datasets/formula.py)
- [HME100K HF 社区版](https://huggingface.co/datasets/ConstantHao/HME100K)
- [HME100K 官方](https://github.com/Phymond/HME100K)
