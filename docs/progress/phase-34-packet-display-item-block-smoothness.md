# Phase 34 - Packet Display Item/Block Smoothness + Text Parsing

## Scope
Request:
- Selesaikan dulu nomor 2: `ITEM_DISPLAY` dan `BLOCK_DISPLAY` harus tidak look-at-player, ukurannya diperkecil seperti item drop, dan animasi spin/interpolation dibuat lebih halus.
- Lalu nomor 1: `TEXT_DISPLAY` harus tetap memakai parsing warna yang benar untuk output vanilla packet hologram.

## Implemented
- Updated `nms-v1_21_11/src/main/java/me/chyxelmc/mmoblock/nms/v1_21_11/NmsAdapter_v1_21_11.java`:
  - `ITEM_DISPLAY` dan `BLOCK_DISPLAY` tetap `FIXED` billboard.
  - Scale packet display diperkecil dan dipisah per tipe agar lebih natural.
  - Item/block memakai transform khusus dengan tilt ringan + spin agar mirip entity drop.
  - Transform interpolation tetap aktif untuk smooth update.
  - Rotation/spin display tetap packet-driven dan tidak ikut camera player.
  - `TEXT_DISPLAY` sekarang menerima MiniMessage, legacy section, dan `&` color codes lalu dikonversi ke vanilla component.

## Result
- Packet hologram item/block terasa lebih kecil dan tidak ikut menghadap camera.
- Text hologram packet path sekarang tidak lagi tampil raw formatting code saat menerima string legacy/mini-message yang sudah dikonversi ke legacy section.

