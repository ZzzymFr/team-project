# 冻结 CNN 的训练

`train_decoder.py` 直接调用 `train.py` 的公共训练入口。数据增强、来源混合采样、批次构造、损失、优化器、学习率调度、验证、整式识别率抽样及日志都使用同一份实现。

区别是 CNN 参数不参与优化，每轮进入训练模式后重新调用 `cnn.eval()`，固定 BatchNorm 的运行均值、方差和计数器。生成解码器的自注意力、交叉注意力、前馈层、词嵌入及共享输出层继续训练；判断分支沿用 `train.py` 的冻结策略。这仍是图文识别训练，不是纯文本语言建模。

在仓库根目录的 PowerShell 中运行：

```powershell
& "src/neutral network/.venv/Scripts/python.exe" "src/neutral network/data.py" --sources crohme2023
& "src/neutral network/.venv/Scripts/python.exe" "src/neutral network/train_decoder.py" --data "src/neutral network/data/crohme2023-mixed"
```

默认读取并更新根目录下的 `checkpoints/model.pt`，`last.pt`、训练日志和验证抽样也保存到 `checkpoints/`。与 `train.py` 一样，每次启动重新创建优化器和学习率调度器。可以用 `--out DIR` 指定其他输出目录。

- `--init-weights PATH`：输出模型不存在时使用的初始权重，只读。
- `--out DIR`：日志、验证抽样以及默认模型输出目录。
- `--weights PATH`：与 `train.py` 相同，读取并原位更新指定模型；`last.pt` 保存在它的同级目录。
- 缺少初始权重时拒绝启动，避免冻结随机初始化的 CNN。
- 不指定 `--data` 时仍使用原来的 `data/mixed`；其他选项见 `--help`。

## CROHME 2023 数据

官方发布入口为 https://zenodo.org/records/8428035，下载文件 `CROHME23.zip`；下载器校验官方 MD5，并记录 SHA-256。支持分段续传和已完成下载的离线复用。

数据解压到 `data/crohme2023/TC11_CROHME23/`。转换器保留官方 train/val/test 划分，把 val 映射成训练程序使用的 valid。选择包含完整表达式 LaTeX 和轨迹的 InkML，每个表达式只导入一次，由现有渲染函数生成训练图片。只有 PNG / SymLG 的部分保留在原始数据中，暂不导入训练清单；转换统计见 `data/crohme2023/conversion.json`。

训练部分包含官方 `Artificial_data` 合成笔迹，不全是真实手写采集。实际训练仍沿用 `train.py` 的长度和词表过滤规则，因此可用样本数会少于清单条数。

默认运行 `data.py` 会把 MathWriting、UniMER、HME100K 和 CROHME 2023 合并到 `data/mixed/`，两个训练入口默认都会使用这个目录。单独指定 `--sources crohme2023` 时生成 `data/crohme2023-mixed/`。混合清单按当前标签规范化规则排除跨分区相同公式。也可以显式生成其他混合目录：

```powershell
& "src/neutral network/.venv/Scripts/python.exe" "src/neutral network/data.py" --sources mathwriting unimer hme100k-hf crohme2023 --out "src/neutral network/data/mixed-decoder"
& "src/neutral network/.venv/Scripts/python.exe" "src/neutral network/train_decoder.py" --data "src/neutral network/data/mixed-decoder"
```

不要直接拼接不同数据集的训练清单；通过混合步骤统一处理训练、验证和测试的重复公式。完整来源、许可与说明保留在解压包的 `readme.md` 中。
