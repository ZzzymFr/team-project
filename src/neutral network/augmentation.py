"""训练专用的温和 RGB 图片增强；输入输出沿用预处理后的 [3,128,W]。"""
import math
import random

import numpy as np
import torch
from PIL import Image, ImageFilter

DEFAULT_AUGMENT_PROBABILITY = 0.7
AUGMENTATION_VERSION = 1


def _geometry(image: Image.Image, rng, background: tuple[int, int, int]) -> Image.Image:
    """扩展旋转/倾斜画布，再完整缩入原尺寸；不裁剪、不翻转任何公式部分。"""
    width, height = image.size
    # 长公式限制旋转引起的额外高度，避免整体被缩得过小。
    angle_limit = min(4.0, math.degrees(math.atan(0.25 * height / width)))
    image = image.rotate(rng.uniform(-angle_limit, angle_limit),
                         resample=Image.Resampling.BICUBIC, expand=True, fillcolor=background)
    shear = rng.uniform(-0.06, 0.06)
    expanded_width = image.width + math.ceil(abs(shear) * image.height)
    image = image.transform((expanded_width, image.height), Image.Transform.AFFINE,
                            (1, -shear, min(0, shear * image.height), 0, 1, 0),
                            resample=Image.Resampling.BICUBIC, fillcolor=background)
    scale = min(width / image.width, height / image.height) * rng.uniform(0.9, 1.0)
    size = (max(1, min(width, round(image.width * scale))),
            max(1, min(height, round(image.height * scale))))
    image = image.resize(size, Image.Resampling.LANCZOS)
    canvas = Image.new('RGB', (width, height), background)
    canvas.paste(image, (rng.randint(0, width - size[0]), rng.randint(0, height - size[1])))
    return canvas


def augment_formula_image(image: torch.Tensor, rng=None) -> torch.Tensor:
    """只在训练 Dataset 中调用；使用 Python RNG，兼容主进程及 DataLoader worker 的种子。"""
    if image.device.type != 'cpu' or image.ndim != 3 or image.size(0) != 3:
        raise ValueError('图片增强需要 CPU RGB [3,H,W] 张量')
    rng = random if rng is None else rng
    pixels = image.permute(1, 2, 0).numpy()
    picture = Image.fromarray(np.rint(np.clip(pixels, 0, 1) * 255).astype(np.uint8))
    background = picture.getpixel((0, 0))
    if rng.random() < 0.8:
        picture = _geometry(picture, rng, background)
    if rng.random() < 0.2:
        picture = picture.filter(ImageFilter.GaussianBlur(rng.uniform(0.15, 0.45)))
    pixels = np.asarray(picture, dtype=np.float32) / 255
    bg = np.asarray(background, dtype=np.float32) / 255
    light_background = bool(np.median(bg) >= 0.5)
    # 根据 RGB 对比度改变笔迹颜色；不把彩色图片转成灰度。
    if rng.random() < 0.35:
        colour = np.array([rng.uniform(0.05, 0.35) for _ in range(3)], dtype=np.float32)
        if not light_background:
            colour = 1 - colour
        strength = np.max(np.abs(pixels - bg), axis=2, keepdims=True) * rng.uniform(0.3, 0.8)
        pixels = pixels * (1 - strength) + colour * strength
    pixels = bg + (pixels - bg) * rng.uniform(0.9, 1.1)
    # 小幅改变背景和光照；保持深浅极性及足够的笔迹对比度。
    pixels = pixels * rng.uniform(0.94, 1.0)
    if not light_background:
        pixels += rng.uniform(0, 0.03)
    if rng.random() < 0.2:
        noise_rng = np.random.default_rng(rng.getrandbits(64))
        # 保留边缘补边颜色，让组批时的背景填充稳定。
        pixels[2:-2, 2:-2] += noise_rng.normal(0, rng.uniform(0.002, 0.008),
                                              pixels[2:-2, 2:-2].shape).astype(np.float32)
    return torch.from_numpy(np.clip(pixels, 0, 1)).permute(2, 0, 1).contiguous()
