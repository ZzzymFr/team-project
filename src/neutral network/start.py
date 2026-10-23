import argparse
from pathlib import Path

import torch

from model import ids_to_latex, load_vocab, vital_mini
from preprocess import load_formula_image

# 使用脚本位置定位项目根目录，避免 IDE 的工作目录影响权重路径。
WEIGHTS_PATH = Path(__file__).resolve().parents[2] / "checkpoints" / "model.pt"
val_path = Path(__file__).resolve().parents[0] / "val"
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".webp", ".tif", ".tiff"}


def load_image(path: Path, invert: bool | None = None) -> torch.Tensor:
    return load_formula_image(path, invert).unsqueeze(0).expand(1, 3, -1, -1).contiguous()


def main():
    parser = argparse.ArgumentParser(description="加载 val 目录中的公式图片并输出 LaTeX")
    inversion = parser.add_mutually_exclusive_group()
    inversion.add_argument("--invert", dest="invert", action="store_true", help="强制反色")
    inversion.add_argument("--no-invert", dest="invert", action="store_false", help="不反色，保留原图明暗")
    parser.set_defaults(invert=None)
    args = parser.parse_args()

    if not val_path.is_dir():
        raise FileNotFoundError(f"图片目录不存在: {val_path}")
    image_paths = sorted(p for p in val_path.iterdir()
                         if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS)
    if not image_paths:
        print(f"没有找到图片，请将单张公式图片放入: {val_path}")
        return

    device = torch.device("cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu")
    if not WEIGHTS_PATH.is_file():
        raise FileNotFoundError(f"模型权重不存在: {WEIGHTS_PATH}")

    # train.py 保存的是含 model、epoch 等字段的 checkpoint。
    checkpoint = torch.load(WEIGHTS_PATH, map_location="cpu", weights_only=True)
    model = vital_mini()
    model.load_state_dict(checkpoint["model"], strict=True)
    model = model.to(device)
    model.eval()
    print(f"已加载模型权重: {WEIGHTS_PATH} | device: {device}")
    table = load_vocab()
    with torch.inference_mode():
        for path in image_paths:
            try:
                image = load_image(path, invert=args.invert)
            except (OSError, ValueError) as error:
                print(f"{path.name}: 跳过，{error}", flush=True)
                continue
            token_ids = model.generate(image.to(device), beam_width=3)[0]
            latex = ids_to_latex(token_ids, table)
            print(f"{path.name}: {latex or '(空结果)'}", flush=True)


if __name__ == "__main__":
    main()
