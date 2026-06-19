#!/data/data/com.termux/files/usr/bin/bash
# setup_adb.sh — sekali jalan buat ngasih izin DUMP ke Game Booster
# lewat ADB wireless debugging (gak butuh PC, gak butuh root)

set -e

PKG="com.atomic.gamebooster"

echo "=== Setup ADB lokal buat Game Booster ==="
echo ""
echo "Langkah manual dulu (sekali aja):"
echo "1. Buka Settings > System > Developer options"
echo "   (kalau belum ada: Settings > About phone > tap 'Build number' 7x)"
echo "2. Aktifkan 'Wireless debugging'"
echo "3. Tap 'Wireless debugging' > 'Pair device with pairing code'"
echo "   Catat: IP:PORT pairing dan 6 digit kode yang muncul"
echo ""

if ! command -v adb &> /dev/null; then
    echo "Install android-tools dulu..."
    pkg install -y android-tools
fi

read -p "Masukin IP:PORT buat PAIRING (contoh 192.168.1.5:37251): " PAIR_ADDR
read -p "Masukin 6-digit pairing code: " PAIR_CODE

adb pair "$PAIR_ADDR" "$PAIR_CODE"

echo ""
echo "Sekarang balik ke layar 'Wireless debugging' utama,"
echo "catat IP:PORT yang beda (buat CONNECT, bukan pairing)."
read -p "Masukin IP:PORT buat CONNECT (contoh 192.168.1.5:40123): " CONNECT_ADDR

adb connect "$CONNECT_ADDR"

echo ""
echo "Ngasih izin DUMP ke Game Booster..."
adb shell pm grant $PKG android.permission.DUMP

echo ""
echo "Selesai! Cek statusnya:"
adb shell dumpsys package $PKG | grep -A2 "android.permission.DUMP" || true

echo ""
echo "Catatan: kalau HP restart, 'Wireless debugging' kemungkinan mati lagi"
echo "tapi izin DUMP yang udah digrant biasanya TETEP nempel (gak perlu pairing ulang)."
echo "Kalau ternyata ke-reset, tinggal jalanin script ini lagi (skip pairing,"
echo "langsung connect aja kalau device masih kepairing)."
