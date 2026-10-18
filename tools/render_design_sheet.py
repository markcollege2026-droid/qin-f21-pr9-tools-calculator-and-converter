#!/usr/bin/env python3
"""Renders a design system sheet from the real resources of the app.

The colours, the type scale and the icons are read from
`core/design/src/main/res`, so the sheet always shows what the app really uses.
It is a rendering of the design system, not a screenshot of the running app.

Usage:  python3 tools/render_design_sheet.py [output.png]
"""
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(ROOT, "core/design/src/main/res")
ANDROID = "{http://schemas.android.com/apk/res/android}"

SCALE = 3  # render at 3x and downscale for crisp output


# --------------------------------------------------------------------------- helpers
def font(size, bold=False, mono=False):
    candidates = []
    if mono:
        candidates = [
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
        ]
    else:
        candidates = [
            "/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf" % ("-Bold" if bold else ""),
            "/usr/share/fonts/truetype/liberation/LiberationSans%s.ttf" % ("-Bold" if bold else "-Regular"),
        ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def parse_colors():
    tree = ET.parse(os.path.join(DESIGN, "values/colors.xml"))
    colors = {}
    for node in tree.getroot():
        name = node.get("name")
        value = (node.text or "").strip()
        if name and value.startswith("#") and len(value) == 9:
            # AARRGGBB in the resource file, drop the alpha byte
            colors[name] = tuple(int(value[i:i + 2], 16) for i in (3, 5, 7))
    return colors


def parse_dimens():
    tree = ET.parse(os.path.join(DESIGN, "values/dimens.xml"))
    out = {}
    for node in tree.getroot():
        name = node.get("name")
        value = (node.text or "").strip()
        if name and value.endswith("dp"):
            out[name] = float(value[:-2])
        elif name and value.endswith("sp"):
            out[name] = float(value[:-2])
    return out


def luminance(color):
    def channel(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (channel(x) for x in color[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    if la < lb:
        la, lb = lb, la
    return (la + 0.05) / (lb + 0.05)


# --------------------------------------------------------------------------- path parsing
TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+(?:e-?\d+)?")


def tokenize(data):
    return TOKEN.findall(data)


def arc_to_beziers(x1, y1, rx, ry, phi_deg, large_arc, sweep, x2, y2):
    """Converts an SVG elliptical arc into a list of cubic bezier segments."""
    if rx == 0 or ry == 0:
        return [("L", x2, y2)]
    phi = math.radians(phi_deg)
    cos_p, sin_p = math.cos(phi), math.sin(phi)
    dx2, dy2 = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cos_p * dx2 + sin_p * dy2
    y1p = -sin_p * dx2 + cos_p * dy2
    rx, ry = abs(rx), abs(ry)
    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1:
        scale = math.sqrt(lam)
        rx *= scale
        ry *= scale
    numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    factor = math.sqrt(max(0.0, numerator / denominator)) if denominator else 0.0
    if large_arc == sweep:
        factor = -factor
    cxp = factor * rx * y1p / ry
    cyp = -factor * ry * x1p / rx
    cx = cos_p * cxp - sin_p * cyp + (x1 + x2) / 2.0
    cy = sin_p * cxp + cos_p * cyp + (y1 + y2) / 2.0

    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        norm = math.hypot(ux, uy) * math.hypot(vx, vy)
        value = math.acos(max(-1.0, min(1.0, dot / norm))) if norm else 0.0
        if ux * vy - uy * vx < 0:
            value = -value
        return value

    theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and delta > 0:
        delta -= 2 * math.pi
    elif sweep and delta < 0:
        delta += 2 * math.pi

    segments = []
    count = max(1, int(math.ceil(abs(delta) / (math.pi / 2))))
    step = delta / count
    alpha = 4.0 / 3.0 * math.tan(step / 4.0)
    theta = theta1
    px, py = x1, y1
    for _ in range(count):
        cos_t, sin_t = math.cos(theta), math.sin(theta)
        theta2 = theta + step
        cos_t2, sin_t2 = math.cos(theta2), math.sin(theta2)

        def point(c, s):
            return (cos_p * rx * c - sin_p * ry * s + cx, sin_p * rx * c + cos_p * ry * s + cy)

        ex, ey = point(cos_t2, sin_t2)
        d1x, d1y = -rx * sin_t, ry * cos_t
        d2x, d2y = -rx * sin_t2, ry * cos_t2
        d1x, d1y = cos_p * d1x - sin_p * d1y, sin_p * d1x + cos_p * d1y
        d2x, d2y = cos_p * d2x - sin_p * d2y, sin_p * d2x + cos_p * d2y
        c1x, c1y = px + alpha * d1x, py + alpha * d1y
        c2x, c2y = ex - alpha * d2x, ey - alpha * d2y
        segments.append(("C", c1x, c1y, c2x, c2y, ex, ey))
        px, py = ex, ey
        theta = theta2
    return segments


def parse_path(data):
    """Returns a list of subpaths, each a list of segments (x, y) points / beziers."""
    tokens = tokenize(data)
    subpaths = []
    current = []
    x = y = 0.0
    start_x = start_y = 0.0
    i = 0
    command = None
    last_control = None

    def num():
        nonlocal i
        value = float(tokens[i])
        i += 1
        return value

    while i < len(tokens):
        token = tokens[i]
        if re.match(r"[A-Za-z]", token):
            command = token
            i += 1
            if command in "Zz":
                if current:
                    current.append(("L", start_x, start_y))
                    subpaths.append(current)
                    current = []
                x, y = start_x, start_y
                continue
        if command is None:
            i += 1
            continue
        relative = command.islower()
        upper = command.upper()

        if upper == "M":
            nx, ny = num(), num()
            if relative:
                nx, ny = x + nx, y + ny
            if current:
                subpaths.append(current)
            current = []
            x, y = nx, ny
            start_x, start_y = x, y
            current.append(("M", x, y))
            command = "L" if relative else "l"  # subsequent pairs are line commands
            command = "L"
        elif upper == "L":
            nx, ny = num(), num()
            if relative:
                nx, ny = x + nx, y + ny
            current.append(("L", nx, ny))
            x, y = nx, ny
        elif upper == "H":
            nx = num()
            if relative:
                nx = x + nx
            current.append(("L", nx, y))
            x = nx
        elif upper == "V":
            ny = num()
            if relative:
                ny = y + ny
            current.append(("L", x, ny))
            y = ny
        elif upper == "C":
            x1, y1, x2, y2, nx, ny = (num() for _ in range(6))
            if relative:
                x1, y1, x2, y2, nx, ny = x + x1, y + y1, x + x2, y + y2, x + nx, y + ny
            current.append(("C", x1, y1, x2, y2, nx, ny))
            last_control = (x2, y2)
            x, y = nx, ny
        elif upper == "S":
            x2, y2, nx, ny = (num() for _ in range(4))
            if relative:
                x2, y2, nx, ny = x + x2, y + y2, x + nx, y + ny
            if last_control:
                x1 = 2 * x - last_control[0]
                y1 = 2 * y - last_control[1]
            else:
                x1, y1 = x, y
            current.append(("C", x1, y1, x2, y2, nx, ny))
            last_control = (x2, y2)
            x, y = nx, ny
        elif upper == "Q":
            qx, qy, nx, ny = (num() for _ in range(4))
            if relative:
                qx, qy, nx, ny = x + qx, y + qy, x + nx, y + ny
            current.append(("C", x + 2.0 / 3 * (qx - x), y + 2.0 / 3 * (qy - y),
                            nx + 2.0 / 3 * (qx - nx), ny + 2.0 / 3 * (qy - ny), nx, ny))
            x, y = nx, ny
        elif upper == "A":
            rx, ry, rot, large, sweep, nx, ny = (num() for _ in range(7))
            if relative:
                nx, ny = x + nx, y + ny
            for segment in arc_to_beziers(x, y, rx, ry, rot, int(large), int(sweep), nx, ny):
                current.append(segment)
            x, y = nx, ny
        else:
            i += 1
    if current:
        subpaths.append(current)
    return subpaths


def bezier_points(p0, p1, p2, p3, steps=16):
    points = []
    for index in range(1, steps + 1):
        t = index / steps
        mt = 1 - t
        px = mt ** 3 * p0[0] + 3 * mt ** 2 * t * p1[0] + 3 * mt * t ** 2 * p2[0] + t ** 3 * p3[0]
        py = mt ** 3 * p0[1] + 3 * mt ** 2 * t * p1[1] + 3 * mt * t ** 2 * p2[1] + t ** 3 * p3[1]
        points.append((px, py))
    return points


def render_icon(draw, subpaths, origin, size, viewport, color, stroke_width):
    scale = size / viewport

    def to_px(px, py):
        return (origin[0] + px * scale, origin[1] + py * scale)

    for subpath in subpaths:
        points = []
        current = (0, 0)
        for segment in subpath:
            if segment[0] == "M":
                current = (segment[1], segment[2])
                points.append(to_px(*current))
            elif segment[0] == "L":
                current = (segment[1], segment[2])
                points.append(to_px(*current))
            elif segment[0] == "C":
                p0 = current
                p1 = (segment[1], segment[2])
                p2 = (segment[3], segment[4])
                p3 = (segment[5], segment[6])
                for point in bezier_points(p0, p1, p2, p3):
                    points.append(to_px(*point))
                current = p3
        if len(points) < 2:
            continue
        width = max(1, int(round(stroke_width * scale)))
        draw.line(points, fill=color, width=width, joint="curve")
        radius = width / 2.0
        for point in (points[0], points[-1]):
            draw.ellipse(
                [point[0] - radius, point[1] - radius, point[0] + radius, point[1] + radius],
                fill=color,
            )


def load_vector(path):
    tree = ET.parse(path)
    root = tree.getroot()
    viewport = float(root.get(ANDROID + "viewportWidth", "24"))
    strokes = []
    fills = []
    for node in root:
        if node.tag != "path":
            continue
        data = node.get(ANDROID + "pathData", "")
        subpaths = parse_path(data)
        stroke = node.get(ANDROID + "strokeColor")
        fill = node.get(ANDROID + "fillColor")
        width = float(node.get(ANDROID + "strokeWidth", "1"))
        if stroke and stroke.lower() != "none":
            strokes.append((subpaths, width))
        elif fill and fill.lower() not in ("none", "#00000000"):
            fills.append(subpaths)
    return viewport, strokes, fills


# --------------------------------------------------------------------------- sheet
def main():
    output = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs/design-system.png")
    colors = parse_colors()
    dimens = parse_dimens()

    bg = colors.get("bg", (11, 15, 20))
    surface = colors.get("surface", (20, 26, 34))
    surface_alt = colors.get("surface_alt", (28, 36, 46))
    outline = colors.get("outline", (42, 52, 65))
    primary = colors.get("primary", (45, 212, 191))
    accent = colors.get("accent", (251, 191, 36))
    text_primary = colors.get("text_primary", (241, 245, 249))
    text_secondary = colors.get("text_secondary", (154, 168, 184))
    text_muted = colors.get("text_muted", (107, 122, 140))

    width, height = 1000, 1700
    image = Image.new("RGB", (width * SCALE, height * SCALE), bg)
    draw = ImageDraw.Draw(image)

    def s(value):
        return int(round(value * SCALE))

    title_font = font(26 * SCALE, bold=True)
    subtitle_font = font(13 * SCALE)
    section_font = font(12 * SCALE, bold=True)
    label_font = font(11 * SCALE)
    small_font = font(10 * SCALE)
    mono_font = font(11 * SCALE, mono=True)

    draw.text((s(40), s(38)), "Qin F21 Toolbox - design system", font=title_font, fill=text_primary)
    draw.text((s(40), s(76)), "rendered from core/design/src/main/res - the colours, the type scale and the icons are the ones the app uses",
              font=subtitle_font, fill=text_muted)

    # ---- palette ------------------------------------------------------------
    draw.text((s(40), s(120)), "COLOUR", font=section_font, fill=primary)
    palette = [
        ("bg", bg), ("surface", surface), ("surface_alt", surface_alt), ("surface_high", colors["surface_high"]),
        ("outline", outline), ("primary", primary), ("primary_dim", colors["primary_dim"]),
        ("accent", accent), ("danger", colors["danger"]), ("success", colors["success"]),
        ("info", colors["info"]), ("text_primary", text_primary), ("text_secondary", text_secondary),
        ("text_muted", text_muted),
    ]
    x = s(40)
    y = s(144)
    for name, color in palette:
        box_w, box_h = s(118), s(52)
        draw.rounded_rectangle([x, y, x + box_w, y + box_h], radius=s(8), fill=color,
                               outline=outline, width=s(1))
        hexcode = "#%02X%02X%02X" % color[:3]
        contrast_bg = contrast(color, bg)
        # label drawn on top of the swatch, so it must contrast with the swatch
        ratio_color = (10, 14, 20) if luminance(color) > 0.35 else (245, 248, 252)
        draw.text((x + s(8), y + s(8)), hexcode, font=mono_font, fill=ratio_color)
        draw.text((x + s(8), y + s(26)), name, font=small_font, fill=ratio_color)
        draw.text((x, y + box_h + s(4)), "%.1f:1 on bg" % contrast_bg, font=small_font, fill=text_muted)
        x += box_w + s(10)
        if x > s(900):
            x = s(40)
            y += s(86)

    # ---- type scale ---------------------------------------------------------
    y += s(52)
    draw.text((s(40), y), "TYPE SCALE", font=section_font, fill=primary)
    y += s(24)
    samples = [
        ("Display", "text_display", "28sp", "1,234.56 m"),
        ("Title", "text_title", "18sp", "Unit converter"),
        ("Subtitle", "text_subtitle", "15sp", "Length - kilometre to mile"),
        ("Body", "text_body", "14sp", "Every conversion is stored on the device"),
        ("Label", "text_label", "12sp", "All units - 24 units"),
        ("Caption", "text_caption", "11sp", "Bundled offline rates, no network"),
        ("Mono", "text_mono", "16sp", "00:42.318"),
    ]
    for name, dim, size, sample in samples:
        value = dimens.get(dim, 14)
        draw.text((s(40), y), name, font=label_font, fill=text_secondary)
        draw.text((s(140), y - s(2)), sample, font=font(int(value * SCALE * 0.9)), fill=text_primary)
        draw.text((s(720), y), "%s  %s" % (size, dim), font=small_font, fill=text_muted)
        y += s(34)

    # ---- icons --------------------------------------------------------------
    y += s(30)
    draw.text((s(40), y), "ICONS (61 vector drawables, 24dp grid)", font=section_font, fill=primary)
    y += s(20)
    drawables = sorted(
        f for f in os.listdir(os.path.join(DESIGN, "drawable"))
        if f.startswith("ic_") and f.endswith(".xml")
    )
    columns = 10
    cell = s(90)
    icon_size = s(40)
    for index, name in enumerate(drawables):
        column = index % columns
        row = index // columns
        cx = s(40) + column * cell
        cy = y + row * s(72)
        draw.rounded_rectangle([cx, cy, cx + s(60), cy + s(60)], radius=s(10), fill=surface, outline=outline, width=s(1))
        path = os.path.join(DESIGN, "drawable", name)
        viewport, strokes, fills = load_vector(path)
        origin = (cx + (s(60) - icon_size) / 2.0, cy + (s(60) - icon_size) / 2.0)
        for subpaths, stroke_w in strokes:
            render_icon(draw, subpaths, origin, icon_size, viewport, primary, stroke_w)
        for subpaths in fills:
            render_icon(draw, subpaths, origin, icon_size, viewport, primary, 1.6)
        draw.text((cx, cy + s(62)), name[3:-4], font=small_font, fill=text_muted)

    rows = (len(drawables) + columns - 1) // columns
    y += rows * s(72) + s(18)

    # ---- components ---------------------------------------------------------
    draw.text((s(40), y), "COMPONENTS", font=section_font, fill=primary)
    y += s(24)

    def row_card(x0, y0, title, subtitle, focused):
        fill = surface_alt if focused else surface
        draw.rounded_rectangle(
            [x0, y0, x0 + s(430), y0 + s(50)], radius=s(10), fill=fill,
            outline=primary if focused else outline, width=s(3 if focused else 1))
        viewport, strokes, fills = load_vector(os.path.join(DESIGN, "drawable/ic_convert.xml"))
        render_icon(draw, [p for p, w in strokes], (x0 + s(12), y0 + s(13)), s(24), viewport,
                    primary, 1.7)
        draw.text((x0 + s(48), y0 + s(9)), title, font=font(13 * SCALE), fill=text_primary)
        draw.text((x0 + s(48), y0 + s(28)), subtitle, font=small_font,
                  fill=text_secondary if not focused else text_primary)
        if focused:
            draw.text((x0 + s(340), y0 + s(31)), "focused", font=small_font, fill=primary)

    row_card(s(40), y, "Unit converter", "Length - kilometre to mile", False)
    row_card(s(490), y, "Unit converter", "Length - kilometre to mile", True)
    draw.text((s(40), y + s(54)), "resting row", font=small_font, fill=text_muted)
    draw.text((s(490), y + s(54)), "D-pad focus: surface_high + 2 dp primary ring", font=small_font, fill=text_muted)
    y += s(78)

    # buttons
    draw.rounded_rectangle([s(40), y, s(170), y + s(38)], radius=s(8), fill=primary)
    draw.text((s(58), y + s(10)), "Calculate", font=font(13 * SCALE, bold=True), fill=colors["on_primary"])
    draw.rounded_rectangle([s(186), y, s(316), y + s(38)], radius=s(8), fill=surface_alt,
                           outline=outline, width=s(1))
    draw.text((s(202), y + s(10)), "Reset", font=font(13 * SCALE), fill=text_primary)
    # focused secondary button
    draw.rounded_rectangle([s(332), y, s(462), y + s(38)], radius=s(8), fill=surface_alt,
                           outline=primary, width=s(3))
    draw.text((s(348), y + s(10)), "Swap", font=font(13 * SCALE), fill=text_primary)
    draw.text((s(490), y + s(12)), "primary / secondary / focused secondary", font=small_font, fill=text_muted)
    y += s(54)

    # result and unavailable cards
    draw.rounded_rectangle([s(40), y, s(460), y + s(56)], radius=s(10),
                           fill=colors["primary_dim"], outline=primary, width=s(1))
    draw.text((s(56), y + s(10)), "0.621371 mi", font=font(20 * SCALE, bold=True), fill=primary)
    draw.text((s(56), y + s(38)), "result card", font=small_font, fill=text_secondary)
    draw.rounded_rectangle([s(490), y, s(910), y + s(56)], radius=s(10),
                           fill=surface_alt, outline=colors["outline_strong"], width=s(1))
    draw.text((s(506), y + s(10)), "No infrared emitter", font=font(13 * SCALE, bold=True), fill=text_primary)
    draw.text((s(506), y + s(32)), "runtime detection failed - the code list stays readable",
              font=small_font, fill=text_secondary)
    y += s(72)

    # keypad - the exact grids the app builds, 4 columns, keys measured by the view
    draw.text((s(40), y), "KEYPAD - converter (120 dp tall, 4 x 4)", font=section_font, fill=primary)
    draw.text((s(560), y), "calculator (4 x 5)", font=section_font, fill=primary)
    y += s(20)
    converter_keys = [["7", "8", "9", "\u232b"], ["4", "5", "6", "C"],
                      ["1", "2", "3", "\u2212"], ["0", "00", ".", "\u00b1"]]
    calculator_keys = [["7", "8", "9", "\u00f7"], ["4", "5", "6", "\u00d7"],
                       ["1", "2", "3", "\u2212"], ["0", ".", "=", "+"],
                       ["(", ")", "%", "\u232b"]]

    def keypad(x0, y0, grid, focused_key, key_w, key_h, gap):
        for row_i, row in enumerate(grid):
            for column, label in enumerate(row):
                kx = x0 + column * (key_w + gap)
                ky = y0 + row_i * (key_h + gap)
                focused = label == focused_key
                draw.rounded_rectangle(
                    [kx, ky, kx + key_w, ky + key_h], radius=s(6),
                    fill=surface_alt if focused else surface,
                    outline=primary if focused else outline,
                    width=s(2 if focused else 1))
                glyph_font = font(14 * SCALE, mono=True)
                text_width = draw.textlength(label, font=glyph_font)
                draw.text((kx + (key_w - text_width) / 2, ky + s((key_h - s(18)) // 2) + s(2)),
                          label, font=glyph_font, fill=text_primary)

    keypad(s(40), y, converter_keys, "5", s(58), s(27), s(5))
    keypad(s(560), y, calculator_keys, "=", s(58), s(22), s(4))

    note_x = s(316)
    draw.text((note_x, y + s(2)), "Keys are laid out by KeypadView, not by the", font=small_font, fill=text_secondary)
    draw.text((note_x, y + s(18)), "platform: the container measures every", font=small_font, fill=text_secondary)
    draw.text((note_x, y + s(34)), "cell and the D-pad moves with the grid.", font=small_font, fill=text_secondary)
    draw.text((note_x, y + s(58)), "The key with the focus ring is the current", font=small_font, fill=text_muted)
    draw.text((note_x, y + s(74)), "D-pad position; Enter presses it.", font=small_font, fill=text_muted)

    y += 5 * (s(22) + s(4)) + s(28)

    draw.text((s(40), y), "spacing 4 / 8 / 12 / 16 / 24 dp     touch target 44 dp     key 42 dp",
              font=small_font, fill=text_muted)

    image = image.resize((int(width), int(height)), Image.LANCZOS)
    image.save(output)
    print("wrote", output, image.size)


if __name__ == "__main__":
    main()
