# LG Smart TV Remote (WebOS)

A fully functional, high-performance Android remote control application for LG WebOS Smart TVs. Built with Kotlin and OkHttp, this app supports dual-protocol communication (SSAP + Pointer Socket) for a seamless experience.

## 🚀 Features

- **Automatic TV Discovery**: Scans your local network using SSDP to find LG TVs instantly.
- **Dual-Protocol Control**:
  - **SSAP**: High-level system commands (Volume, Apps, Power).
  - **Pointer Socket**: Low-latency D-Pad navigation and mouse control.
- **Three-Tab Interface**:
  1. **Remote**: Physical remote layout with all essential buttons.
  2. **Touchpad**: Precision mouse movement and scrolling surface.
  3. **Apps**: Launch and manage installed TV apps directly from your phone.
- **Custom TV Keyboard**: Type text on your TV using a comfortable mobile keyboard interface.
- **Auto-Pairing**: Saves your client-key after the first connection for instant access later.

## 🛠 Tech Stack

- **Language**: Kotlin
- **Networking**: OkHttp (WebSockets)
- **Image Loading**: Glide
- **Architecture**: Single Activity with ViewBinding
- **CI/CD**: GitHub Actions (Auto-build APK)

## 📦 How to Build

1. Clone the repository.
2. Open in **Android Studio**.
3. Sync Gradle and click **Run**.

Alternatively, download the latest APK from the **Actions** tab in this repository after every commit.

## 📱 Permissions

- `INTERNET`: To communicate with the TV.
- `ACCESS_WIFI_STATE` & `CHANGE_WIFI_MULTICAST_STATE`: For SSDP device discovery.

## 🤝 Contributing

Feel free to open issues or submit pull requests to improve the app!

---
*Disclaimer: This is an unofficial remote and is not affiliated with LG Electronics.*
