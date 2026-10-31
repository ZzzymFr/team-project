"""复用 train.py 的全部训练/验证流程，冻结 CNN 参数与 BatchNorm 统计。

默认读取并更新 checkpoints/model.pt，日志及 last.pt 也保存到 checkpoints/。
与 train.py 一样每次重建优化器和调度器。
"""
from train import main as training_main


def main(argv=None):
    training_main(argv, freeze_cnn=True)


if __name__ == "__main__":
    main()
