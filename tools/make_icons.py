"""生成各密度启动图标（纯标准库，无 Pillow 依赖）。

用法： python tools/make_icons.py
输出： app/src/main/res/mipmap-*/ic_launcher.png
"""

import os
import struct
import zlib

BG = (212, 83, 126)
FG = (255, 255, 255)
SS = 4  # 超采样倍数

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def in_rrect(px, py, x0, y0, x1, y1, r):
    if px < x0 or px > x1 or py < y0 or py > y1:
        return False
    cx = min(max(px, x0 + r), x1 - r)
    cy = min(max(py, y0 + r), y1 - r)
    return (px - cx) ** 2 + (py - cy) ** 2 <= r * r


# 归一化坐标下的一层层图形，后画的覆盖先画的
LAYERS = [
    ("rrect", 0.02, 0.02, 0.98, 0.98, 0.22, BG),
    ("rrect", 0.24, 0.20, 0.40, 0.34, 0.04, FG),
    ("rrect", 0.60, 0.20, 0.76, 0.34, 0.04, FG),
    ("rrect", 0.22, 0.28, 0.78, 0.82, 0.08, FG),
    ("rrect", 0.30, 0.40, 0.70, 0.46, 0.02, BG),
    ("rrect", 0.31, 0.54, 0.41, 0.64, 0.02, BG),
    ("rrect", 0.45, 0.54, 0.55, 0.64, 0.02, BG),
    ("rrect", 0.59, 0.54, 0.69, 0.64, 0.02, BG),
    ("rrect", 0.31, 0.69, 0.41, 0.79, 0.02, BG),
    ("rrect", 0.45, 0.69, 0.55, 0.79, 0.02, BG),
]


def sample(px, py):
    """返回该点的 (r, g, b, a)，从最上层往下找第一个命中的图层"""
    for _, x0, y0, x1, y1, r, color in reversed(LAYERS):
        if in_rrect(px, py, x0, y0, x1, y1, r):
            return color[0], color[1], color[2], 255
    return 0, 0, 0, 0


def render(size):
    out = bytearray(size * size * 4)
    step = 1.0 / (size * SS)
    for y in range(size):
        for x in range(size):
            rs = gs = bs = as_ = 0.0
            for sy in range(SS):
                for sx in range(SS):
                    px = (x * SS + sx + 0.5) * step
                    py = (y * SS + sy + 0.5) * step
                    r, g, b, a = sample(px, py)
                    if a:
                        rs += r
                        gs += g
                        bs += b
                        as_ += 1
            n = SS * SS
            if as_ == 0:
                continue
            idx = (y * size + x) * 4
            out[idx] = int(rs / as_)
            out[idx + 1] = int(gs / as_)
            out[idx + 2] = int(bs / as_)
            out[idx + 3] = int(255 * as_ / n)
    return bytes(out)


def write_png(path, size, pixels):
    raw = b"".join(b"\x00" + pixels[y * size * 4:(y + 1) * size * 4] for y in range(size))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(blob)


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    for density, size in DENSITIES.items():
        folder = os.path.join(root, "app", "src", "main", "res", "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        path = os.path.join(folder, "ic_launcher.png")
        write_png(path, size, render(size))
        print("wrote", os.path.relpath(path, root), size)


if __name__ == "__main__":
    main()
