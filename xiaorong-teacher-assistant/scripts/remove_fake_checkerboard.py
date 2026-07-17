from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
CHARACTER_DIR = ROOT / "src" / "main" / "resources" / "static" / "assets" / "characters"


def is_fake_checker_pixel(pixel):
    red, green, blue, alpha = pixel
    if alpha == 0:
        return True
    bright = (red + green + blue) / 3
    neutral = max(red, green, blue) - min(red, green, blue)
    return bright >= 228 and neutral <= 18


def remove_connected_background(image):
    rgba = image.convert("RGBA")
    width, height = rgba.size
    pixels = rgba.load()
    visited = set()
    queue = deque()

    for x in range(width):
        queue.append((x, 0))
        queue.append((x, height - 1))
    for y in range(height):
        queue.append((0, y))
        queue.append((width - 1, y))

    while queue:
        x, y = queue.popleft()
        if (x, y) in visited or x < 0 or y < 0 or x >= width or y >= height:
            continue
        visited.add((x, y))
        if not is_fake_checker_pixel(pixels[x, y]):
            continue

        pixels[x, y] = (255, 255, 255, 0)
        queue.append((x + 1, y))
        queue.append((x - 1, y))
        queue.append((x, y + 1))
        queue.append((x, y - 1))

    return rgba


def trim_alpha(image, padding=24):
    alpha = image.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        return image

    left, top, right, bottom = bbox
    left = max(0, left - padding)
    top = max(0, top - padding)
    right = min(image.width, right + padding)
    bottom = min(image.height, bottom + padding)
    return image.crop((left, top, right, bottom))


def soften_alpha_edge(image, radius=0.35):
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    soft_alpha = alpha.filter(ImageFilter.GaussianBlur(radius=radius))
    rgba.putalpha(soft_alpha)
    return rgba


def upscale_for_retina(image, scale=2):
    resampling = getattr(Image, "Resampling", Image).LANCZOS
    return image.resize((image.width * scale, image.height * scale), resampling)


def process_file(path):
    image = Image.open(path)
    transparent = remove_connected_background(image)
    trimmed = trim_alpha(transparent)
    softened = soften_alpha_edge(trimmed)

    output = path.with_name(path.stem + "-transparent.png")
    softened.save(output)

    retina_output = path.with_name(path.stem + "-transparent@2x.png")
    upscale_for_retina(softened).save(retina_output)
    return output, retina_output


def main():
    names = [
        "teacher-teaching.png",
        "baizi-ask.png",
        "baizi-comfort.png",
        "baizi-happy.png",
    ]
    for name in names:
        outputs = process_file(CHARACTER_DIR / name)
        for output in outputs:
            print(output.relative_to(ROOT))


if __name__ == "__main__":
    main()
