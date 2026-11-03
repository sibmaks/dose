#!/usr/bin/env python3
"""Compose RuStore artwork around unmodified app captures. Requires Pillow.

Only Android's status/navigation bars are trimmed. App pixels are uniformly
scaled, never repainted, rearranged, stretched, or replaced with mock UI.
"""

import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "store-assets/rustore/ru"
FONT = "/System/Library/Fonts/Avenir Next.ttc"
SIZE = (1080, 1920)

# Asset key, upload filename, headline, supporting copy, palette.
SCENES = [
    ("01-today", "01-today", "Лекарства —\nпо расписанию", "Ближайший приём и прогресс дня\nна одном экране.", "violet"),
    ("02-medications", "02-medications", "Все лекарства\nпод рукой", "Дозировки, время и заметки —\nв одном списке.", "lavender"),
    ("03-schedule", "03-schedule", "Расписание\nпод ваш день", "Каждый день, по дням недели\nили в выбранные даты.", "mint"),
    ("04-dosage", "04-dosage", "Укажите нужную\nдозировку", "Удобные единицы измерения\nи возможность добавить свои.", "lavender"),
    ("05-forms", "05-forms", "Разные формы.\nОдин порядок.", "Таблетки, капсулы, капли\nи другие формы приёма.", "peach"),
    ("06-popup", "06-popup", "Напоминание\nперед глазами", "Отметьте приём или отложите\nнапоминание на 10 минут.", "violet"),
    ("07-history", "07-history", "История\nкаждого приёма", "Время приёма и напоминания —\nв подробной истории событий.", "mint"),
    ("08-report", "08-report", "Следите за\nрегулярностью", "Средняя задержка приёма\nза неделю, месяц или всё время.", "lavender"),
    ("09-dark", "09-dark", "Светлая днём.\nТёмная вечером.", "Выберите удобную тему\nили доверьте выбор системе.", "night"),
    ("10-languages", "10-languages", "На вашем\nязыке", "Русский и ещё 12 языков.\nПереключайтесь в приложении.", "peach"),
]
PALETTES = {
    "violet": ("#5543B6", "#7966DD", "#FFFFFF", "#E7E0FF", "#9C8BEA"),
    "lavender": ("#F5F1FF", "#E8E1FA", "#29243D", "#60596F", "#D5C9F0"),
    "mint": ("#F1F8F5", "#DEEEE7", "#263B35", "#536D63", "#C4E0D3"),
    "peach": ("#FFF7F0", "#F5E5DD", "#392B3C", "#756274", "#E9D0CB"),
    "night": ("#1E1C30", "#34304D", "#FFFFFF", "#CEC7E4", "#54496F"),
}


def rgb(color):
    return tuple(bytes.fromhex(color.lstrip("#")))


def font(size, weight="regular"):
    return ImageFont.truetype(FONT, size, index={"regular": 7, "medium": 5, "bold": 0, "demi": 2}[weight])


def draw_lines(draw, x, y, text, face, color, line_height, max_width):
    for line in text.splitlines():
        assert draw.textlength(line, font=face) <= max_width, f"Text overflow: {line}"
        draw.text((x, y), line, font=face, fill=color, anchor="lt")
        y += line_height


def background(palette):
    top, bottom, ink, muted, decoration = PALETTES[palette]
    image = Image.new("RGB", SIZE)
    d = ImageDraw.Draw(image)
    a, b = rgb(top), rgb(bottom)
    for y in range(SIZE[1]):
        t = y / (SIZE[1] - 1)
        d.line((0, y, SIZE[0], y), fill=tuple(round(x + (z - x) * t) for x, z in zip(a, b)))
    # Quiet, off-canvas circular lines echo the app's dose progress indicator.
    d.arc((610, 1060, 1480, 1930), 78, 305, fill=decoration, width=2)
    d.arc((-530, 1050, 270, 1850), 242, 77, fill=decoration, width=2)
    return image, ink, muted


def card(image, screenshot, device):
    # Crop only system chrome. Keep the complete application viewport.
    density = 360 if device == "phone" else 320
    top = round(24 * density / 160)
    bottom = round(48 * density / 160)
    content = screenshot.crop((0, top, screenshot.width, screenshot.height - bottom)).convert("RGB")
    max_width, max_height = (828, 1370) if device == "phone" else (912, 1370)
    scale = min(max_width / content.width, max_height / content.height)
    width, height = round(content.width * scale), round(content.height * scale)
    x = (1080 - width) // 2
    y = 466 + (1370 - height) // 2
    border = 10
    outer = (x - border, y - border, x + width + border, y + height + border)
    shadow = Image.new("RGBA", SIZE)
    ImageDraw.Draw(shadow).rounded_rectangle(
        (outer[0] - 3, outer[1] + 15, outer[2] + 3, outer[3] + 25), 38,
        fill=(29, 19, 62, 52))
    image = Image.alpha_composite(image.convert("RGBA"), shadow.filter(ImageFilter.GaussianBlur(24)))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle(outer, radius=34, fill="#FFFFFF")
    scaled = content.resize((width, height), Image.Resampling.LANCZOS)
    mask = Image.new("L", (width, height))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=25, fill=255)
    image.paste(scaled, (x, y), mask)
    assert outer[1] >= 450 and outer[3] <= 1860
    return image.convert("RGB"), [0, top, screenshot.width, screenshot.height - bottom], list(outer)


def render(scene, device, position, count):
    key, name, title, copy, palette = scene
    source = OUT / "raw" / device / (key + ".png")
    screenshot = Image.open(source)
    expected = (1080, 1920) if device == "phone" else (1200, 1920)
    assert screenshot.size == expected, (source, screenshot.size)
    canvas, ink, muted = background(palette)
    d = ImageDraw.Draw(canvas)
    icon = Image.open(ROOT / "design-reference/app-icon-512.png").convert("RGB").resize((64, 64), Image.Resampling.LANCZOS)
    mask = Image.new("L", (64, 64))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 63, 63), radius=17, fill=255)
    canvas.paste(icon, (72, 62), mask)
    d.text((154, 70), "dose.", font=font(47, "bold"), fill=ink, anchor="lt")
    d.text((1008, 83), f"{position:02d} / {count:02d}", font=font(22, "demi"), fill=muted, anchor="rt")
    draw_lines(d, 72, 168, title, font(77, "bold"), ink, 90, 936)
    draw_lines(d, 76, 365, copy, font(31, "medium"), muted, 43, 928)
    canvas, crop, bounds = card(canvas, screenshot, device)
    target_dir = OUT / device
    target_dir.mkdir(parents=True, exist_ok=True)
    # Tablet filenames have their own continuous upload order.
    target = target_dir / f"{position:02d}-{name[3:]}.jpg"
    canvas.save(target, quality=95, subsampling=0, optimize=True, dpi=(72, 72))
    with Image.open(target) as check:
        assert check.size == SIZE and check.mode == "RGB" and check.format == "JPEG"
    assert target.stat().st_size < 3_000_000
    return dict(file=str(target.relative_to(OUT)), width=1080, height=1920,
                bytes=target.stat().st_size, title=title, subtitle=copy,
                source=str(source.relative_to(OUT)), source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                system_chrome_crop=crop, app_frame_bounds=bounds,
                sha256=hashlib.sha256(target.read_bytes()).hexdigest())


def contact_sheet(device, columns, thumb_width):
    files = sorted((OUT / device).glob("*.jpg"))
    thumb_height = round(thumb_width * 16 / 9)
    gap, label_height = 24, 34
    rows = (len(files) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * (thumb_width + gap) + gap,
                              rows * (thumb_height + label_height + gap) + gap), "#F5F4F8")
    d = ImageDraw.Draw(sheet)
    for i, path in enumerate(files):
        x, y = gap + i % columns * (thumb_width + gap), gap + i // columns * (thumb_height + label_height + gap)
        with Image.open(path) as source:
            sheet.paste(source.resize((thumb_width, thumb_height), Image.Resampling.LANCZOS), (x, y))
        d.text((x, y + thumb_height + 10), path.stem, font=font(17, "medium"), fill="#514762", anchor="lt")
    sheet.save(OUT / f"preview-{device}.jpg", quality=92, optimize=True)


def main():
    items = []
    for device in ("phone", "tablet"):
        scenes = SCENES
        existing = list((OUT / device).glob("*.jpg"))
        for path in existing:
            path.unlink()
        for i, scene in enumerate(scenes, 1):
            items.append(render(scene, device, i, len(scenes)))
        contact_sheet(device, 5, 260)
    first_three = Image.new("RGB", (1080, 640))
    for i, path in enumerate(sorted((OUT / "phone").glob("*.jpg"))[:3]):
        with Image.open(path) as source:
            first_three.paste(source.resize((360, 640), Image.Resampling.LANCZOS), (i * 360, 0))
    first_three.save(OUT / "preview-first-three.jpg", quality=94)
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    manifest = dict(language="ru", generated_for="RuStore", git_revision=subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
        captures="Real app on dedicated Android 8.1 emulator; fictional sample data.",
        phone_viewport="1080x1920, 360 dpi (480dp wide)",
        tablet_viewport="1200x1920, 320 dpi (600dp wide)", images=items)
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    readme = """# Dose — скриншоты для RuStore

Язык: русский. Версия приложения: 1.1.0.

## Загрузка

- `phone/`: 10 изображений для телефона, в порядке 01–10.
- `tablet/`: 10 изображений для планшета, в порядке 01–10.
- Выберите вертикальную ориентацию. Загружайте JPG только из соответствующей папки.
- Каждый файл: 1080×1920 px, 9:16, RGB, меньше 3 МБ (также меньше лимита 5 МБ).
- Для каждого выбранного типа устройств загрузите минимум 3 изображения.

`preview-*.jpg` — обзорные листы для проверки, не для загрузки в магазин.
`raw/` — исходные снимки экрана; они сохраняются отдельно от архива для загрузки.
`manifest.json` содержит тексты, размеры, контрольные суммы и происхождение каждого кадра.

Все экраны сняты с работающего приложения, с вымышленными данными. Интерфейс
не перерисован. Срезаны только системные панели Android; пропорции приложения
сохранены. Планшетные экраны сняты отдельно при ширине интерфейса 600 dp.
Публикация в консоли RuStore не выполнялась.

Требования: https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication
API-спецификация: https://www.rustore.ru/help/work-with-rustore-api/api-upload-publication-app/apk-screens-upload
"""
    (OUT / "README.md").write_text(readme)
    archive = OUT.parent / "dose-rustore-ru.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for item in items:
            bundle.write(OUT / item["file"], item["file"])
        for name in ("README.md", "manifest.json", "preview-phone.jpg", "preview-tablet.jpg", "preview-first-three.jpg"):
            bundle.write(OUT / name, name)
    print(json.dumps(dict(archive=str(archive), images=len(items),
                          largest_bytes=max(x["bytes"] for x in items)), indent=2))


if __name__ == "__main__":
    main()
