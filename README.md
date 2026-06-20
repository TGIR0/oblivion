# Oblivion - Unofficial Warp Client for Android

"Internet, for all or none!"

Oblivion provides secure, optimized internet access through a user-friendly Android app using Cloudflare Warp technology.
It uses a **Next-Gen Core** powered by the latest `sing-box`, `gvisor`, and `bepass-sdk`, specifically engineered for censorship circumvention in restrictive environments like Iran.

![oblivion3.jpg](media/oblivion3.jpg)

## Key Features

- **Advanced Anti-Censorship Core**: Built on the latest `sing-box` and `gvisor` network stacks with **MASQUE (HTTP/3)** support capabilities.
- **Smart Routing**: Integrated `GeoIP` support for intelligent traffic routing.
- **VPN Service Shell**: Android `VpnService` integration is kept in place while the native tunnel core is reset.
- **Optimized Performance**: Low latency, battery-efficient, and minimal resource usage.
- **Advanced Split Tunneling**: Selective app routing with internet permission filtering.
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
