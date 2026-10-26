"""按人工核对的裁切框导入本批笔记；固定随机种子，重复执行不重复追加标签。"""
import hashlib
import json
import math
import random
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

from model import load_vocab, max_len
from train import tokenize_label

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "notes_20261023"
SOURCE = Path(r"C:\Users\Desktop\Documents\xwechat_files\wxid_16ejg0rm8hv222_0f82\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8")
SEED = 20261023
# 坐标基于用户预览尺寸；读取原图后按尺寸比例映射。标签照录原文，不改数学内容。
SOURCES = [
    (Path(r"C:\Users\Desktop\AppData\Local\Temp\codex-clipboard-cbcd38b3-cd67-42fc-8875-0854dbedb145.png"), (600, 220), [
        ((18, 5, 460, 63), r"\text{Assume }Ax=b\text{. Then}"),
        ((20, 63, 491, 123), r"A(x+v)=b\text{ iff }Av=0"),
        ((12, 124, 600, 166), r"\text{Since, }A(x+v)=Ax+Av=b+Av\text{.}"),
        ((535, 170, 581, 212), r"\square"),
    ]),
    (SOURCE / "3e24f6112341e639090e2df0cd5cb14b.jpg", (512, 170), [
        ((7, 45, 474, 145), r"f'(x)=\lim_{n\rightarrow\infty}f_n'(x)"),
    ]),
    (SOURCE / "4970021d29a3f597a5ef53ab4cf4c03a.jpg", (2048, 429), [
        ((55, 52, 722, 182), r"\text{More specifically:}"),
        ((61, 174, 1053, 246), r"\text{Let }A\text{ be a }m\times n\text{ matrix.}"),
        ((65, 246, 982, 337), r"\text{An }n\text{-dimension vector }v\text{ is}"),
        ((1000, 244, 1970, 351), r"\text{turned into an }m\text{-dimensional}"),
        ((66, 341, 451, 416), r"\text{vector }Av"),
    ]),
    (SOURCE / "ad2dffc50cc0d9afeb326f797b3769e7.jpg", (1486, 245), [
        ((13, 19, 1368, 94), r"\text{b). Let }P_{4,a}(x)=f(a)+f'(a)(x-a)+\frac{f''(a)}{4}(x-a)^2+\frac{f'''(a)}{3!}(x-a)^3+\frac{f^{(4)}(a)}{4!}(x-a)^4"),
        ((350, 96, 722, 163), r"=f(a)+\frac{f^{(4)}(a)}{4!}(x-a)^4"),
        ((795, 108, 1326, 178), r"\text{be the Taylor polynomial of }f\text{.}"),
        ((40, 163, 600, 238), r"\text{Then }\lim_{x\rightarrow a}f(x)-P_{4,a}(x)=0"),
    ]),
    (SOURCE / "f965f80643ff4109128003ee0f787abd.jpg", (1792, 338), [
        ((4, 20, 626, 96), r"\text{Theorem 4 (Weierstrass M-Test)}"),
        ((636, 18, 1300, 96), r"\text{Let }\{f_n\}\text{ be a sequence of functions}"),
        ((1306, 20, 1772, 100), r"\text{defined on }A\text{ and suppose}"),
        ((7, 100, 970, 170), r"\text{that }\{M_n\}\text{ is a sequence of numbers such that}"),
        ((94, 168, 683, 236), r"|f_n(x)|<M_n\text{ for all }x\text{ in }A"),
        ((13, 235, 864, 328), r"\text{Suppose moreover that }\sum_{n=1}^{\infty}M_n\text{ converges.}"),
        ((872, 232, 1296, 326), r"\text{Then for each }x\text{ in}"),
    ]),
    (SOURCE / "cb162a8eac1b93099b88b83d49b81b17.jpg", (754, 311), [
        ((102, 4, 514, 106), r"\int_a^x g=\lim_{n\rightarrow\infty}\int_a^x f_n'"),
        ((224, 108, 744, 207), r"=\lim_{n\rightarrow\infty}f_n(x)-f_n(a)\text{.}"),
        ((221, 196, 500, 286), r"=f(x)-f(a)"),
    ]),
    (SOURCE / "f9fe2085a0a2e050cfcf1595c7369184.jpg", (1722, 1188), [
        ((88, 91, 440, 164), r"120\times5=600"),
        ((77, 200, 543, 266), r"1000+1000=2000"),
        ((73, 289, 362, 371), r"2^{2\times3}=64"),
        ((70, 435, 550, 552), r"\frac{5\times6\times7\times8}{2}=840"),
        ((99, 613, 395, 703), r"80\times9=720"),
        ((53, 733, 401, 843), r"\lim_{x\rightarrow\infty}8x+3=\infty"),
        ((51, 855, 432, 990), r"\lim_{x\rightarrow\infty}\frac{1}{8x+3}=0"),
        ((743, 69, 1715, 176), r"\sum_{i=1}^{25}ix_i=x_1+2x_2+...+24x_{24}+25x_{25}"),
        ((742, 267, 1289, 417), r"\sum_{i=1}^{\infty}x_i=\lim_{n\rightarrow\infty}\int_1^n f(x)"),
        ((754, 461, 1070, 528), r"54\times8=432"),
        ((755, 555, 1065, 629), r"77\times5=385"),
    ]),
]


def main():
    vocab = set(load_vocab())
    records = []
    for source, reference_size, samples in SOURCES:
        for box, label in samples:
            tokens = tokenize_label(label)
            assert set(tokens) <= vocab, (label, set(tokens) - vocab)
            assert len(tokens) + 2 <= max_len, label
            records.append(dict(source=source.name, box=box, reference_size=reference_size,
                                latex=label, image=f"notes_20261023_{len(records)+1:02d}.png"))
    order = list(range(len(records)))
    random.Random(SEED).shuffle(order)
    train_ids = set(order[:math.floor(len(records) * 0.7 + 0.5)])
    for i, record in enumerate(records):
        record["split"] = "train" if i in train_ids else "val"

    (OUT / "originals").mkdir(parents=True, exist_ok=True)
    for source, _, _ in SOURCES:
        backup = OUT / "originals" / source.name
        if not backup.exists():
            shutil.copy2(source, backup)
        else:
            assert hashlib.sha256(source.read_bytes()).digest() == hashlib.sha256(backup.read_bytes()).digest()

    for record in records:
        with Image.open(OUT / "originals" / record["source"]) as raw:
            original = ImageOps.exif_transpose(raw).convert("RGB")
        if record["source"].startswith("cb162a8e") and record["box"][1] in (108, 196):
            # 两行紧挨：用行间折线隔开上一行极限下标和下一行 f 的顶部，不擦改目标笔迹。
            seam = [(220, 198), (313, 198), (325, 208), (754, 208)]
            if record["box"][1] == 108:
                polygon = seam + [(754, 311), (220, 311)]
            else:
                polygon = [(220, 0), (754, 0)] + list(reversed(seam))
            ImageDraw.Draw(original).polygon(polygon, fill=original.getpixel((10, 190)))
        rw, rh = record["reference_size"]
        left, top, right, bottom = record["box"]
        crop = original.crop((round(left * original.width / rw), round(top * original.height / rh),
                              round(right * original.width / rw), round(bottom * original.height / rh)))
        # 等比例缩放；原纸张颜色、横线和笔迹保留，不反色、不生成或修补文字。
        scale = min(112 / crop.height, 1008 / crop.width)
        crop = crop.resize((max(1, round(crop.width * scale)), max(1, round(crop.height * scale))), Image.Resampling.LANCZOS)
        width = max(64, math.ceil((crop.width + 16) / 16) * 16)
        canvas = Image.new("RGB", (width, 128), "white")
        canvas.paste(crop, ((width - crop.width) // 2, (128 - crop.height) // 2))
        dest = ROOT / record["split"] / record["image"]
        dest.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(dest)

    for split in ("train", "val"):
        manifest = ROOT / split / "labels.jsonl"
        existing = [json.loads(line) for line in manifest.read_text(encoding="utf-8").splitlines() if line.strip()]
        lookup = {r["image"]: r["latex"] for r in existing}
        added = []
        for record in records:
            if record["split"] != split:
                continue
            if record["image"] in lookup:
                for item in existing:
                    if item["image"] == record["image"]:
                        item["latex"] = record["latex"]
            else:
                added.append({k: record[k] for k in ("image", "latex")})
        manifest.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in existing + added), encoding="utf-8")

    report = {"seed": SEED, "split_unit": "crop", "train": len(train_ids),
              "val": len(records) - len(train_ids), "samples": records}
    (OUT / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # 审阅页直接使用导入后的像素，并列出编号与分组，便于逐条对照标签。
    for page, start in enumerate(range(0, len(records), 12), 1):
        subset = records[start:start+12]
        sheet = Image.new("RGB", (1060, len(subset) * 157), "#dddddd")
        draw = ImageDraw.Draw(sheet)
        for row, record in enumerate(subset):
            y = row * 157
            draw.text((8, y + 2), f"{record['image']} [{record['split']}]", fill="black")
            with Image.open(ROOT / record["split"] / record["image"]) as sample:
                sheet.paste(sample, (8, y + 22))
        sheet.save(OUT / f"preview_{page}.jpg")
    print(json.dumps({"total": len(records), "train": len(train_ids), "val": len(records)-len(train_ids)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
