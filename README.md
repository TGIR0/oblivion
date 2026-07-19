# Oblivion v8 - Unofficial Warp Client for Android

"Internet, for all or none!"

Oblivion provides secure, optimized internet access through a user-friendly Android app using Cloudflare Warp technology.

**Version 8** is a from-scratch redesign of Oblivion, purpose-built to respond to Iran's evolving filtering and deep-packet-inspection landscape. The entire tunneling infrastructure has been rebuilt from the ground up around a **next-generation core** â€” powered by the latest `sing-box`, `gVisor`, and `bepass-sdk` â€” replacing the legacy WireGuard-only implementation with a more resilient, harder-to-block stack.

![oblivion3.jpg](media/oblivion3.jpg)

## What's New in v8

- **Rebuilt from scratch**: The core networking layer was rewritten from zero rather than patched on top of the old implementation.
- **Next-Gen anti-censorship core**: Built on `sing-box` + `gVisor`, with **MASQUE (HTTP/3)** support for better resistance against modern DPI-based filtering used in Iran.
- **Smart routing**: Integrated `GeoIP`-based routing for smarter, more efficient traffic handling.
- **Wider OS support**: Compatible from Android 7.0 up through the latest Android 17 releases.

## Features

- **Secure VPN**: Modern tunneling core with `sing-box`, `gVisor`, and `bepass-sdk`.
- **Optimized Speeds**: Tuned for minimal latency even under heavy filtering conditions.
- **Advanced Split Tunneling**: Selective per-app routing with internet permission filtering.
- **Themes**: Light, Dark, and Pitch Black (OLED) modes.
- **Live Logs**: Color-coded, real-time connection logs with auto-cleanup.
- **Quick Settings Tile**: Toggle the VPN directly from your notification shade.
- **Android TV Support**: Works on Android TV interfaces as well as phones/tablets.
- **User-Friendly**: Simple, intuitive interface.

## System Requirements

- **OS support**: Android 7.0 (API 24) and up, with active support through the latest Android 17 (API 37) releases.
- **Architecture**: ARM64-v8a, armeabi-v7a, x86, and x86_64 devices.
- **Storage**: ~50 MB of free space for installation.
- **Connectivity**: An active internet connection (mobile data or Wi-Fi) is required to establish the tunnel.

## Quick Start

1. **Download**:
   - Grab the latest APK from this fork's [Releases](https://github.com/TGIR0/oblivion/releases) page, **or**
   - Build it yourself from source using the instructions below (recommended if no release build is published yet), **or**
   - Use the original upstream app from the [Google Play Store(official Version)](https://play.google.com/store/apps/details?id=org.bepass.oblivion) / [official Releases](https://github.com/bepass-org/oblivion/releases) if you don't need the v8 Iran-focused core.

   <a href="https://github.com/TGIR0/oblivion/releases">
   <img alt="Download from GitHub Releases" src="https://img.shields.io/badge/Download-Latest%20APK-blue?style=for-the-badge&logo=android" />
   </a>

2. **Install**: Enable "Install from unknown sources" for your file manager/browser if you sideloaded the APK, then install it.

3. **Connect**: Launch Oblivion and hit the switch button.

## Building the Project

### Prerequisites

- **JDK 25**
- **Android SDK**: API Level 36 (Android 16) or newer recommended
- **NDK**: 29.0.14206865 (or a compatible LTS release)
- **Go 1.22+** (for the native core components)

### Follow the steps below to build Oblivion:

1. Clone this repository:
   ```bash
   git clone https://github.com/TGIR0/oblivion.git
   cd oblivion
   ```
2. Open the project in Android Studio and let Gradle sync.
3. In Android Studio, navigate to **Build** in the menu bar.
4. Select **Generate Signed Bundle/APK...**
5. Choose **APK**, select the `release` build type, and proceed.

## Get Involved

We're a community-driven project, aiming to make the internet accessible for all â€” especially where filtering is heaviest. Whether you want to contribute code, suggest features, or need some help, we'd love to hear from you! Check out our [GitHub Issues](https://github.com/TGIR0/oblivion/issues) or submit a pull request.

## Acknowledgements and Credits

This project makes use of several open-source tools and libraries, and we are grateful to the developers and communities behind these projects. In particular, we would like to acknowledge:

### Cloudflare Warp

- **Project**: Cloudflare Warp
- **Website**: [Cloudflare Warp](https://www.cloudflare.com/products/warp/)
- **License**: [License information](https://www.cloudflare.com/application/terms/)
- **Description**: Cloudflare Warp is a technology that enhances the security and performance of Internet applications. We use it in our project for its efficient and secure network traffic routing capabilities.

### sing-box

- **Project**: sing-box
- **GitHub Repository**: [sing-box on GitHub](https://github.com/sagernet/sing-box)
- **Description**: A universal proxy platform that powers the new anti-censorship core, including MASQUE (HTTP/3) support.

### gVisor

- **Project**: gVisor
- **GitHub Repository**: [gVisor on GitHub](https://github.com/google/gvisor)
- **Description**: An application-level sandbox used to isolate and secure the network stack.

### WireGuard-go

- **Project**: WireGuard-go
- **GitHub Repository**: [WireGuard-go on GitHub](https://github.com/WireGuard/wireguard-go)
- **License**: [GNU General Public License v2.0](https://github.com/WireGuard/wireguard-go/blob/master/COPYING)
- **Description**: WireGuard-go is an implementation of the WireGuard secure network tunnel, used for parts of the secure VPN tunneling.

### bepass-sdk

- **Project**: bepass-sdk
- **Description**: Used for network optimization and censorship-circumvention capabilities.

Please note that the use of these tools is governed by their respective licenses, and you should consult those licenses for terms and conditions of use.

## License

This project is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License - see the [CC BY-NC-SA 4.0 License](https://creativecommons.org/licenses/by-nc-sa/4.0/) for details.

### Summary of License

The CC BY-NC-SA 4.0 License is a free, copyleft license suitable for non-commercial use. Here's what it means for using this project:

- **Attribution (BY)**: You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use.

- **NonCommercial (NC)**: You may not use the material for commercial purposes.

- **ShareAlike (SA)**: If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original.

This summary is only a brief overview. For the full legal text, please visit the provided link.- **Advanced Split Tunneling**: Selective app routing with internet permission filtering.
- **Themes**: Light, Dark, and **Pitch Black (OLED)** support.
- **Live Logs**: Color-coded real-time logs (Info, Warning, Error) with auto-cleanup.
- **Quick Settings Tile**: Toggle VPN directly from your notification shade.
- **Android TV Support**: Compatible with TV interfaces.
- **Legacy Support**: Works on Android 7.0+ (API 24+).

## Quick Start

1. **Download**: Grab the APK from our [Releases](https://github.com/bepass-org/oblivion/releases) page or [Google Play Store](https://play.google.com/store/apps/details?id=org.bepass.oblivion).
   <a href="https://play.google.com/store/apps/details?id=org.bepass.oblivion">
   <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" width="165" height="64" />
   </a>

2. **Connect**: Launch Oblivion and hit the switch button.

## Developer Guide

### Prerequisites
- **JDK 25**: Required for building the Android project.
- **Android SDK**: API Level 36 (Android 16) recommended.
- **NDK**: Version 29.0.14206865 (or similar LTS).

### Project Structure
- `app/`: Main Android application module.
- `tun2socks/`: Empty Go placeholder that preserves the old public API until a new core is chosen.

### Building Instructions

1. **Build APK**:
   - Open the project in Android Studio.
   - Sync Gradle.
   - Build > Generate Signed Bundle/APK > APK.
   - Select `release` build type.

### Notes for Contributors
- **Code Style**: Follow standard Android/Kotlin coding conventions.
- **Dependencies**: The `tun2socks` module is intentionally dependency-free until a replacement core is added.
- **Logs**: Use the centralized logging system in `LogActivity`.

## Get Involved

We're a community-driven project, aiming to make the internet accessible for all. Whether you want to contribute code, suggest features, or need some help, we'd love to hear from you! Check out our [GitHub Issues](https://github.com/bepass-org/oblivion/issues) or submit a pull request.

## Acknowledgements and Credits

This project makes use of several open-source tools and libraries, and we are grateful to the developers and communities behind these projects.

- **Cloudflare Warp**: For the underlying technology. [Website](https://www.cloudflare.com/products/warp/)
- **Sing-box**: The universal proxy platform. [GitHub](https://github.com/sagernet/sing-box)
- **gVisor**: Container runtime sandbox. [GitHub](https://github.com/google/gvisor)
- **WireGuard-go**: For the secure tunnel implementation. [GitHub](https://github.com/WireGuard/wireguard-go)
- **bepass-sdk**: For optimization and censorship circumvention.

## License

This project is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License - see the [CC BY-NC-SA 4.0 License](https://creativecommons.org/licenses/by-nc-sa/4.0/) for details.
