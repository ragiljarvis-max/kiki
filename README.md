# KIKI 🐱

**K**awaii **I**nter-device **K**ommunication **I**nterface

Transfer file lintas device tanpa internet — Android ⇄ Desktop (Windows/Linux), via LAN.

Basis: [tFileTransfer](https://github.com/Tans5/tFileTransfer) (Apache-2.0), fork `m91857687-bot/tFileTransfer` — versi game dihapus.

## Fitur

- **Android**: WiFi Direct (P2P), UDP broadcast, QR scan connect, hotspot, web share, APK extractor, cleaner, vault, riwayat transfer
- **Desktop (Windows/Linux)**: UDP broadcast discovery, remote browse, kirim/terima file, download folder `~/Downloads/KIKI`
- Protokol sama (Netty TCP/UDP + Moshi) → Android ↔ Desktop kompatibel

## Build

Butuh JDK 21 (penuh, dengan jmods — mis. Temurin) + Android SDK (untuk `:app`).

```bash
# Android APK (arm64-v8a)
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-arm64-v8a-release.apk

# Desktop portable zip (semua OS)
./gradlew :desktop:packagePortableZip
# → desktop/build/dist/KIKI-portable.zip
```

Signing release Android: set env `KIKI_KEYSTORE`, `KIKI_KEYSTORE_PASSWORD`, `KIKI_KEY_ALIAS`, `KIKI_KEY_PASSWORD` — tanpa env, fallback ke debug keystore.

## Struktur

```
kiki/
  net/      core JVM: Netty + transferproto (broadcast/fileexplore/filetransfer/qrscan/p2p)
  app/      Android (Compose + ViewBinding)
  desktop/  Compose Multiplatform JVM (jpackage)
```

## CI

`.github/workflows/release.yml`:
- `android` — APK arm64-v8a
- `windows-portable` — zip portable (bundle runtime)
- `linux-appimage` — build di container `ubuntu:20.04` (glibc ≥ 2.31), output AppImage + portable zip

## Lisensi

Apache-2.0, lihat [LICENSE](LICENSE).
