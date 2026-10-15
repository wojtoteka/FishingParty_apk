"""
Przemalowuje oryginalne sprity Fishing Party na warianty używane w grze.

Źródło:  tools/original_sprites/*.png   (nietknięte oryginały)
Wynik:   app/src/main/assets/sprites/... (postacie, ryby, łodzie, marynarze)
         app/src/main/assets/tiles/<motyw>/... (kafelki mapy dla każdego świata)

Skrypt tylko podmienia kolory pikseli - kształty spritów zostają oryginalne.
Uruchomienie:  python tools/recolor_sprites.py
"""
import colorsys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "original_sprites"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT_SPRITES = ASSETS / "sprites"
OUT_TILES = ASSETS / "tiles"

TILE_FILES = [
    "sea", "wave0", "wave1", "wave2", "wave3", "wave4",
    "sandfull",
    "sandtop0", "sandtop1", "sandbottom0", "sandbottom1",
    "sandleft0", "sandleft1", "sandright0", "sandright1",
    "sandtopleft0", "sandtopleft1", "sandtopright0", "sandtopright1",
    "sandleftbottom0", "sandleftbottom1", "sandbottomright0", "sandbottomright1",
    "rock0", "rock1", "rock2",
    "wall0", "wall1", "wallsand", "walltile",
    "door0", "door1",
    "crab",
]

CHARACTER_FILES = [
    "arrowup", "doigt", "huit", "fish", "fish1", "fish2", "fishscore",
    "mast", "octopus1", "octopus2", "piranha1", "piranha2",
    "piranhaback0", "piranhaback1", "piranhafront0", "piranhafront1",
    "sailor", "shark1", "shark2", "ship", "ship2", "shiptop",
]

SEA = (0, 151, 227)
FOAM = (227, 236, 255)
SAND = [(255, 179, 28), (255, 179, 27)]
SAND_LIGHT = (255, 208, 113)
ROCK = [(90, 90, 90), (135, 135, 135), (180, 180, 180)]
WALL = [(133, 108, 95), (170, 145, 132), (207, 182, 169), (227, 193, 176),
        (227, 193, 175), (241, 212, 197), (255, 255, 251)]
WOOD = [(124, 40, 0), (126, 51, 14), (142, 58, 16), (146, 58, 14),
        (170, 82, 38), (194, 106, 62), (224, 136, 92)]
CRAB = (255, 57, 57)

# id, woda, piana, piasek, jasny piasek, skały (ciemna, środek, jasna), mnożnik murów, mnożnik drewna, krab
THEMES = [
    ("sunny_bay", None),
    ("coral_reef", dict(water=(0, 170, 170), foam=(220, 255, 240), sand=(255, 150, 120), sand_light=(255, 195, 170),
                        rock=[(80, 50, 80), (120, 80, 110), (170, 120, 150)], wall=(1.0, 0.95, 0.95),
                        wood=(1.0, 1.0, 1.0), crab=(255, 90, 60))),
    ("sunset_lagoon", dict(water=(90, 80, 200), foam=(255, 214, 190), sand=(255, 140, 50), sand_light=(255, 190, 110),
                           rock=[(70, 55, 85), (110, 90, 120), (150, 130, 160)], wall=(1.05, 0.9, 0.85),
                           wood=(1.0, 0.9, 0.9), crab=(255, 60, 90))),
    ("frozen_fjord", dict(water=(20, 90, 170), foam=(235, 248, 255), sand=(222, 234, 248), sand_light=(255, 255, 255),
                          rock=[(80, 95, 115), (120, 140, 160), (170, 190, 205)], wall=(0.85, 0.95, 1.1),
                          wood=(0.8, 0.85, 1.0), crab=(90, 170, 255))),
    ("misty_swamp", dict(water=(50, 120, 90), foam=(190, 225, 180), sand=(140, 100, 55), sand_light=(175, 135, 80),
                         rock=[(60, 70, 50), (95, 105, 80), (135, 145, 110)], wall=(0.85, 0.95, 0.8),
                         wood=(0.9, 0.95, 0.8), crab=(200, 120, 40))),
    ("volcanic_isles", dict(water=(40, 60, 110), foam=(255, 190, 150), sand=(75, 65, 70), sand_light=(255, 110, 40),
                            rock=[(35, 28, 30), (60, 50, 50), (95, 80, 80)], wall=(0.6, 0.55, 0.55),
                            wood=(0.7, 0.6, 0.6), crab=(255, 80, 0))),
    ("emerald_atoll", dict(water=(0, 185, 160), foam=(235, 255, 250), sand=(250, 225, 160), sand_light=(255, 245, 205),
                           rock=[(90, 90, 80), (130, 130, 120), (180, 180, 165)], wall=(1.0, 1.0, 0.95),
                           wood=(1.0, 1.0, 1.0), crab=(255, 120, 80))),
    ("midnight_sea", dict(water=(15, 45, 110), foam=(150, 185, 255), sand=(95, 95, 150), sand_light=(150, 150, 210),
                          rock=[(45, 48, 75), (70, 75, 110), (110, 115, 155)], wall=(0.6, 0.65, 0.85),
                          wood=(0.6, 0.6, 0.8), crab=(200, 80, 140))),
    ("desert_oasis", dict(water=(0, 155, 190), foam=(220, 250, 255), sand=(235, 150, 70), sand_light=(250, 200, 130),
                          rock=[(120, 75, 45), (170, 110, 70), (210, 150, 100)], wall=(1.1, 0.95, 0.75),
                          wood=(1.0, 0.95, 0.85), crab=(230, 60, 40))),
    ("abyss", dict(water=(25, 30, 75), foam=(90, 220, 255), sand=(70, 45, 95), sand_light=(140, 90, 190),
                   rock=[(30, 28, 50), (55, 50, 80), (90, 85, 120)], wall=(0.55, 0.5, 0.75),
                   wood=(0.55, 0.5, 0.7), crab=(120, 255, 200))),
    # Tylko poziom 1000 (Wielki Finał): królewska woda, złoty piasek i złote skały.
    ("legend", dict(water=(55, 25, 120), foam=(255, 225, 120), sand=(255, 205, 50), sand_light=(255, 245, 170),
                    rock=[(150, 95, 0), (210, 150, 20), (255, 215, 80)], wall=(1.15, 1.0, 0.55),
                    wood=(1.0, 0.85, 0.6), crab=(255, 70, 70))),
]


def clamp(v):
    return max(0, min(255, int(round(v))))


def lerp(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def water_t(c):
    """Pozycja koloru na odcinku morze -> piana (0..1)."""
    d = [FOAM[i] - SEA[i] for i in range(3)]
    num = sum((c[i] - SEA[i]) * d[i] for i in range(3))
    den = sum(x * x for x in d)
    return max(0.0, min(1.0, num / den))


def theme_color(c, th):
    rgb = c[:3]
    if rgb in SAND:
        return th["sand"]
    if rgb == SAND_LIGHT:
        return th["sand_light"]
    if rgb in ROCK:
        return th["rock"][ROCK.index(rgb)]
    if rgb in WALL:
        return tuple(clamp(rgb[i] * th["wall"][i]) for i in range(3))
    if rgb in WOOD:
        return tuple(clamp(rgb[i] * th["wood"][i]) for i in range(3))
    if rgb == CRAB:
        return th["crab"]
    r, g, b = rgb
    if b > 170 and r < 245 and b >= r:  # odcienie wody, fal i piany
        return lerp(th["water"], th["foam"], water_t(rgb))
    return rgb


def recolor(img, fn):
    img = img.convert("RGBA")
    out = Image.new("RGBA", img.size)
    src = img.load()
    dst = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            p = src[x, y]
            if p[3] == 0:
                dst[x, y] = p
            else:
                dst[x, y] = fn(p[:3]) + (p[3],)
    return out


def mapping(table):
    def fn(rgb):
        return table.get(rgb, rgb)
    return fn


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


def load(name):
    return Image.open(SRC / f"{name}.png").convert("RGBA")


def build_tiles():
    for theme_id, th in THEMES:
        for name in TILE_FILES:
            img = load(name)
            if th is not None:
                img = recolor(img, lambda rgb, th=th: theme_color(rgb, th))
            save(img, OUT_TILES / theme_id / f"{name}.png")


def build_characters():
    for name in CHARACTER_FILES:
        save(load(name), OUT_SPRITES / f"{name}.png")

    fish_body, fish_light, fish_fin, fish_eye = (127, 104, 142), (185, 173, 194), (255, 57, 57), (0, 76, 114)
    variants = {
        "fishblue": {fish_body: (40, 110, 210), fish_light: (150, 200, 255), fish_fin: (255, 215, 60), fish_eye: (0, 30, 70)},
        "fishgold": {fish_body: (255, 185, 20), fish_light: (255, 240, 150), fish_fin: (255, 110, 20), fish_eye: (90, 40, 0)},
    }
    for prefix, table in variants.items():
        for i in (1, 2):
            save(recolor(load(f"fish{i}"), mapping(table)), OUT_SPRITES / f"{prefix}{i}.png")

    save(recolor(load("fishscore"), mapping({(198, 44, 44): (255, 196, 32)})), OUT_SPRITES / "fishscore_gold.png")
    save(recolor(load("fishscore"), mapping({(198, 44, 44): (112, 118, 134)})), OUT_SPRITES / "fishscore_grey.png")
    save(recolor(load("fishscore"), mapping({(198, 44, 44): (40, 140, 230)})), OUT_SPRITES / "fishscore_blue.png")

    red_piranha = {
        (69, 209, 131): (220, 70, 70), (33, 174, 99): (170, 30, 40), (123, 255, 57): (255, 110, 80),
        (196, 255, 130): (255, 180, 150), (35, 175, 97): (170, 30, 40), (9, 149, 71): (120, 10, 20),
        (45, 182, 57): (190, 40, 40),
    }
    for name in ("piranha1", "piranha2", "piranhaback0", "piranhaback1", "piranhafront0", "piranhafront1"):
        save(recolor(load(name), mapping(red_piranha)), OUT_SPRITES / (name.replace("piranha", "piranhared") + ".png"))

    dark_shark = {
        (82, 158, 196): (70, 75, 95), (52, 128, 166): (45, 50, 65), (127, 203, 241): (110, 115, 140),
        (145, 186, 207): (130, 135, 155), (117, 158, 178): (95, 100, 120), (161, 202, 223): (140, 145, 165),
        (0, 76, 114): (25, 28, 40),
    }
    for i in (1, 2):
        save(recolor(load(f"shark{i}"), mapping(dark_shark)), OUT_SPRITES / f"sharkdark{i}.png")

    # Ikony wzmocnień: lodowy rekin (Lodowa mgła) i złota pętla (Rytm).
    frost_shark = {
        (82, 158, 196): (170, 225, 245), (52, 128, 166): (120, 190, 225), (127, 203, 241): (220, 245, 255),
        (145, 186, 207): (200, 235, 250), (117, 158, 178): (160, 210, 235), (161, 202, 223): (235, 250, 255),
        (0, 76, 114): (40, 110, 160), (105, 105, 105): (190, 225, 245), (75, 75, 75): (130, 180, 215),
        (99, 98, 136): (150, 185, 225),
    }
    save(recolor(load("shark1"), mapping(frost_shark)), OUT_SPRITES / "sharkfrost1.png")
    save(recolor(load("huit"), mapping({(255, 255, 255): (255, 196, 32)})), OUT_SPRITES / "combo.png")

    red_octopus = {
        (140, 112, 226): (220, 70, 60), (185, 101, 227): (250, 110, 70),
        (228, 144, 255): (255, 170, 120), (170, 170, 255): (255, 200, 170),
    }
    for i in (1, 2):
        save(recolor(load(f"octopus{i}"), mapping(red_octopus)), OUT_SPRITES / f"octopusred{i}.png")


def hull_shades(main):
    """4 odcienie kadłuba z koloru głównego (jak HullPaints.shades w grze): ciemniejszy, główny, 2 jaśniejsze."""
    h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in main])

    def shade(sat, val_mul, val_add):
        val = v * val_mul + val_add
        sat = s * sat - max(0.0, val - 1) * 0.9  # zabrakło jasności – rozjaśnia spadek nasycenia
        rgb = colorsys.hsv_to_rgb(h, max(0.0, min(1.0, sat)), max(0.0, min(1.0, val)))
        return tuple(clamp(c * 255) for c in rgb)

    return [shade(1.05, 0.78, 0), tuple(main), shade(0.92, 1.12, 0.04), shade(0.82, 1.24, 0.08)]


BOATS = {
    "classic": None,
    "crimson": [(110, 10, 10), (140, 25, 25), (165, 40, 40), (190, 60, 55)],
    "navy": [(10, 25, 70), (20, 40, 95), (35, 60, 120), (55, 85, 150)],
    "emerald": [(10, 60, 30), (20, 85, 45), (35, 110, 60), (55, 135, 80)],
    "ivory": [(150, 140, 120), (190, 180, 160), (215, 207, 190), (238, 233, 218)],
    "golden": [(150, 95, 0), (190, 130, 10), (220, 165, 30), (245, 200, 60)],
    "shadow": [(20, 18, 25), (35, 32, 42), (52, 48, 62), (72, 68, 85)],
    "royal": [(60, 15, 80), (85, 30, 110), (110, 50, 140), (135, 75, 165)],
    # Wersja 2.3
    "sky": [(30, 90, 150), (45, 115, 180), (70, 145, 205), (100, 175, 230)],
    "teal": [(0, 80, 80), (10, 105, 100), (25, 130, 120), (45, 155, 145)],
    "mint": [(40, 120, 90), (60, 150, 110), (90, 180, 135), (130, 210, 165)],
    "lime": [(70, 110, 10), (95, 140, 20), (125, 170, 35), (160, 200, 60)],
    "amber": [(150, 60, 0), (185, 85, 5), (215, 115, 20), (240, 150, 45)],
    "rose": [(140, 40, 80), (175, 60, 105), (205, 90, 135), (230, 125, 165)],
    "lavender": [(90, 70, 140), (115, 95, 170), (145, 125, 195), (175, 160, 220)],
    "silver": [(90, 95, 105), (125, 130, 140), (160, 165, 175), (195, 200, 210)],
    # Kolory z jednego odcienia głównego – tak samo liczy je kreator kolorów w grze.
    "tangerine": hull_shades((225, 95, 20)),
    "coral": hull_shades((230, 105, 95)),
    "lemon": hull_shades((225, 200, 40)),
    "turquoise": hull_shades((30, 180, 170)),
    "cobalt": hull_shades((30, 70, 200)),
    "fuchsia": hull_shades((200, 40, 150)),
    "plum": hull_shades((110, 30, 75)),
    "ice": hull_shades((185, 220, 235)),
}
HULL = [(85, 27, 0), (97, 39, 9), (113, 55, 25), (129, 71, 41)]


def build_boats():
    for boat_id, shades in BOATS.items():
        table = {} if shades is None else dict(zip(HULL, shades))
        for name in ("ship", "ship2", "shiptop"):
            save(recolor(load(name), mapping(table)), OUT_SPRITES / "boats" / boat_id / f"{name}.png")


SAILORS = {
    "ginger": None,
    "blond": {(255, 104, 28): (255, 214, 70)},
    "brunet": {(255, 104, 28): (96, 54, 28), (0, 57, 170): (48, 48, 56)},
    "captain": {(255, 104, 28): (236, 236, 236), (0, 57, 170): (22, 32, 84)},
    "punk": {(255, 104, 28): (255, 80, 170), (0, 57, 170): (34, 34, 40)},
    "diver": {(255, 104, 28): (60, 200, 120), (0, 57, 170): (210, 120, 0)},
    # Wersja 2.3 (część z inną karnacją: skóra i jej cień)
    "fisher": {(255, 104, 28): (60, 140, 60), (0, 57, 170): (120, 80, 40), (255, 227, 170): (198, 134, 66), (227, 185, 101): (160, 100, 45)},
    "surfer": {(255, 104, 28): (0, 200, 220), (0, 57, 170): (255, 140, 0)},
    "beach": {(255, 104, 28): (255, 214, 70), (0, 57, 170): (30, 140, 220), (255, 227, 170): (141, 85, 36), (227, 185, 101): (110, 60, 20)},
    "pirate": {(255, 104, 28): (200, 30, 40), (0, 57, 170): (30, 30, 36)},
    "ninja": {(255, 104, 28): (40, 40, 48), (0, 57, 170): (60, 60, 70)},
    "ghost": {(255, 104, 28): (200, 215, 240), (0, 57, 170): (150, 170, 210), (255, 227, 170): (230, 240, 255), (227, 185, 101): (190, 205, 230)},
    "king": {(255, 104, 28): (150, 60, 200), (0, 57, 170): (255, 200, 40), (255, 227, 170): (224, 172, 105), (227, 185, 101): (190, 135, 70)},
    "golden": {(255, 104, 28): (255, 200, 40), (0, 57, 170): (200, 140, 0)},
}


def build_sailors():
    for sailor_id, table in SAILORS.items():
        save(recolor(load("sailor"), mapping(table or {})), OUT_SPRITES / "sailors" / f"{sailor_id}.png")


# Wersja 2.3: postacie narysowane od zera (9x9, jak oryginalny marynarz), a nie przemalowane.
# Litery a/d/c to skóra, jej cień i oczy oryginału; pozostałe – kolory z tabeli danej postaci.
DRAWN_SAILORS = {
 "viking": (["w.......w",
             "w.ggggg.w",
             ".wgggggw.",
             "..gcacg..",
             "..yaaay..",
             ".ayyyyya.",
             "..arrra..",
             "...ppp...",
             "...p.p..."], dict(w=(240,232,205), g=(150,158,170), y=(255,206,60), r=(190,40,40), p=(110,70,40))),
 "chef": ([".wwwwwww.",
           "..wwwww..",
           "..ggggg..",
           "..acaca..",
           "..ammma..",
           ".awwwwwa.",
           "..wwkww..",
           "...kwk...",
           "...k.k..."], dict(w=(250,250,250), g=(200,205,215), k=(40,40,48), m=(90,50,20))),
 "cat": (["..o...o..",
          "..oo.oo..",
          "..ooooo..",
          "..gcocg..",
          "..oopoo..",
          ".owwwwwo.",
          "..nnnnn..",
          "...www...",
          "...o.o..."], dict(o=(245,150,40), g=(245,150,40), p=(255,130,150), w=(240,240,250), n=(30,60,150))),
 "penguin": (["...kkk...",
              "..kkkkk..",
              "..kwkwk..",
              "..kwowk..",
              "..kwwwk..",
              ".kkwwwkk.",
              "..kwwwk..",
              "...kkk...",
              "..oo.oo.."], dict(k=(30,32,40), w=(245,245,250), o=(255,150,30))),
 "frog": (["..g...g..",
           ".gwg.gwg.",
           ".gcgggcg.",
           ".ggggggg.",
           "..grrrg..",
           ".lgggggl.",
           "..lllll..",
           "...ggg...",
           "..gg.gg.."], dict(g=(70,170,60), w=(250,250,250), r=(200,40,60), l=(150,215,90))),
 "skeleton": (["..rrrrr..",
               ".rrwwwrr.",
               "..kkwkk..",
               "..wwkww..",
               "...wkw...",
               ".w.www.w.",
               "...www...",
               "...w.w...",
               "...w.w..."], dict(r=(200,30,40), w=(235,235,225), k=(20,20,24))),
 "santa": (["....rrw..",
            "...rrr...",
            "..wwwww..",
            "..acaca..",
            "..wwwww..",
            ".rwwwwwr.",
            "..rkykr..",
            "...rrr...",
            "...k.k..."], dict(r=(210,30,40), w=(250,250,250), k=(30,30,34), y=(255,200,40))),
 "alien": (["..a...a..",
            "...a.a...",
            "..ggggg..",
            ".gkkgkkg.",
            "..ggggg..",
            "...gdg...",
            "..sssss..",
            "...sss...",
            "...s.s..."], dict(a=(120,230,90), g=(120,230,90), k=(20,20,30), d=(60,140,50), s=(160,90,220))),
 "robot": (["....r....",
            "....m....",
            "..mmmmm..",
            "..mcmcm..",
            "..mmmmm..",
            ".dmyyymd.",
            "..mmmmm..",
            "...mmm...",
            "...d.d..."], dict(r=(255,60,60), m=(170,180,195), c=(60,220,255), d=(100,110,125), y=(255,200,40))),
 "astronaut": (["...www...",
                "..wwwww..",
                ".wwbbbww.",
                ".wbbllbw.",
                ".wwbbbww.",
                "..wwwww..",
                ".wwrwrww.",
                "...www...",
                "...w.w..."], dict(w=(240,242,248), b=(30,50,110), l=(130,200,255), r=(220,60,60))),
 "wizard": (["....p....",
             "...ppp...",
             "...pyp...",
             ".ppppppp.",
             "..acaca..",
             "..wwwww..",
             ".pwwwwwp.",
             "..ppwpp..",
             "...ppp..."], dict(p=(100,60,180), y=(255,215,60), w=(235,235,240))),
 "shark": (["....g....",
            "...ggg...",
            "..ggggg..",
            ".gcgggcg.",
            ".wrwrwrw.",
            "..rrrrr..",
            ".gwwwwwg.",
            "...ggg...",
            "..gg.gg.."], dict(g=(110,130,150), w=(250,250,250), r=(200,40,50))),
}


def build_drawn_sailors():
    base = {"a": (255, 227, 170), "d": (227, 185, 101), "c": (0, 0, 0)}
    for sailor_id, (rows, colors) in DRAWN_SAILORS.items():
        palette = {**base, **colors}
        image = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            assert len(row) == 9 and len(rows) == 9, sailor_id
            for x, ch in enumerate(row):
                if ch != ".":
                    image.putpixel((x, y), palette[ch] + (255,))
        save(image, OUT_SPRITES / "sailors" / f"{sailor_id}.png")


# Wersja 2.3: widmokuda – najgroźniejszy potwór, narysowana od zera w stylu oryginałów (bez czarnego
# obrysu, jasny grzbiet i ciemny spód, małe oko i paszcza pełna kłów jak u piranii). Patrzy w prawo jak rekin.
# T/B/M/D – blask grzbietu, grzbiet, bok i spód; W – jasny brzuch; s – kolce grzbietu; f – płetwy;
# A/a – półprzezroczysty ogon („widmo”); t/h – kły i paszcza; e/p – oko i źrenica.
# Wariant „lock” (namierzyła łódź): czerwone oko i zjeżone, czerwone kolce.
STALKER_FRAMES = [
    ["..........s..s........",
     "A........sssssf.......",
     "AA.......TTTTTTT......",
     "AAA....TTBBTBBDDDT....",
     ".AAa.TTBTBBBBBBeeDTT..",
     "..aaTBBBMMMMMMMepBBBT.",
     "..aaBBBMMMfMMMMMMMBBBT",
     "..aaBWWWfffMMMhththth.",
     ".aaaDWWWWWWWWWhhththtt",
     "aaa..DDDWWWWWWWWWWWDDD",
     "aa...ff.DDDDDDDDDDD...",
     "a........ff..........."],
    ["..........s..s........",
     ".........sssssf.......",
     ".A.......TTTTTTT......",
     "AAA....TTBBTBBDDDT....",
     ".AAa.TTBTBBBBBBeeDTT..",
     "..aaTBBBMMMMMMMepBBBT.",
     "..aaBBBMMMfMMMMMMMBBBT",
     "..aaBWWWMfffMMhththth.",
     ".aaaDWWWWWWWWWhhththtt",
     "aaa..DDDWWWWWWWWWWWDDD",
     "aa...ff.DDDDDDDDDDD...",
     "aa.......ff..........."],
]
STALKER_COLORS = dict(
    T=(178, 168, 255), B=(122, 104, 232), M=(92, 72, 196), D=(58, 42, 138), W=(206, 212, 255),
    s=(150, 136, 250), f=(96, 78, 206), A=(150, 136, 250), a=(96, 78, 206),
    t=(240, 244, 255), h=(24, 14, 56), e=(255, 224, 64), p=(24, 14, 56),
)
STALKER_LOCK = dict(e=(255, 72, 52), s=(255, 96, 72))
STALKER_GHOST_ALPHA = 170


def build_stalker():
    for prefix, extra in (("stalker", {}), ("stalkerlock", STALKER_LOCK)):
        palette = {**STALKER_COLORS, **extra}
        for i, rows in enumerate(STALKER_FRAMES, start=1):
            image = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
            for y, row in enumerate(rows):
                assert len(row) == len(rows[0]), (prefix, i, y)
                for x, ch in enumerate(row):
                    if ch != ".":
                        alpha = STALKER_GHOST_ALPHA if ch in "Aa" else 255
                        image.putpixel((x, y), palette[ch] + (alpha,))
            save(image, OUT_SPRITES / f"{prefix}{i}.png")


def build_notification_icon():
    """Ikona na pasku powiadomień: biała sylwetka oryginalnej rybki, powiększona bez wygładzania."""
    src = load("fishscore")
    icon = Image.new("RGBA", src.size, (0, 0, 0, 0))
    for y in range(src.height):
        for x in range(src.width):
            if src.getpixel((x, y))[3] > 0:
                icon.putpixel((x, y), (255, 255, 255, 255))
    out = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_stat_fish.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    icon.resize((src.width * 12, src.height * 12), Image.NEAREST).save(out)


if __name__ == "__main__":
    build_tiles()
    build_characters()
    build_boats()
    build_sailors()
    build_drawn_sailors()
    build_stalker()
    build_notification_icon()
    print("OK")
