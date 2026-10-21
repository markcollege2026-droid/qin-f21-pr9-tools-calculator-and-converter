#!/usr/bin/env python3
"""Renders the redesigned screens as 1:1 mockups from the real tokens.

Every colour, radius, spacing step and type size below is read from
`core/design/src/main/res`, so this is a drawing of the design system applied to the three
core screens (home, calculator, unit converter) - not a screenshot of the running app.
No device or emulator was available in this environment, so the layout was also checked
against the 320 x 427 dp budget of the Qin F21 Pro by hand.

Usage:  python3 tools/render_ui_screens.py [output.png]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_design_sheet as sheet  # noqa: E402  (token parser + vector renderer)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(ROOT, "core/design/src/main/res")

S = 2                     # 2 px per dp, the device itself renders at ~1.5 px per dp
SCREEN_W, SCREEN_H = 320, 427   # dp, the Qin F21 Pro window after the system bars

COLORS = sheet.parse_colors()
DIMENS = sheet.parse_dimens()

FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


def rgb(name, fallback=(255, 255, 255)):
    return COLORS.get(name, fallback)


def dp(value):
    return int(round(value * S))


class Screen:
    """A 320 x 427 dp canvas with the helpers the real screens use."""

    def __init__(self):
        self.image = Image.new("RGB", (dp(SCREEN_W), dp(SCREEN_H)), rgb("bg"))
        self.draw = ImageDraw.Draw(self.image)
        self._fonts = {}

    # -- primitives ---------------------------------------------------------
    def font(self, sp, weight="regular"):
        key = (sp, weight)
        if key not in self._fonts:
            path = {"regular": FONT_REG, "medium": FONT_BOLD, "mono": FONT_MONO}[weight]
            self._fonts[key] = ImageFont.truetype(path, max(8, int(round(sp * S))))
        return self._fonts[key]

    def text(self, x, y, value, sp, color, weight="regular", align="left",
             tracking=0.0, anchor_y="top"):
        font = self.font(sp, weight)
        width = self.draw.textlength(value, font=font)
        if tracking:
            width += tracking * S * max(0, len(value) - 1)
        if align == "right":
            x = x - width / S
        elif align == "center":
            x = x - width / (2 * S)
        if tracking:
            px = x
            for char in value:
                self.draw.text((dp(px), dp(y)), char, font=font, fill=color)
                px += (self.draw.textlength(char, font=font) / S) + tracking
        else:
            self.draw.text((dp(x), dp(y)), value, font=font, fill=color)
        return width / S

    def box(self, x, y, w, h, fill, radius, stroke=None, stroke_w=1):
        self.draw.rounded_rectangle(
            [dp(x), dp(y), dp(x + w), dp(y + h)],
            radius=dp(radius), fill=fill,
            outline=stroke, width=dp(stroke_w) if stroke else 0,
        )

    def line(self, x1, y1, x2, y2, color, width=1):
        self.draw.line([dp(x1), dp(y1), dp(x2), dp(y2)], fill=color, width=max(1, dp(width)))

    def icon(self, name, x, y, size, color, stroke=1.7):
        path = os.path.join(DESIGN, "drawable/%s.xml" % name)
        viewport, strokes, fills = sheet.load_vector(path)
        for subpaths, stroke_w in strokes:
            sheet.render_icon(self.draw, subpaths, (dp(x), dp(y)), dp(size), viewport, color, stroke_w)
        for subpaths in fills:
            sheet.render_icon(self.draw, subpaths, (dp(x), dp(y)), dp(size), viewport, color, stroke)

    # -- components ---------------------------------------------------------
    def app_bar(self, title, subtitle=None, back=True, icon=None):
        height = DIMENS.get("header_height", 52)
        if back:
            self.box(6, 6, 40, 40, None, 12, rgb("divider"), 1)
            self.icon("ic_back", 15, 15, 22, rgb("text_secondary"))
        stack_x = 52
        if subtitle:
            self.text(stack_x, 9, title, 17, rgb("text_primary"), "medium")
            self.text(stack_x, 29, subtitle.upper(), 9, rgb("text_muted"), "medium", tracking=1.2)
        else:
            width = self.draw.textlength(title, font=self.font(17, "medium")) / S
            self.text(stack_x, (height - 17) / 2 - 1, title, 17, rgb("text_primary"), "medium")
        if icon:
            self.icon(icon, SCREEN_W - 32, 16, 20, rgb("text_muted"))
        self.line(0, height, SCREEN_W, height, rgb("divider"))

    def section(self, y, label, count=None):
        self.text(16, y, label.upper(), 9, rgb("text_muted"), "medium", tracking=1.3)
        width = self.draw.textlength(label.upper(), font=self.font(9, "medium")) / S
        width += 1.3 * max(0, len(label) - 1)
        rule_x = 16 + width + 12
        if count is not None:
            self.text(rule_x, y, str(count), 9, rgb("text_secondary"), "medium")
            rule_x += 12
        self.line(rule_x, y + 4, 308, y + 4, rgb("divider"))

    def row(self, y, title, note, icon, height=56):
        self.box(12, y, 296, height, rgb("surface"), 12, rgb("outline"), 1)
        self.box(22, y + (height - 32) / 2, 32, 32, rgb("surface_high"), 8)
        self.icon(icon, 29, y + (height - 32) / 2 + 7, 18, rgb("text_secondary"))
        self.text(64, y + 12, title, 14, rgb("text_primary"))
        self.text(64, y + 30, note, 11, rgb("text_muted"))
        self.icon("ic_back", 286, y + height / 2 - 7, 14, rgb("text_muted"))
        # the chevron is the back arrow rotated 180 degrees
        box = self.image.crop((dp(286), dp(y + height / 2 - 7), dp(300), dp(y + height / 2 + 7)))
        self.image.paste(box.rotate(180), (dp(286), dp(y + height / 2 - 7)))

    def tile(self, x, y, label, icon, accent=False):
        self.box(x, y, 76, 72, rgb("surface"), 16, rgb("outline"), 1)
        self.icon(icon, x + 27, y + 10, 22, rgb("primary") if accent else rgb("text_secondary"))
        lines = label.split("\n")
        for index, line_text in enumerate(lines):
            self.text(x + 38, y + 42 + index * 13, line_text, 12 if accent else 11,
                      rgb("text_primary") if accent else rgb("text_secondary"),
                      "medium" if accent else "regular", align="center")

    def pill(self, x, y, label, width=None, height=32, fill=None, border=None, color=None,
             mono=True, stroke_w=1):
        width = width if width is not None else 22 + self.draw.textlength(
            label, font=self.font(12, "mono" if mono else "regular")) / S
        self.box(x, y, width, height, fill or rgb("surface_alt"), height / 2,
                 border or rgb("outline"), stroke_w)
        text_w = self.draw.textlength(label, font=self.font(12, "mono" if mono else "regular")) / S
        self.text(x + (width - text_w) / 2, y + (height - 12) / 2 - 1, label, 12,
                  color or rgb("text_secondary"), "mono" if mono else "regular")
        return width

    def field(self, x, y, w, h, label, muted=True, radius=12):
        self.box(x, y, w, h, rgb("surface"), radius, rgb("outline"), 1)
        self.text(x + 12, y + (h - 14) / 2 - 1, label, 14,
                  rgb("text_muted") if muted else rgb("text_primary"), "mono")


# --------------------------------------------------------------------------- screens
def render_home():
    s = Screen()
    s.app_bar("Toolbox", "21 tools · offline", back=False)

    s.box(12, 64, 296, 44, rgb("surface"), 22, rgb("outline"), 1)
    s.icon("ic_search", 26, 77, 18, rgb("text_muted"))
    s.text(52, 79, "Search tools", 14, rgb("text_muted"))

    s.section(124, "Quick access")
    strip = [("Calculator", "ic_calculator"), ("Unit\nconverter", "ic_convert"),
             ("Currency", "ic_currency"), ("Stopwatch", "ic_stopwatch")]
    x = 12
    for index, (label, icon) in enumerate(strip):
        s.tile(x, 146, label, icon, accent=index == 0)
        x += 84

    s.section(232, "Convert", 4)
    rows = [
        ("Unit converter", "18 categories, 200+ units", "ic_convert"),
        ("Currency", "Offline reference rates", "ic_currency"),
        ("Time zones", "World clock, DST aware", "ic_timezone"),
        ("Date & time", "Differences and intervals", "ic_calendar"),
    ]
    y = 254
    for title, note, icon in rows:
        if y + 56 > SCREEN_H:
            break
        s.row(y, title, note, icon)
        y += 64
    return s.image


def render_calculator():
    s = Screen()
    s.app_bar("Calculator", icon="ic_calculator")

    # reading card
    s.box(12, 64, 296, 106, rgb("surface_sunken"), 16, rgb("outline"), 1)
    s.text(26, 78, "EXPRESSION", 9, rgb("text_muted"), "medium", tracking=1.4)
    s.text(294, 96, "12 × (3 + 4)", 13, rgb("text_secondary"), "mono", align="right")
    s.text(294, 116, "84", 34, rgb("primary"), "medium", align="right")

    # function chips
    x = 12
    for label in ["sin", "cos", "tan", "ln", "log", "√", "xʸ", "π"]:
        width = s.pill(x, 180, label)
        x += width + 6
        if x > SCREEN_W - 12:
            break

    # keypad, 4 x 4 with the commit key filled
    keys = [["7", "8", "9", "÷"], ["4", "5", "6", "×"], ["1", "2", "3", "−"], ["0", ".", "=", "+"]]
    operators = {"÷", "×", "−", "+"}
    top, key_w, key_h, gap = 224, 72, 46, 4
    for r, row in enumerate(keys):
        for c, label in enumerate(row):
            kx = 8 + c * (key_w + gap)
            ky = top + r * (key_h + gap)
            commit = label == "="
            operator = label in operators
            fill = rgb("primary") if commit else (rgb("primary_dim") if operator else rgb("surface_alt"))
            stroke = rgb("outline") if not (commit or operator) else None
            s.box(kx, ky, key_w, key_h, fill, 12, stroke, 1)
            color = rgb("on_primary") if commit else (rgb("primary") if operator else rgb("text_primary"))
            s.text(kx + key_w / 2, ky + (key_h - 17) / 2 - 1, label, 17, color,
                   "medium" if commit else "mono", align="center")
    return s.image


def render_converter():
    s = Screen()
    s.app_bar("Unit converter")

    # category + favourite
    s.field(12, 64, 244, 44, "Length")
    s.text(160, 79, "▾", 12, rgb("text_muted"))
    s.box(264, 64, 44, 44, rgb("surface"), 12, rgb("outline"), 1)
    s.icon("ic_star", 275, 75, 22, rgb("text_muted"))

    # unit pickers
    s.field(12, 116, 124, 44, "Kilometre")
    s.text(144, 130, "→", 14, rgb("text_muted"))
    s.field(164, 116, 144, 44, "Mile")

    # value + result card
    s.box(12, 168, 296, 130, rgb("surface_sunken"), 16, rgb("outline"), 1)
    s.text(26, 180, "VALUE", 9, rgb("text_muted"), "medium", tracking=1.4)
    s.box(266, 176, 32, 32, rgb("surface_high"), 10)
    s.icon("ic_swap", 273, 183, 18, rgb("primary"))
    s.text(26, 196, "1", 22, rgb("text_primary"), "mono")
    s.line(26, 230, 294, 230, rgb("outline"))
    s.text(26, 240, "RESULT", 9, rgb("primary"), "medium", tracking=1.4)
    s.text(294, 240, "Mile (mi)", 12, rgb("text_secondary"), align="right")
    s.text(26, 256, "0.621371", 28, rgb("primary"), "medium")

    # list header + keypad chip
    s.text(16, 310, "Kilometre in all 18 units", 11, rgb("text_muted"))
    s.pill(240, 304, "Keypad", width=68, height=32, mono=False)

    rows = [("0.621371", "Mile (mi)"), ("1093.61", "Yard (yd)"), ("3280.84", "Foot (ft)"),
            ("1000", "Metre (m)")]
    y = 344
    for value, name in rows:
        if y + 44 > SCREEN_H:
            break
        s.line(12, y, 308, y, rgb("divider"))
        s.text(24, y + 14, value, 15, rgb("text_primary"), "mono")
        s.text(296, y + 16, name, 11, rgb("text_muted"), align="right")
        y += 44
    return s.image


def main():
    output = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs/ui-redesign.png")
    screens = [
        ("Home", "search · quick access · catalogue", render_home()),
        ("Calculator", "reading, chips, 4 × 4 keypad", render_calculator()),
        ("Unit converter", "one reading card · units · overlay keypad", render_converter()),
    ]

    pad, gap, caption = 44, 40, 96
    width = pad * 2 + len(screens) * dp(SCREEN_W) + (len(screens) - 1) * gap
    height = pad + 96 + dp(SCREEN_H) + caption
    canvas = Image.new("RGB", (width, height), rgb("bg"))
    draw = ImageDraw.Draw(canvas)

    title_font = ImageFont.truetype(FONT_BOLD, 30)
    sub_font = ImageFont.truetype(FONT_REG, 15)
    label_font = ImageFont.truetype(FONT_BOLD, 16)
    small_font = ImageFont.truetype(FONT_REG, 13)

    draw.text((pad, 36), "Qin F21 Toolbox - redesigned screens", font=title_font, fill=rgb("text_primary"))
    draw.text((pad, 74),
              "320 x 427 dp, drawn from the tokens in core/design/src/main/res - a mockup of the new UI, not a screenshot",
              font=sub_font, fill=rgb("text_muted"))

    x = pad
    for name, note, image in screens:
        canvas.paste(image, (x, 128))
        draw.rectangle([x, 128, x + dp(SCREEN_W) - 1, 128 + dp(SCREEN_H) - 1],
                       outline=rgb("outline_strong"))
        draw.text((x, 128 + dp(SCREEN_H) + 18), name, font=label_font, fill=rgb("text_primary"))
        draw.text((x, 128 + dp(SCREEN_H) + 42), note, font=small_font, fill=rgb("text_muted"))
        x += dp(SCREEN_W) + gap

    canvas.save(output)
    print("wrote", output, canvas.size)


if __name__ == "__main__":
    main()
