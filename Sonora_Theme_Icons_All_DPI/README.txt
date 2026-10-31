SONORA — THEME ICON PACK
========================

Includes 9 Sonora S-logo themes:
1. default_red         — Default Red (based on the red source app icon)
2. liquid_glass        — Liquid Glass
3. liquid_glass_clear  — Liquid Glass Clear
4. pixel_glass         — Pixel Glass
5. samsung_glass       — Samsung Glass
6. neon_violet         — Neon Violet
7. neon_cyan           — Neon Cyan
8. neon_silver         — Neon Silver
9. monochrome          — Monochrome

Each theme includes:
- master/<theme>_1024.png
- mipmap-mdpi/ic_launcher.png and ic_launcher_round.png (48 px)
- mipmap-hdpi/ic_launcher.png and ic_launcher_round.png (72 px)
- mipmap-xhdpi/ic_launcher.png and ic_launcher_round.png (96 px)
- mipmap-xxhdpi/ic_launcher.png and ic_launcher_round.png (144 px)
- mipmap-xxxhdpi/ic_launcher.png and ic_launcher_round.png (192 px)

Integration:
1. Choose a theme folder.
2. Copy its mipmap-* folders into app/src/main/res/.
3. If your project uses adaptive launcher icons (mipmap-anydpi-v26),
   keep your existing adaptive XML and point its foreground/background
   resources to the chosen artwork as appropriate. These files are
   complete square launcher artwork, not separated adaptive foregrounds.
4. Build and test on a device with both circular and squircle icon masks.

Note: These are complete raster launcher icons at all standard Android
density sizes. The master PNG is included for future resizing.
