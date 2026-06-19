# Game Booster (no-root)

## Yang beneran jalan
- **Game Mode (DND)**: blok semua notif selama main. Beneran works, pake API resmi `NotificationManager`.
- **Monitor CPU/Suhu/RAM**: overlay floating, update tiap detik, baca langsung dari `/proc/stat` dan `/sys/class/thermal`.
- **FPS app yang lagi dibuka**: baca cumulative frame count dari `dumpsys gfxinfo <package>`, dihitung deltanya tiap detik. INI BUTUH izin `android.permission.DUMP` yang cuma bisa digrant lewat ADB shell (lihat bagian Setup ADB di bawah) — gak bisa lewat dialog permission biasa.
- **Bersihin RAM**: minta sistem kill proses background app lain. Cuma kena yang statusnya "cached" (gak aktif) — app yang lagi jalan gak kena. Efeknya kecil karena Android emang udah auto-manage ini sendiri.

## Yang TIDAK ada (sengaja, biar jujur)
- Kill paksa app lain → butuh root.
- "Boost performa CPU/GPU" → klaim app booster kebanyakan di Play Store itu placebo, gak ada API buat overclock/governor tanpa root.

## Cara build dari Termux (pakai GitHub Actions, gratis)

1. Push project ini ke repo GitHub baru:
   ```bash
   cd GameBooster
   git init
   git add .
   git commit -m "init game booster"
   gh repo create game-booster --public --source=. --push
   # atau manual: git remote add origin <url repo kamu>, lalu git push -u origin main
   ```

2. Buka tab **Actions** di repo GitHub kamu (lewat browser HP) — workflow "Build APK" otomatis jalan.

3. Tunggu ~2-3 menit, build selesai → buka run yang sukses → download artifact **game-booster-debug-apk** (isinya `app-debug.apk`).

4. Install APK-nya di HP (aktifkan "Install from unknown sources" kalau diminta).

## Setelah install
- Buka app → tap "Kasih Izin Usage Access (buat FPS)" → aktifin di list yang kebuka (ini buat deteksi app apa yang lagi di-foreground).
- Tap "Aktifkan Game Mode" → izinkan akses DND di setting yang kebuka.
- Tap "Start Monitor" → izinkan "Display over other apps" di setting yang kebuka.
- Overlay bakal muncul nempel di pojok kiri atas: `FPS .. | CPU ..% | ..°C | RAM ../..MB`

## Setup ADB buat fitur FPS (sekali aja)

FPS app lain butuh `android.permission.DUMP`, dan ini cuma bisa digrant lewat ADB shell —
gak ada dialog izin biasa buat ini. Tapi bisa full dari HP sendiri, gak butuh PC:

```bash
pkg install android-tools
chmod +x setup_adb.sh
./setup_adb.sh
```

Script bakal nuntun kamu:
1. Aktifin "Wireless debugging" di Developer options
2. Pairing (sekali, pake kode 6 digit)
3. Connect ke device sendiri (`127.0.0.1` style, tapi lewat WiFi LAN)
4. Auto-grant `android.permission.DUMP` ke Game Booster

Selama belum di-setup, field FPS bakal nunjukin `no-perm` — bukan bug, emang nunggu izin itu.

