# 🪨 MMOBlock

**MMOBlock** adalah solusi terbaik untuk membuat sistem "Mining RPG" di server Minecraft kamu. Dengan plugin ini, kamu bisa meletakkan blok interaktif di mana saja yang bisa ditambang pemain, memberikan hadiah, dan muncul kembali secara otomatis.

> [!TIP]
> Cocok untuk server bertema Survival, Skyblock, atau RPG yang ingin memiliki area tambang khusus (seperti batu meteor, kristal langka, atau pohon abadi).

---

## ✨ Fitur Utama

* 🧱 **Blok Kustom:** Letakkan blok apa saja di mana saja. Tidak terbatas pada blok Vanilla.
* ⛏️ **Sistem Ketahanan:** Tentukan berapa kali sebuah blok harus dipukul sebelum pecah (Click-based mining).
* 🎁 **Hadiah Dinamis:** Berikan item, uang (vault), XP, atau jalankan perintah konsol saat blok hancur.
* 🔁 **Auto Respawn:** Blok yang hancur akan muncul kembali secara otomatis sesuai waktu yang kamu tentukan.
* 💬 **Hologram Keren:** Menampilkan nama blok, HP (progress bar), dan waktu mundur respawn di atas blok.
* 🎨 **Dukungan Model 3D:** Mendukung **ModelEngine** dan **ItemsAdder** untuk tampilan blok yang lebih realistis dan unik.
* 💾 **Penyimpanan Aman:** Data blok tersimpan rapi menggunakan database (H2, MySQL, atau Redis).

---

## 🔧 Persyaratan Sistem

| Syarat | Minimal |
|---|---|
| **Server** | [PaperMC](https://papermc.io/) (atau turunannya seperti Purpur) |
| **Java** | Versi 21 ke atas |
| **Minecraft** | 1.19.4, 1.20.4, 1.21.x (Hingga versi terbaru) |

---

## 🚀 Cara Pemasangan

1.  **Download** file `.jar` terbaru dari folder [Releases](../../releases).
2.  **Upload** file tersebut ke folder `plugins` di server kamu.
3.  **Restart** server untuk menghasilkan file konfigurasi otomatis.
4.  (Opsional) Pasang **DecentHolograms** jika ingin fitur teks melayang yang lebih ringan dan stabil.

---

## 🎮 Perintah & Izin (Commands)

| Perintah | Deskripsi | Izin (Permission) |
|---|---|---|
| `/mmoblock place <id>` | Meletakkan blok sesuai ID yang dibuat di config | `mmoblock.admin` |
| `/mmoblock remove` | Menghapus blok yang ada di depanmu | `mmoblock.admin` |
| `/mmoblock reload` | Memperbarui perubahan di file config tanpa restart | `mmoblock.admin` |

---

## 📁 Panduan Konfigurasi (Mudah Dimengerti)

Plugin ini terbagi menjadi 3 bagian utama di dalam folder `plugins/MMOBlock/`:

### 1. Folder `blocks/` (Pengaturan Blok)
Di sini kamu mengatur identitas blok. Contohnya:
- **Nama:** "Batu Kristal"
- **Waktu Respawn:** 60 detik.
- **Model:** Mau pakai blok berlian biasa atau model 3D kustom.

### 2. Folder `tools/` (Pengaturan Alat)
Di sini kamu mengatur alat apa yang bisa menghancurkan blok tersebut. 
- *Contoh:* Blok Kristal hanya bisa hancur jika dipukul pakai **Pickaxe Berlian** sebanyak 10 kali.

### 3. Folder `drops/` (Pengaturan Hadiah)
Di sini kamu mengatur apa yang didapat pemain.
- *Contoh:* Peluang 50% dapat 1 Berlian, 10% dapat 5 Berlian, atau 100% menjalankan perintah `/give`.

---

## 🛠️ Cara Membuat Blok Pertama Kamu

1.  Edit file di folder `blocks/example.yml`.
2.  Masuk ke game, berdiri di lokasi yang diinginkan.
3.  Ketik `/mmoblock place exampleEntity`.
4.  Coba pukul menggunakan alat yang sesuai di folder `tools`.

---

## 📜 Lisensi & Dukungan

- **Website:** [chyxelmc.me](https://chyxelmc.me)
- **Lapor Bug:** Silakan buka [GitHub Issues](../../issues).

*Dibuat dengan ❤️ untuk komunitas Minecraft oleh Aniko.*
