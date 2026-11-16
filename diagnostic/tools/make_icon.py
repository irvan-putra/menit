#!/usr/bin/env python3
"""Draws the launcher icon with the version from tools/icon-version.txt. Usage: python3 tools/make_icon.py"""
import os, re
from PIL import Image, ImageDraw, ImageFont
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ver = open(os.path.join(root, 'tools', 'icon-version.txt')).read().strip()
assert re.fullmatch(r'\d+\.\d+\.\d+', ver), ver
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
for d, sz in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    out = os.path.join(root, 'app/src/main/res', 'mipmap-' + d); os.makedirs(out, exist_ok=True)
    im = Image.new('RGB', (sz, sz), (30, 36, 48)); dr = ImageDraw.Draw(im)
    dr.rectangle([sz*0.08, sz*0.08, sz*0.92, sz*0.92], outline=(31, 111, 235), width=max(2, sz//24))
    for txt, y, px in [('DIAG', 0.20, 0.24), ('menit', 0.42, 0.18), 'v' + ver and ('v' + ver, 0.68, 0.19)]:
        ft = ImageFont.truetype(FONT, int(sz*px)); w = dr.textlength(txt, font=ft); dr.text(((sz-w)/2, sz*y), txt, fill=(255, 255, 255), font=ft)
    im.save(os.path.join(out, 'ic_launcher.png'))
