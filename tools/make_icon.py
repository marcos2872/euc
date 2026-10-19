#!/usr/bin/env python3
"""Gera o ícone adaptativo do app a partir de branding/logo.jpeg.

- remove o fundo quase preto por *flood fill* a partir das bordas (preserva os
  elementos escuros internos, como o pneu e o capacete);
- recorta a arte e a centraliza na zona segura (66dp de 108dp) do ícone
  adaptativo, em todas as densidades;
- gera a camada `monochrome` (ícones temáticos do Android 13+);
- gera o fundo (gradiente escuro com brilho ciano) e prévias com máscaras
  (círculo, squircle, arredondado) para conferência.

Uso: python3 tools/make_icon.py
"""

from __future__ import annotations

import math
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "branding", "logo.jpeg")
RES = os.path.join(ROOT, "app", "src", "main", "res")
PREVIEW_DIR = "/tmp/opencode"

# Ícone adaptativo: 108dp de tela, com a zona segura de 66dp no centro.
CANVAS_DP = 108
SAFE_DP = 66
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

BACKGROUND_INNER = (14, 26, 36)
BACKGROUND_OUTER = (5, 8, 12)
GLOW = (53, 224, 245)


def extract_art() -> Image.Image:
    """Remove o fundo por flood fill das bordas e devolve a arte com alfa."""
    image = Image.open(SOURCE).convert("RGBA")
    width, height = image.size
    # Cor sentinela improvável na arte (para marcar o fundo a remover).
    sentinel = (255, 0, 255, 255)
    for seed in ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)):
        ImageDraw.floodfill(image, seed, sentinel, thresh=26)

    pixels = image.load()
    for y in range(height):
        for x in range(width):
            if pixels[x, y] == sentinel:
                pixels[x, y] = (0, 0, 0, 0)

    return image.crop(image.getbbox())


def background(size: int) -> Image.Image:
    """Fundo do ícone: gradiente radial escuro com brilho ciano suave."""
    image = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(image)
    center = (size / 2, size * 0.62)
    max_distance = math.hypot(center[0], size - center[1])
    for y in range(size):
        for x in range(size):
            distance = math.hypot(x - center[0], y - center[1]) / max_distance
            blend = min(1.0, distance)
            color = tuple(
                round(BACKGROUND_INNER[i] * (1 - blend) + BACKGROUND_OUTER[i] * blend)
                for i in range(3)
            )
            image.putpixel((x, y), (*color, 255))

    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    radius = size * 0.42
    glow_draw.ellipse(
        [center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius],
        fill=(*GLOW, 46),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.10))
    return Image.alpha_composite(image, glow)


def foreground(art: Image.Image, size: int) -> Image.Image:
    """Arte centralizada na zona segura, sobre um canvas transparente."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    target = int(size * SAFE_DP / CANVAS_DP)
    scale = min(target / art.width, target / art.height)
    resized = art.resize(
        (max(1, round(art.width * scale)), max(1, round(art.height * scale))),
        Image.LANCZOS,
    )
    canvas.alpha_composite(
        resized,
        ((size - resized.width) // 2, (size - resized.height) // 2),
    )
    return canvas


def monochrome(art: Image.Image, size: int) -> Image.Image:
    """Silhueta branca da arte (ícones temáticos do Android 13+)."""
    white = Image.new("RGBA", art.size, (255, 255, 255, 0))
    white.putalpha(art.getchannel("A"))
    return foreground(white, size)


def write_layers(art: Image.Image) -> None:
    for density, factor in DENSITIES.items():
        size = round(CANVAS_DP * factor)
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        foreground(art, size).save(os.path.join(folder, "ic_launcher_foreground.png"))
        monochrome(art, size).save(os.path.join(folder, "ic_launcher_monochrome.png"))
        background(size).save(os.path.join(folder, "ic_launcher_background.png"))
        print(f"  mipmap-{density}: {size}x{size}px")

    # Logo para uso dentro do app (ex.: tela Sobre) — arte com fundo transparente.
    logo = art.resize((512, round(512 * art.height / art.width)), Image.LANCZOS)
    logo.save(os.path.join(RES, "drawable-nodpi", "brand_logo.png"))
    print("  drawable-nodpi/brand_logo.png")

    # Logo do splash: quadrado com margem generosa, porque o sistema recorta o
    # ícone do splash num círculo.
    splash_size = 512
    splash = Image.new("RGBA", (splash_size, splash_size), (0, 0, 0, 0))
    scale = min(splash_size * 0.62 / art.width, splash_size * 0.62 / art.height)
    resized = art.resize(
        (max(1, round(art.width * scale)), max(1, round(art.height * scale))),
        Image.LANCZOS,
    )
    splash.alpha_composite(
        resized,
        ((splash_size - resized.width) // 2, (splash_size - resized.height) // 2),
    )
    splash.save(os.path.join(RES, "drawable-nodpi", "splash_logo.png"))
    print("  drawable-nodpi/splash_logo.png")


def write_previews(art: Image.Image) -> None:
    """Prévias com as máscaras do sistema, para conferência visual."""
    size = 432
    foreground_layer = foreground(art, size)
    background_layer = background(size)
    icon = Image.alpha_composite(background_layer, foreground_layer)

    masks = {
        "circulo": Image.new("L", (size, size), 0),
        "squircle": Image.new("L", (size, size), 0),
        "redondo": Image.new("L", (size, size), 0),
    }
    ImageDraw.Draw(masks["circulo"]).ellipse([0, 0, size, size], fill=255)
    ImageDraw.Draw(masks["redondo"]).ellipse([size * 0.06, size * 0.06, size * 0.94, size * 0.94], fill=255)
    ImageDraw.Draw(masks["squircle"]).rounded_rectangle(
        [0, 0, size, size], radius=size * 0.28, fill=255
    )

    sheet = Image.new("RGBA", (size * 3 + 40, size), (18, 18, 20, 255))
    for index, (name, mask) in enumerate(masks.items()):
        masked = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        masked.paste(icon, (0, 0), mask)
        sheet.alpha_composite(masked, (index * (size + 20), 0))
        print(f"  prévia: {name}")
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    sheet.save(os.path.join(PREVIEW_DIR, "icon_preview.png"))

    # Arte extraída isolada (para conferir se o recorte ficou limpo).
    art.resize((512, round(512 * art.height / art.width)), Image.LANCZOS).save(
        os.path.join(PREVIEW_DIR, "icon_art.png")
    )


def main() -> None:
    art = extract_art()
    print(f"arte extraída: {art.width}x{art.height}")
    write_layers(art)
    write_previews(art)


if __name__ == "__main__":
    main()
