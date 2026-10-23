"""Renderiza os ícones iniciais com a fonte Ionicons já usada pelo frontend.

Uso: python scripts/generate_catalog_icons.py CAMINHO_DO_FRONTEND
Requer Pillow. Não acessa o servidor nem altera imagens já existentes.
"""
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ICONS = {
    'areas/eletrica': 'flash-outline', 'areas/limpeza': 'sparkles-outline',
    'areas/pintura': 'color-palette-outline', 'areas/hidraulica': 'water-outline',
    'areas/jardinagem': 'leaf-outline', 'areas/montagem': 'hammer-outline',
    'areas/climatizacao': 'snow-outline',
    'categories/eletricista': 'flash-outline', 'categories/luminarias': 'bulb-outline',
    'categories/diarista': 'home-outline', 'categories/limpeza-pos-obra': 'sparkles-outline',
    'categories/passadoria': 'shirt-outline', 'categories/pintor': 'brush-outline',
    'categories/textura': 'grid-outline', 'categories/encanador': 'water-outline',
    'categories/desentupimento': 'construct-outline', 'categories/jardineiro': 'leaf-outline',
    'categories/podador': 'cut-outline', 'categories/montador-moveis': 'cube-outline',
    'categories/persianas': 'sunny-outline', 'categories/ar-condicionado': 'snow-outline',
    'categories/higienizacao-split': 'sparkles-outline',
}

if __name__ == '__main__':
    vendor = Path(sys.argv[1]) / 'node_modules/@expo/vector-icons/build/vendor/react-native-vector-icons'
    glyphs = json.loads((vendor / 'glyphmaps/Ionicons.json').read_text())
    font = ImageFont.truetype(str(vendor / 'Fonts/Ionicons.ttf'), 180)
    for key, name in ICONS.items():
        dest = ROOT / 'src/main/resources/catalog' / (key + '.png')
        if dest.exists():
            continue
        image = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        glyph = chr(glyphs[name])
        x0, y0, x1, y1 = draw.textbbox((0, 0), glyph, font=font)
        draw.text(((256-x1-x0)/2, (256-y1-y0)/2), glyph, font=font, fill='#0f766e')
        dest.parent.mkdir(parents=True, exist_ok=True)
        image.save(dest, optimize=True)
    print(f'{len(ICONS)} ícones iniciais disponíveis em src/main/resources/catalog.')
