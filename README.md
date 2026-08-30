# Offline QR Transfer

Transfer files between two Android devices with **zero network connectivity** — no Wi-Fi, no Bluetooth, no NFC, no internet, no cloud. Just point one phone's camera at the other's screen.

The sender splits a file into chunks, encodes each chunk into a QR code, and animates through them on screen. The receiver's camera scans the sequence in real time, verifies each chunk, and reassembles the original file — entirely offline, entirely on-device.

## How it works

**Sender**
1. Pick any file.
2. The app splits it into fixed-size chunks.
3. Each chunk becomes a QR code, animated in sequence at an adjustable speed.

**Receiver**
1. Open the camera scanner.
2. Point it at the sender's screen.
3. Chunks are verified (checksum) and reassembled automatically as they arrive.
4. The completed file is saved to device storage, with a preview shown for images and text files.

## Features

- 100% offline — no `INTERNET` permission requested, ever
- Custom QR packet protocol (versioned, checksummed, session-scoped)
- Base45 encoding for QR-optimized data density
- Animated QR streaming with adjustable transfer speed (Slow / Medium / Fast / Ultra)
- Live camera scanning with on-screen alignment guide
- Real-time scan feedback ("Scanning…", "Chunk Found!")
- Transfer speed and ETA estimation
- Pause / Resume / Cancel on both sender and receiver
- Resume support if the receiver app is closed mid-transfer
- File preview (images and text) before/after save
- Transfer history log (Room database)
- Per-chunk checksum verification (CRC32) to reject corrupted frames

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Camera | CameraX |
| QR generation/scanning | ZXing |
| Concurrency | Kotlin Coroutines |
| Persistence | Room |
| File access | Storage Access Framework |

## Project structure

```
app/src/main/java/com/example/qrcode/
├── MainActivity.kt          # Navigation + Sender screen
├── ReceiverScreen.kt         # Camera scanning + reassembly UI
├── HistoryScreen.kt          # Transfer history list
├── protocol/                 # Core packet encoding/decoding
│   ├── QrPacket.kt
│   ├── PacketCodec1.kt
│   ├── Base45.kt
│   └── QrFrameCodec.kt
├── transfer/                  # File chunking, QR rendering, scanning, reassembly
│   ├── FileChunker.kt
│   ├── QrImageGenerator.kt
│   ├── QrAnalyzer.kt
│   ├── TransferReceiver.kt
│   └── FilePreviewDetector.kt
└── history/                   # Transfer history (Room)
    ├── TransferRecord.kt
    ├── TransferDao.kt
    └── AppDatabase.kt
```

## Getting started

### Prerequisites
- Android Studio (latest stable)
- Two physical Android devices (recommended — camera-to-screen QR scanning does not work reliably on emulators)
- minSdk 28+

### Build & run
```bash
git clone https://github.com/<your-username>/offline-qr-transfer.git
```
1. Open the project in Android Studio.
2. Let Gradle sync (downloads CameraX, ZXing, Room, and Compose dependencies).
3. Connect a device via USB with USB debugging enabled.
4. Run the app (`Run ▶`) on two separate devices.
5. On Device A: **Send File** → pick a file.
6. On Device B: **Receive File** → grant camera permission → point at Device A's screen.

### Running tests
Unit tests cover the core protocol (packet encode/decode round-trip, chunking, checksum verification):
```bash
./gradlew testDebugUnitTest
```

## Roadmap

- [ ] AES-256 encryption with password-protected transfers
- [ ] Full-file SHA-256 verification (in addition to per-chunk CRC32)
- [ ] Secure mode (block screenshots) + biometric gate before receiving
- [ ] Missing-chunk recovery / smart replay
- [ ] Optional ZIP compression for multi-file and folder transfers
- [ ] Offline text/contact/business-card sharing
- [ ] Cross-platform support (iOS, desktop)

## Limitations

- Transfer speed depends on screen brightness, camera quality, and ambient lighting
- Large files require many QR frames and proportionally longer transfer times
- Best used with two dedicated devices in good lighting, held steady during transfer

## License

Add your chosen license here (e.g. MIT, Apache 2.0).
