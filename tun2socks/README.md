# How To build?

## Requirements

- Go 1.22.x (MUST be exactly 1.22 due to Psiphon dependency)
- Android NDK r26b (26.1.10909125)
- Android SDK installed and available in environment (ANDROID_SDK_ROOT/ANDROID_HOME)
- JDK 17

## Option A: Build via Gradle (recommended)

From the project root:

Linux/macOS:
```bash
./gradlew :app:buildTun2SocksAar
```

Windows:
```powershell
.\gradlew.bat :app:buildTun2SocksAar
```

Output AAR: `app/libs/tun2socks.aar`

## Option B: Build via gomobile directly

From this directory (`tun2socks/`):
```sh
go run golang.org/x/mobile/cmd/gomobile init
go run golang.org/x/mobile/cmd/gomobile bind -ldflags="-w -s" -target=android -androidapi=23 -o=tun2socks.aar .
```
