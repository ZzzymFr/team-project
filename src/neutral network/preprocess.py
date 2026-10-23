"""训练和识别共用的公式图片预处理。"""
import math
from pathlib import Path

import numpy as np
import torch
from PIL import Image, ImageOps


def load_formula_image(path: Path, invert: bool | None = None) -> torch.Tensor:
    """单张公式图 -> [1, 128, W]；invert=None 自动判断，否则强制开/关反色。"""
    with Image.open(path) as source:
        rgba = ImageOps.exif_transpose(source).convert("RGBA")
        background = Image.new("RGBA", rgba.size, "white")
        gray = Image.alpha_composite(background, rgba).convert("L")

    pixels = np.asarray(gray)
    border = np.concatenate((pixels[0], pixels[-1], pixels[:, 0], pixels[:, -1]))
    light_background = bool(np.median(border) >= 128)
    should_invert = light_background if invert is None else invert
    # 裁切始终根据原图背景定位笔迹，不受输出是否反色影响。
    foreground = ImageOps.invert(gray) if light_background else gray
    bounds = foreground.point(lambda p: 255 if p > 32 else 0).getbbox()
    if bounds is None:
        raise ValueError("图片中没有可识别的笔迹")
    ink = (ImageOps.invert(gray) if should_invert else gray).crop(bounds)

    # 对齐 train.py 的高度、边距、最大宽度以及横线的缩放规则。
    height, margin, stride, max_width, min_width = 128, 8, 16, 1024, 64
    width, ink_height = ink.size
    scale = min((height - 2 * margin) / max(ink_height, 0.2 * width),
                (max_width - 2 * margin) / width)
    resized_width = max(1, round(width * scale))
    resized_height = max(1, round(ink_height * scale))
    ink = ink.resize((resized_width, resized_height), Image.Resampling.LANCZOS)
    canvas_width = max(min_width, math.ceil((resized_width + 2 * margin) / stride) * stride)
    background_value = 255 if light_background != should_invert else 0
    canvas = Image.new("L", (canvas_width, height), background_value)
    canvas.paste(ink, ((canvas_width - resized_width) // 2, (height - resized_height) // 2))
    tensor = torch.from_numpy(np.asarray(canvas, dtype=np.float32) / 255.0)
    return tensor.unsqueeze(0)

