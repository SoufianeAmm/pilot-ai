"""Minimal UML renderer (class, sequence, use-case, package diagrams) for ReportLab.

Diagrams are laid out in "diagram coordinates" (x to the right, y downwards, points)
and are ReportLab Flowables, so they can be placed in a platypus document and are
scaled down automatically to fit the page. Everything is vector output.
"""
import math

from reportlab.lib.colors import HexColor, white
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Flowable

FONT, BOLD, ITALIC, MONO, MONO_BOLD = "Arial", "Arial-Bold", "Arial-Italic", "Consolas", "Consolas-Bold"


def register_fonts(font_dir=r"C:\Windows\Fonts"):
    for name, file in [(FONT, "arial.ttf"), (BOLD, "arialbd.ttf"), (ITALIC, "ariali.ttf"),
                       ("Arial-BoldItalic", "arialbi.ttf"), (MONO, "consola.ttf"), (MONO_BOLD, "consolab.ttf")]:
        pdfmetrics.registerFont(TTFont(name, f"{font_dir}\\{file}"))
    pdfmetrics.registerFontFamily(FONT, normal=FONT, bold=BOLD, italic=ITALIC, boldItalic="Arial-BoldItalic")


TEXT = HexColor("#1F2937")
MUTED = HexColor("#6B7280")
LINE = HexColor("#374151")

TONES = {
    "mine":     (HexColor("#E8F0FE"), HexColor("#2F5FC4")),   # Soufiane's modules
    "friend":   (HexColor("#FFF1E0"), HexColor("#C46A12")),   # other teams' modules
    "shared":   (HexColor("#E9F5EC"), HexColor("#3E7D4F")),   # shared kernel
    "external": (HexColor("#F3EEFA"), HexColor("#6D4BB0")),   # external systems / frameworks
    "neutral":  (HexColor("#FFFFFF"), HexColor("#4B5563")),
    "note":     (HexColor("#FFFBEA"), HexColor("#B59B2B")),
}


def tw(text, font, size):
    return pdfmetrics.stringWidth(text, font, size)


# --------------------------------------------------------------------------- drawing helpers
class Pen:
    """Thin wrapper over a canvas that takes diagram coordinates (y grows downwards)."""

    def __init__(self, canvas):
        self.c = canvas

    def text(self, x, y, s, font=FONT, size=8, color=TEXT, anchor="left"):
        c = self.c
        c.setFillColor(color)
        c.setFont(font, size)
        if anchor == "center":
            c.drawCentredString(x, -y, s)
        elif anchor == "right":
            c.drawRightString(x, -y, s)
        else:
            c.drawString(x, -y, s)

    def rect(self, x, y, w, h, fill=None, stroke=LINE, width=0.8, dash=None, radius=0):
        c = self.c
        c.setLineWidth(width)
        c.setDash(*dash) if dash else c.setDash()
        if stroke is not None:
            c.setStrokeColor(stroke)
        if fill is not None:
            c.setFillColor(fill)
        if radius:
            c.roundRect(x, -y - h, w, h, radius, stroke=1 if stroke is not None else 0, fill=1 if fill is not None else 0)
        else:
            c.rect(x, -y - h, w, h, stroke=1 if stroke is not None else 0, fill=1 if fill is not None else 0)
        c.setDash()

    def poly(self, pts, color=LINE, width=0.8, dash=None):
        c = self.c
        c.setStrokeColor(color)
        c.setLineWidth(width)
        c.setDash(*dash) if dash else c.setDash()
        p = c.beginPath()
        p.moveTo(pts[0][0], -pts[0][1])
        for x, y in pts[1:]:
            p.lineTo(x, -y)
        c.drawPath(p, stroke=1, fill=0)
        c.setDash()

    def shape(self, pts, fill, stroke=LINE, width=0.8):
        c = self.c
        c.setStrokeColor(stroke)
        c.setFillColor(fill)
        c.setLineWidth(width)
        c.setDash()
        p = c.beginPath()
        p.moveTo(pts[0][0], -pts[0][1])
        for x, y in pts[1:]:
            p.lineTo(x, -y)
        p.close()
        c.drawPath(p, stroke=1, fill=1)

    def ellipse(self, cx, cy, rx, ry, fill, stroke, width=0.9):
        c = self.c
        c.setStrokeColor(stroke)
        c.setFillColor(fill)
        c.setLineWidth(width)
        c.setDash()
        c.ellipse(cx - rx, -cy - ry, cx + rx, -cy + ry, stroke=1, fill=1)

    def circle(self, cx, cy, r, fill=white, stroke=LINE, width=0.9):
        self.ellipse(cx, cy, r, r, fill, stroke, width)

    def label(self, x, y, s, font=ITALIC, size=7.5, color=TEXT, anchor="center", bg=white):
        w = tw(s, font, size)
        left = x - w / 2 if anchor == "center" else (x - w if anchor == "right" else x)
        if bg is not None:
            self.rect(left - 2, y - size + 0.5, w + 4, size + 3, fill=bg, stroke=None)
        self.text(x, y, s, font, size, color, anchor)

    # arrow heads: tip at (x2, y2), direction from (x1, y1)
    def head(self, x1, y1, x2, y2, kind, color=LINE):
        ang = math.atan2(y2 - y1, x2 - x1)
        size, spread = (9, 0.42) if kind in ("triangle", "filled") else (8, 0.45)

        def pt(a, d):
            return x2 - d * math.cos(ang + a), y2 - d * math.sin(ang + a)

        if kind == "open":
            self.poly([pt(spread, size), (x2, y2), pt(-spread, size)], color=color)
        elif kind == "filled":
            self.shape([pt(spread, size), (x2, y2), pt(-spread, size)], fill=color, stroke=color)
        elif kind == "triangle":
            self.shape([pt(spread, size + 1), (x2, y2), pt(-spread, size + 1)], fill=white, stroke=color)
        elif kind in ("diamond", "hdiamond"):
            d = 6
            back = (x2 - 2 * d * math.cos(ang), y2 - 2 * d * math.sin(ang))
            side1 = (x2 - d * math.cos(ang) + 4 * math.sin(ang), y2 - d * math.sin(ang) - 4 * math.cos(ang))
            side2 = (x2 - d * math.cos(ang) - 4 * math.sin(ang), y2 - d * math.sin(ang) + 4 * math.cos(ang))
            self.shape([(x2, y2), side1, back, side2], fill=color if kind == "diamond" else white, stroke=color)


def wrap_words(text, font, size, max_width):
    lines, current = [], ""
    for word in text.split():
        trial = f"{current} {word}".strip()
        if tw(trial, font, size) <= max_width or not current:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


# --------------------------------------------------------------------------- base flowable
class Diagram(Flowable):
    max_scale = 1.0

    def __init__(self):
        super().__init__()
        self._laid_out = False
        self.scale = 1.0

    # subclasses implement layout() -> (minx, miny, maxx, maxy) and render(pen)
    def _ensure_layout(self):
        if not self._laid_out:
            self.bounds = self.layout()
            self._laid_out = True

    def wrap(self, avail_width, avail_height):
        self._ensure_layout()
        minx, miny, maxx, maxy = self.bounds
        w, h = maxx - minx, maxy - miny
        self.scale = min(avail_width / w, avail_height / h, self.max_scale)
        self.width, self.height = w * self.scale, h * self.scale
        return self.width, self.height

    def draw(self):
        minx, miny, _, _ = self.bounds
        c = self.canv
        c.saveState()
        c.translate(0, self.height)
        c.scale(self.scale, self.scale)
        c.translate(-minx, miny)
        self.render(Pen(c))
        c.restoreState()


def legend(pen, x, y, items):
    """items: list of (tone, text). Draws a horizontal legend starting at (x, y)."""
    for tone, text in items:
        fill, stroke = TONES[tone]
        pen.rect(x, y - 8, 14, 10, fill=fill, stroke=stroke)
        pen.text(x + 19, y, text, FONT, 8, TEXT)
        x += 19 + tw(text, FONT, 8) + 18
    return x


# --------------------------------------------------------------------------- class / package diagrams
class Box:
    LINE_H = 10.5
    PAD = 6

    def __init__(self, id, name, x, y, kind="class", stereotype=None, attrs=(), methods=(),
                 tone="mine", width=None, member_font=MONO, member_size=7.6):
        self.id, self.name, self.x, self.y = id, name, x, y
        self.kind, self.attrs, self.methods, self.tone = kind, list(attrs), list(methods), tone
        self.member_font, self.member_size = member_font, member_size
        default_stereo = {"interface": "interface", "enum": "enumeration", "record": "record",
                          "entity": "entity", "package": None}.get(kind)
        self.stereotype = stereotype if stereotype is not None else default_stereo
        self.fixed_width = width
        self._measure()

    def _measure(self):
        widths = [tw(self.name, BOLD, 9.5) + 2 * self.PAD + (16 if self.kind == "package" else 0)]
        if self.stereotype:
            widths.append(tw(f"«{self.stereotype}»", ITALIC, 7.5) + 2 * self.PAD)
        for m in self.attrs + self.methods:
            widths.append(tw(m, self.member_font, self.member_size) + 2 * self.PAD)
        self.w = self.fixed_width or max(max(widths), 90)
        if self.kind == "package":
            self.header_h = 0
            self.h = 22 + len(self.attrs) * self.LINE_H + 8
            return
        self.header_h = (9 if self.stereotype else 0) + 17
        h = self.header_h
        if self.attrs or self.kind != "interface":
            h += max(len(self.attrs), 0) * self.LINE_H + (6 if self.attrs else 4)
        if self.methods:
            h += len(self.methods) * self.LINE_H + 6
        self.h = h

    @property
    def center(self):
        return self.x + self.w / 2, self.y + self.h / 2

    def anchor(self, side, offset=0.5):
        if side == "l":
            return self.x, self.y + self.h * offset
        if side == "r":
            return self.x + self.w, self.y + self.h * offset
        if side == "t":
            return self.x + self.w * offset, self.y
        return self.x + self.w * offset, self.y + self.h  # "b"

    def clip(self, toward):
        cx, cy = self.center
        dx, dy = toward[0] - cx, toward[1] - cy
        if dx == 0 and dy == 0:
            return cx, cy
        tx = (self.w / 2) / abs(dx) if dx else float("inf")
        ty = (self.h / 2) / abs(dy) if dy else float("inf")
        t = min(tx, ty)
        return cx + dx * t, cy + dy * t

    def render(self, pen):
        fill, stroke = TONES[self.tone]
        if self.kind == "package":
            tab_w = tw(self.name, BOLD, 9) + 16
            pen.rect(self.x, self.y, tab_w, 14, fill=fill, stroke=stroke, width=1)
            pen.rect(self.x, self.y + 14, self.w, self.h - 14, fill=fill, stroke=stroke, width=1)
            pen.text(self.x + 8, self.y + 10.5, self.name, BOLD, 9, TEXT)
            yy = self.y + 14 + 14
            for line in self.attrs:
                pen.text(self.x + self.PAD, yy, line, self.member_font, self.member_size, TEXT)
                yy += self.LINE_H
            return
        pen.rect(self.x, self.y, self.w, self.h, fill=fill, stroke=stroke, width=1)
        yy = self.y + 4
        if self.stereotype:
            yy += 8
            pen.text(self.x + self.w / 2, yy, f"«{self.stereotype}»", ITALIC, 7.5, MUTED, "center")
        yy += 12
        pen.text(self.x + self.w / 2, yy, self.name, BOLD, 9.5, TEXT, "center")
        yy = self.y + self.header_h
        compartments = []
        if self.attrs or self.kind != "interface":
            compartments.append(self.attrs)
        if self.methods:
            compartments.append(self.methods)
        for members in compartments:
            pen.poly([(self.x, yy), (self.x + self.w, yy)], color=stroke, width=0.7)
            yy += 3
            for m in members:
                yy += self.LINE_H
                font = self.member_font
                if m.startswith("@Tool"):
                    font = MONO_BOLD if self.member_font == MONO else self.member_font
                pen.text(self.x + self.PAD, yy - 2.5, m, font, self.member_size, TEXT)
            yy += 3 if members else 1


class Note:
    def __init__(self, x, y, text, width=200, attach_to=None, size=7.6):
        self.x, self.y, self.width, self.attach_to, self.size = x, y, width, attach_to, size
        self.lines = []
        for para in text.split("\n"):
            self.lines += wrap_words(para, FONT, size, width - 12) or [""]
        self.h = len(self.lines) * (size + 2.5) + 10

    def render(self, pen):
        fill, stroke = TONES["note"]
        x, y, w, h = self.x, self.y, self.width, self.h
        pen.shape([(x, y), (x + w - 8, y), (x + w, y + 8), (x + w, y + h), (x, y + h)], fill=fill, stroke=stroke)
        pen.poly([(x + w - 8, y), (x + w - 8, y + 8), (x + w, y + 8)], color=stroke)
        yy = y + 5
        for line in self.lines:
            yy += self.size + 2.5
            pen.text(x + 6, yy - 1.5, line, FONT, self.size, TEXT)


class ClassDiagram(Diagram):
    def __init__(self, legend_items=None):
        super().__init__()
        self.boxes, self.rels, self.groups, self.notes, self.texts = {}, [], [], [], []
        self.legend_items = legend_items

    def box(self, *args, **kwargs):
        b = Box(*args, **kwargs)
        self.boxes[b.id] = b
        return b

    def group(self, x, y, w, h, title, tone="neutral"):
        self.groups.append((x, y, w, h, title, tone))

    def note(self, x, y, text, width=200, attach_to=None):
        self.notes.append(Note(x, y, text, width, attach_to))

    def caption(self, x, y, text, size=8, font=ITALIC):
        self.texts.append((x, y, text, size, font))

    def rel(self, a, b, kind="assoc", label=None, src=None, dst=None, via=(), a_label=None, b_label=None,
            label_pos=0.5):
        self.rels.append(dict(a=a, b=b, kind=kind, label=label, src=src, dst=dst, via=list(via),
                              a_label=a_label, b_label=b_label, label_pos=label_pos))

    def layout(self):
        xs, ys = [], []
        for b in self.boxes.values():
            xs += [b.x, b.x + b.w]
            ys += [b.y, b.y + b.h]
        for x, y, w, h, *_ in self.groups:
            xs += [x, x + w]
            ys += [y, y + h]
        for n in self.notes:
            xs += [n.x, n.x + n.width]
            ys += [n.y, n.y + n.h]
        for r in self.rels:
            for x, y in r["via"]:
                xs.append(x)
                ys.append(y)
        legend_h = 22 if self.legend_items else 0
        self.legend_y = max(ys) + 18
        return min(xs) - 8, min(ys) - 8, max(xs) + 8, max(ys) + 8 + legend_h

    def _endpoints(self, r):
        a, b = self.boxes[r["a"]], self.boxes[r["b"]]
        via = r["via"]
        if r["src"]:
            side, off = (r["src"], 0.5) if isinstance(r["src"], str) else r["src"]
            p1 = a.anchor(side, off)
        else:
            p1 = a.clip(via[0] if via else b.center)
        if r["dst"]:
            side, off = (r["dst"], 0.5) if isinstance(r["dst"], str) else r["dst"]
            p2 = b.anchor(side, off)
        else:
            p2 = b.clip(via[-1] if via else a.center)
        return [p1] + via + [p2]

    def render(self, pen):
        for x, y, w, h, title, tone in self.groups:
            fill, stroke = TONES[tone]
            pen.rect(x, y, w, h, fill=None, stroke=stroke, width=0.9, dash=(4, 3), radius=6)
            pen.label(x + 10, y + 3.5, f" {title} ", BOLD, 8.5, stroke, "left", bg=white)
        for b in self.boxes.values():
            b.render(pen)
        for n in self.notes:
            n.render(pen)
            if n.attach_to:
                target = self.boxes[n.attach_to]
                start = (n.x + n.width / 2, n.y + n.h / 2)
                end = target.clip(start)
                edge = Box("_", "", n.x, n.y, width=n.width)
                edge.h = n.h
                pen.poly([edge.clip(end), end], color=TONES["note"][1], dash=(2, 2))
        for r in self.rels:
            self._render_rel(pen, r)
        for x, y, text, size, font in self.texts:
            pen.text(x, y, text, font, size, MUTED)
        if self.legend_items:
            legend(pen, self.bounds[0] + 8, self.legend_y, self.legend_items)

    def _render_rel(self, pen, r):
        pts = self._endpoints(r)
        kind = r["kind"]
        dash = (4, 3) if kind in ("dep", "realize", "uses") else None
        pen.poly(pts, dash=dash)
        (x1, y1), (x2, y2) = pts[-2], pts[-1]
        head = {"assoc": "open", "dep": "open", "uses": "open", "realize": "triangle", "inherit": "triangle"}.get(kind)
        if head:
            pen.head(x1, y1, x2, y2, head)
        if kind in ("compose", "aggregate"):
            (sx, sy), (nx, ny) = pts[0], pts[1]
            pen.head(nx, ny, sx, sy, "diamond" if kind == "compose" else "hdiamond")
            pen.head(x1, y1, x2, y2, "open")
        if r["label"]:
            # put label on the longest segment
            segs = list(zip(pts, pts[1:]))
            (ax, ay), (bx, by) = max(segs, key=lambda s: math.dist(s[0], s[1]))
            t = r["label_pos"]
            pen.label(ax + (bx - ax) * t, ay + (by - ay) * t + 3, r["label"])
        for lab, (p, q) in ((r["a_label"], (pts[0], pts[1])), (r["b_label"], (pts[-1], pts[-2]))):
            if lab:
                ang = math.atan2(q[1] - p[1], q[0] - p[0])
                lx = p[0] + 14 * math.cos(ang) + 8 * math.sin(ang)
                ly = p[1] + 14 * math.sin(ang) - 8 * math.cos(ang) + 3
                pen.label(lx, ly, lab, FONT, 7.5, bg=None)


# --------------------------------------------------------------------------- sequence diagrams
class SequenceDiagram(Diagram):
    HEADER_H = 34
    ROW = 21

    def __init__(self, participants, autonumber=True):
        """participants: list of (id, label, kind, tone); kind in box|actor|db|external."""
        super().__init__()
        self.parts = [dict(id=p[0], label=p[1], kind=p[2] if len(p) > 2 else "box",
                           tone=p[3] if len(p) > 3 else "mine") for p in participants]
        self.index = {p["id"]: i for i, p in enumerate(self.parts)}
        self.events = []
        self.autonumber = autonumber

    # ---- building API
    def msg(self, a, b, text, kind="call"):
        self.events.append(("msg", a, b, text, kind))

    def ret(self, a, b, text=""):
        self.events.append(("msg", a, b, text, "return"))

    def self_msg(self, a, text):
        self.events.append(("self", a, text))

    def begin(self, kind, guard=""):
        self.events.append(("begin", kind, guard))

    def else_(self, guard=""):
        self.events.append(("else", guard))

    def end(self):
        self.events.append(("end",))

    def note(self, a, text, b=None, width=None):
        self.events.append(("note", a, b, text, width))

    def divider(self, text):
        self.events.append(("divider", text))

    # ---- layout
    def _label_w(self, p):
        return max(tw(line, BOLD, 8.5) for line in p["label"].split("\n"))

    def _msg_text(self, n, text):
        return f"{n}. {text}" if self.autonumber and text else text

    def layout(self):
        n = len(self.parts)
        widths = [max(self._label_w(p) + 18, 64) for p in self.parts]
        x = [0.0] * n
        x[0] = widths[0] / 2
        for i in range(1, n):
            x[i] = x[i - 1] + (widths[i - 1] + widths[i]) / 2 + 16

        def require(i, j, need):
            if i > j:
                i, j = j, i
            deficit = need - (x[j] - x[i])
            if deficit > 0:
                for k in range(j, n):
                    x[k] += deficit

        counter = 0
        right_extra = 0
        for e in self.events:
            if e[0] == "msg":
                counter += 1
                need = tw(self._msg_text(counter, e[3]), FONT, 7.8) + 26
                i, j = self.index[e[1]], self.index[e[2]]
                if i != j:
                    require(i, j, need)
            elif e[0] == "self":
                counter += 1
                # self-call labels may run across neighbouring lifelines (drawn on a white background),
                # so they only reserve a little room instead of pushing participants apart
                need = tw(self._msg_text(counter, e[2]), FONT, 7.8) + 40
                i = self.index[e[1]]
                if i + 1 < n:
                    require(i, i + 1, min(need, 95))
                else:
                    right_extra = max(right_extra, need)
            elif e[0] == "note" and e[2] is None and e[4]:
                pass
        self.x = x
        self.widths = widths

        # vertical walk: compute y for every event, frame geometry
        y = self.HEADER_H + 16
        self.placed = []
        stack = []
        frames = []
        counter = 0
        for e in self.events:
            kind = e[0]
            if kind == "msg":
                counter += 1
                y += self.ROW
                self.placed.append(("msg", y, e, counter))
                for f in stack:
                    f["parts"].update({self.index[e[1]], self.index[e[2]]})
            elif kind == "self":
                counter += 1
                y += self.ROW
                self.placed.append(("self", y, e, counter))
                text_w = tw(self._msg_text(counter, e[2]), FONT, 7.8)
                for f in stack:
                    f["parts"].add(self.index[e[1]])
                    f["right"] = max(f["right"], x[self.index[e[1]]] + 34 + text_w)
                y += 12
            elif kind == "begin":
                y += 24
                f = dict(kind=e[1], guard=e[2], top=y - 18, elses=[], parts=set(), right=-1e9, depth=len(stack))
                stack.append(f)
                frames.append(f)
            elif kind == "else":
                y += 20
                stack[-1]["elses"].append((y - 13, e[1]))
            elif kind == "end":
                f = stack.pop()
                y += 9
                f["bottom"] = y
                if stack:
                    stack[-1]["parts"].update(f["parts"])
                    stack[-1]["right"] = max(stack[-1]["right"], f["right"])
            elif kind == "note":
                a, b, text, width = e[1], e[2], e[3], e[4]
                ia = self.index[a]
                ib = self.index[b] if b else ia
                lo, hi = min(ia, ib), max(ia, ib)
                w = width or (x[hi] - x[lo] + 120 if b else 170)
                cx = (x[lo] + x[hi]) / 2
                note = Note(cx - w / 2, y + 8, text, w)
                self.placed.append(("note", y + 8, note, None))
                y += note.h + 10
                for f in stack:
                    f["parts"].update({lo, hi})
            elif kind == "divider":
                y += 24
                self.placed.append(("divider", y - 6, e, None))
        self.frames = frames
        self.bottom = y + 18
        self.max_depth = max([f["depth"] for f in frames], default=-1)
        left = x[0] - widths[0] / 2
        right = max(x[-1] + widths[-1] / 2, x[-1] + right_extra)
        for f in frames:
            pad = 34 - 7 * f["depth"]
            lo, hi = (min(f["parts"]), max(f["parts"])) if f["parts"] else (0, n - 1)
            f["x1"] = x[lo] - pad
            f["x2"] = max(x[hi] + pad, f["right"] + 8)
            left = min(left, f["x1"])
            right = max(right, f["x2"])
        for kind, _, obj, _ in self.placed:
            if kind == "note":
                left = min(left, obj.x)
                right = max(right, obj.x + obj.width)
        return left - 6, -4, right + 6, self.bottom + 4

    # ---- rendering
    def render(self, pen):
        x = self.x
        for i, p in enumerate(self.parts):
            pen.poly([(x[i], self.HEADER_H), (x[i], self.bottom)], color=HexColor("#9CA3AF"), dash=(3, 3))
        for i, p in enumerate(self.parts):
            self._render_participant(pen, p, x[i], self.widths[i])
        for f in self.frames:
            self._render_frame(pen, f)
        for kind, y, obj, number in self.placed:
            if kind == "msg":
                self._render_msg(pen, y, obj, number)
            elif kind == "self":
                self._render_self(pen, y, obj, number)
            elif kind == "note":
                obj.render(pen)
            elif kind == "divider":
                left, right = self.bounds[0] + 6, self.bounds[2] - 6
                pen.poly([(left, y), (right, y)], color=MUTED, dash=(1, 2))
                pen.label((left + right) / 2, y + 3, f"  {obj[1]}  ", BOLD, 8, MUTED)

    def _render_participant(self, pen, p, cx, w):
        fill, stroke = TONES[p["tone"]]
        lines = p["label"].split("\n")
        if p["kind"] == "actor":
            pen.circle(cx, 5, 4.5, fill=fill, stroke=stroke)
            pen.poly([(cx, 9.5), (cx, 19)], color=stroke)
            pen.poly([(cx - 7, 13), (cx + 7, 13)], color=stroke)
            pen.poly([(cx - 6, 26), (cx, 19), (cx + 6, 26)], color=stroke)
            pen.text(cx, 34, lines[0], BOLD, 8.5, TEXT, "center")
            return
        top, h = 2, self.HEADER_H - 4
        if p["kind"] == "db":
            pen.rect(cx - w / 2, top + 4, w, h - 4, fill=fill, stroke=stroke, width=1, radius=3)
            pen.ellipse(cx, top + 4, w / 2, 4, fill=fill, stroke=stroke)
        else:
            pen.rect(cx - w / 2, top, w, h, fill=fill, stroke=stroke, width=1, radius=2)
        total = len(lines) * 10
        yy = top + h / 2 - total / 2 + 8
        for k, line in enumerate(lines):
            pen.text(cx, yy + k * 10, line, BOLD if k == 0 else ITALIC, 8.5 if k == 0 else 7.2, TEXT, "center")

    def _render_frame(self, pen, f):
        x1, x2, top, bottom = f["x1"], f["x2"], f["top"], f["bottom"]
        stroke = HexColor("#4B5563")
        pen.rect(x1, top, x2 - x1, bottom - top, fill=None, stroke=stroke, width=0.8)
        tag = f["kind"]
        tag_w = tw(tag, BOLD, 8) + 12
        pen.shape([(x1, top), (x1 + tag_w, top), (x1 + tag_w, top + 9), (x1 + tag_w - 5, top + 14), (x1, top + 14)],
                  fill=HexColor("#F3F4F6"), stroke=stroke)
        pen.text(x1 + 5, top + 10.5, tag, BOLD, 8, TEXT)
        if f["guard"]:
            pen.text(x1 + tag_w + 6, top + 10.5, f["guard"], ITALIC, 7.8, TEXT)
        for y, guard in f["elses"]:
            pen.poly([(x1, y), (x2, y)], color=stroke, dash=(4, 3))
            if guard:
                pen.text(x1 + 6, y + 10, guard, ITALIC, 7.8, TEXT)

    def _render_msg(self, pen, y, e, number):
        _, a, b, text, kind = e
        xa, xb = self.x[self.index[a]], self.x[self.index[b]]
        dash = (4, 3) if kind == "return" else None
        pen.poly([(xa, y), (xb, y)], dash=dash, width=0.85)
        pen.head(xa, y, xb, y, "filled" if kind == "call" else "open")
        if text:
            label = self._msg_text(number, text)
            pen.label((xa + xb) / 2, y - 3.5, label, FONT if kind != "return" else ITALIC, 7.8, bg=None)

    def _render_self(self, pen, y, e, number):
        _, a, text = e
        xa = self.x[self.index[a]]
        pts = [(xa, y), (xa + 24, y), (xa + 24, y + 12), (xa, y + 12)]
        pen.poly(pts, width=0.85)
        pen.head(xa + 24, y + 12, xa, y + 12, "filled")
        pen.label(xa + 30, y + 8.5, self._msg_text(number, text), FONT, 7.8, TEXT, "left", bg=white)


# --------------------------------------------------------------------------- use-case diagrams
class UseCaseDiagram(Diagram):
    def __init__(self, legend_items=None):
        super().__init__()
        self.actors, self.ucs, self.links, self.boundaries, self.notes = {}, {}, [], [], []
        self.legend_items = legend_items

    def actor(self, id, label, x, y, tone="neutral"):
        self.actors[id] = dict(label=label, x=x, y=y, tone=tone)

    def boundary(self, x, y, w, h, title):
        self.boundaries.append((x, y, w, h, title))

    def uc(self, id, label, x, y, tone="mine", rx=None):
        lines = label.split("\n")
        width = max(tw(line, FONT, 8.3) for line in lines)
        self.ucs[id] = dict(lines=lines, x=x, y=y, rx=rx or max(width / 2 + 16, 52), ry=len(lines) * 5.5 + 10, tone=tone)

    def link(self, a, b):
        self.links.append((a, b, "assoc", None))

    def include(self, a, b):
        self.links.append((a, b, "include", "«include»"))

    def extend(self, a, b):
        self.links.append((a, b, "extend", "«extend»"))

    def generalize(self, child, parent):
        self.links.append((child, parent, "inherit", None))

    def note(self, x, y, text, width=180):
        self.notes.append(Note(x, y, text, width))

    def _clip(self, id, toward):
        if id in self.ucs:
            u = self.ucs[id]
            dx, dy = toward[0] - u["x"], toward[1] - u["y"]
            if dx == 0 and dy == 0:
                return u["x"], u["y"]
            t = 1 / math.sqrt((dx / u["rx"]) ** 2 + (dy / u["ry"]) ** 2)
            return u["x"] + dx * t, u["y"] + dy * t
        a = self.actors[id]
        box = Box("_", "", a["x"] - 16, a["y"] - 4, width=32)
        box.h = 46
        return box.clip(toward)

    def _center(self, id):
        if id in self.ucs:
            return self.ucs[id]["x"], self.ucs[id]["y"]
        a = self.actors[id]
        return a["x"], a["y"] + 19

    def layout(self):
        xs, ys = [], []
        for a in self.actors.values():
            half = max(tw(a["label"], BOLD, 8.5) / 2, 16)
            xs += [a["x"] - half, a["x"] + half]
            ys += [a["y"] - 6, a["y"] + 50]
        for u in self.ucs.values():
            xs += [u["x"] - u["rx"], u["x"] + u["rx"]]
            ys += [u["y"] - u["ry"], u["y"] + u["ry"]]
        for x, y, w, h, _ in self.boundaries:
            xs += [x, x + w]
            ys += [y - 4, y + h]
        for n in self.notes:
            xs += [n.x, n.x + n.width]
            ys += [n.y, n.y + n.h]
        legend_h = 22 if self.legend_items else 0
        self.legend_y = max(ys) + 18
        return min(xs) - 8, min(ys) - 8, max(xs) + 8, max(ys) + 8 + legend_h

    def render(self, pen):
        for x, y, w, h, title in self.boundaries:
            pen.rect(x, y, w, h, fill=HexColor("#FAFAFA"), stroke=LINE, width=1)
            pen.text(x + w / 2, y + 15, title, BOLD, 9.5, TEXT, "center")
        for a, b, kind, label in self.links:
            ca, cb = self._center(a), self._center(b)
            p1, p2 = self._clip(a, cb), self._clip(b, ca)
            dash = (4, 3) if kind in ("include", "extend") else None
            pen.poly([p1, p2], dash=dash)
            if kind in ("include", "extend"):
                pen.head(p1[0], p1[1], p2[0], p2[1], "open")
                pen.label((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2 + 3, label, ITALIC, 7.3)
            elif kind == "inherit":
                pen.head(p1[0], p1[1], p2[0], p2[1], "triangle")
        for u in self.ucs.values():
            fill, stroke = TONES[u["tone"]]
            pen.ellipse(u["x"], u["y"], u["rx"], u["ry"], fill=fill, stroke=stroke)
            start = u["y"] - (len(u["lines"]) - 1) * 5 + 3
            for k, line in enumerate(u["lines"]):
                pen.text(u["x"], start + k * 10, line, FONT, 8.3, TEXT, "center")
        for a in self.actors.values():
            fill, stroke = TONES[a["tone"]]
            x, y = a["x"], a["y"]
            pen.circle(x, y + 2, 5.5, fill=fill, stroke=stroke)
            pen.poly([(x, y + 7.5), (x, y + 22)], color=stroke)
            pen.poly([(x - 10, y + 13), (x + 10, y + 13)], color=stroke)
            pen.poly([(x - 8, y + 33), (x, y + 22), (x + 8, y + 33)], color=stroke)
            pen.text(x, y + 44, a["label"], BOLD, 8.5, TEXT, "center")
        for n in self.notes:
            n.render(pen)
        if self.legend_items:
            legend(pen, self.bounds[0] + 8, self.legend_y, self.legend_items)
