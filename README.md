# WatchTogether

A lightweight, battery-efficient offline Android app that streams videos directly from one Android phone to another without internet, using Wi-Fi Direct.

## Features

- **Wi-Fi Direct Streaming** — Stream videos between devices with no router or internet required
- **Host/Viewer Model** — One device hosts and selects videos; others watch in sync
- **Synchronized Playback** — Play, pause, seek, and stop actions sync instantly across devices
- **Local Video Library** — Browse videos from internal storage and SD card with thumbnails, folders, and duration
- **Multi-Format Support** — MP4, MKV, AVI, MOV, WebM
- **Battery Efficient** — Event-driven architecture, no polling, efficient buffering, hardware-accelerated decoding
- **Material Design 3** — Modern UI with dark mode support
- **No Cloud, No Internet** — Everything stays local and private

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  WatchTogether                   │
├─────────────────────────────────────────────────┤
│  UI Layer (MVVM)                                │
│  ├── DiscoveryActivity + ViewModel              │
│  ├── VideoLibraryActivity + ViewModel           │
│  └── PlayerActivity + ViewModel                 │
├─────────────────────────────────────────────────┤
│  Network Layer                                  │
│  ├── WifiDirectManager (WifiP2pManager)         │
│  ├── VideoStreamServer (NanoHTTPD)              │
│  ├── SyncServer (NanoWSD WebSocket)             │
│  └── SyncClient (WebSocket client)              │
├─────────────────────────────────────────────────┤
│  Data Layer                                     │
│  ├── VideoRepository (MediaStore)               │
│  └── Models (VideoItem, DeviceInfo, SyncMessage)│
├─────────────────────────────────────────────────┤
│  Service Layer                                  │
│  └── StreamingService (Foreground Service)      │
├─────────────────────────────────────────────────┤
│  Playback                                       │
│  └── ExoPlayer (Media3)                         │
└─────────────────────────────────────────────────┘
```

### Key Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Device Discovery | WifiP2pManager | Discover and connect to nearby devices via Wi-Fi Direct |
| Video Streaming | NanoHTTPD | Embedded HTTP server with range-request support for video streaming |
| Playback Sync | NanoWSD (WebSocket) | Real-time sync of play/pause/seek/stop between host and viewers |
| Video Playback | ExoPlayer (Media3) | Hardware-accelerated video playback with multi-format support |
| Video Library | MediaStore API | Scan internal/external storage for videos with metadata |
| UI Framework | Material Design 3 | Modern, responsive UI with dark mode |

### How It Works

1. **Host** opens the app → discovers nearby devices or starts hosting directly
2. **Viewer** opens the app → scans for and connects to the host via Wi-Fi Direct
3. Wi-Fi Direct establishes a P2P connection (no router needed)
4. **Host** selects a video from the local library
5. The host device starts:
   - A local HTTP server (NanoHTTPD on port 8080) to stream the video file
   - A WebSocket server (NanoWSD on port 8081) for playback synchronization
6. **Viewer** connects to both servers and begins streaming
7. All playback actions (play/pause/seek/stop) are synchronized via WebSocket messages

## Project Structure

```
app/src/main/java/com/watchtogether/
├── app/
│   └── WatchTogetherApp.kt          # Application class, notification channels
├── data/
│   ├── model/
│   │   ├── DeviceInfo.kt            # Wi-Fi Direct device model
│   │   ├── SyncMessage.kt           # Sync protocol messages (JSON)
│   │   └── VideoItem.kt             # Video metadata model
│   └── repository/
│       └── VideoRepository.kt       # MediaStore video scanner
├── network/
│   ├── server/
│   │   └── VideoStreamServer.kt     # NanoHTTPD video streaming server
│   ├── sync/
│   │   ├── SyncClient.kt            # WebSocket sync client
│   │   └── SyncServer.kt            # WebSocket sync server (NanoWSD)
│   └── wifidirect/
│       ├── WifiDirectBroadcastReceiver.kt
│       └── WifiDirectManager.kt     # Wi-Fi Direct connection manager
├── service/
│   └── StreamingService.kt          # Foreground service for streaming
├── ui/
│   ├── discovery/
│   │   ├── DeviceAdapter.kt         # RecyclerView adapter for devices
│   │   ├── DiscoveryActivity.kt     # Home/device discovery screen
│   │   └── DiscoveryViewModel.kt
│   ├── library/
│   │   ├── VideoAdapter.kt          # RecyclerView adapter for videos
│   │   ├── VideoLibraryActivity.kt  # Video browser screen
│   │   └── VideoLibraryViewModel.kt
│   └── player/
│       ├── PlayerActivity.kt        # Video player screen
│       └── PlayerViewModel.kt
└── util/
    ├── NetworkUtils.kt              # IP address and URL utilities
    └── PermissionHelper.kt          # Runtime permission management
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Kotlin 1.9+
- Min SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
- Two Android devices with Wi-Fi Direct support

## Setup Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/kasiprasath/WatchTogether.git
   cd WatchTogether
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory and open it

3. **Sync Gradle**
   - Android Studio will prompt to sync Gradle — click "Sync Now"
   - Wait for all dependencies to download

4. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

5. **Run on device**
   - Connect an Android device via USB (or use wireless debugging)
   - Click "Run" in Android Studio or:
   ```bash
   ./gradlew installDebug
   ```

## Building APK

### Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

For signed release builds, configure signing in `app/build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("your-keystore.jks")
        storePassword = "your-store-password"
        keyAlias = "your-key-alias"
        keyPassword = "your-key-password"
    }
}
```

## Battery Optimization

- **Event-driven architecture** — No polling loops; uses callbacks and Flow
- **Efficient streaming** — Range-request support with 2MB chunks; streams original files without transcoding
- **Hardware decoding** — ExoPlayer uses device hardware decoders
- **Foreground service only when active** — Service starts only during streaming and stops immediately after
- **Lifecycle-aware** — Proper cleanup of sockets, services, and players on activity/service destruction
- **Coroutines** — Lightweight concurrency with structured cancellation
- **Lazy thumbnails** — Coil image loader with memory and disk caching

## Permissions

| Permission | Purpose |
|-----------|---------|
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | Wi-Fi Direct connection |
| `ACCESS_FINE_LOCATION` | Required for Wi-Fi Direct peer discovery (Android < 13) |
| `NEARBY_WIFI_DEVICES` | Wi-Fi Direct peer discovery (Android 13+) |
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_VIDEO` | Access local video files |
| `INTERNET` | Local network communication only (no cloud) |
| `FOREGROUND_SERVICE` | Streaming service notification |
| `POST_NOTIFICATIONS` | Show streaming status notification |

## Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM
- **UI**: Material Design 3, ViewBinding
- **Video Playback**: ExoPlayer (Media3)
- **Local Server**: NanoHTTPD + NanoWSD
- **Networking**: Wi-Fi Direct (WifiP2pManager)
- **Sync Protocol**: WebSocket (JSON messages)
- **Async**: Kotlin Coroutines + Flow
- **Image Loading**: Coil
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## License

This project is proprietary software. All rights reserved.
