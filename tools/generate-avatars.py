"""Generate the five initial fictional portraits; requires Pillow (development only)."""
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'backend/var/avatars'
NAMES = ['陈启明', '梁悦', '林晓', '赵诚', '周桂兰']


def portrait(index):
    image = Image.new('RGB', (512, 512), ['#dce5df', '#e6ded4', '#e5ebd5', '#dce3eb', '#eee3da'][index])
    d = ImageDraw.Draw(image)
    skin, hair = '#e3b798', '#353735' if index != 4 else '#a7aaa5'
    shirt = ['#283f48', '#818e77', '#b4bb82', '#70889d', '#9f8791'][index]
    d.ellipse((40, 50, 472, 482), fill='#f6f3e9')
    if index == 2:
        d.ellipse((315, 135, 404, 340), fill=hair)
    if index == 1:
        d.rounded_rectangle((145, 105, 367, 347), radius=95, fill=hair)
    d.rounded_rectangle((94, 333, 418, 560), radius=110, fill=shirt)
    d.rectangle((229, 280, 283, 354), fill=skin)
    d.ellipse((165, 115, 347, 316), fill=skin)
    d.pieslice((158, 93, 355, 247), 180, 360, fill=hair)
    d.polygon([(165, 189), (172, 133), (316, 118), (347, 183), (309, 158), (208, 161)], fill=hair)
    for x in [214, 291]:
        d.line((x - 12, 207, x + 10, 205), fill=hair, width=4)
        d.ellipse((x - 4, 220, x + 4, 227), fill='#373c36')
    d.line((254, 230, 248, 251, 258, 251), fill='#bb8a71', width=3)
    d.arc((231, 249, 280, 278), 15, 160, fill='#906652', width=3)
    d.polygon([(220, 335), (256, 365), (231, 399), (200, 350)], fill='#e4e7dc')
    d.polygon([(290, 335), (256, 365), (279, 399), (312, 350)], fill='#e4e7dc')
    if index == 1:
        d.arc((148, 101, 364, 296), 180, 355, fill='#293c39', width=9)
        d.rounded_rectangle((335, 200, 356, 251), radius=8, fill='#293c39')
        d.line((347, 247, 330, 274, 286, 274), fill='#293c39', width=5)
    if index == 4:
        for x in [187, 266]:
            d.rounded_rectangle((x, 210, x + 60, 246), radius=12, outline='#555d58', width=4)
        d.line((247, 222, 266, 222), fill='#555d58', width=4)
    out = BytesIO()
    image.save(out, format='PNG')
    return out.getvalue()


def generate():
    ROOT.mkdir(parents=True, exist_ok=True)
    keys = []
    for index, name in enumerate(NAMES):
        data = portrait(index)
        filename = sha256(data).hexdigest() + '.png'
        path = ROOT / filename
        if path.exists():
            assert path.read_bytes() == data, 'Immutable avatar content differs'
        else:
            path.write_bytes(data)
        with Image.open(BytesIO(data)) as decoded:
            decoded.verify()
        keys.append('avatars/' + filename)
        print(name, keys[-1])
    return keys


if __name__ == '__main__':
    generate()
