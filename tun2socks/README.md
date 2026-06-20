# Tun2socks Placeholder

The native `tun2socks`/Warp core has been removed.

This directory now keeps only the minimal Go package shape and exported API names
so a future core can be dropped in without rediscovering the Android integration
surface from scratch.

There is no gomobile/AAR build step at the moment. The Android app uses the
Kotlin placeholder in `app/src/main/java/tun2socks/` and fails fast when the VPN
core is started.

You can still validate the empty Go package with:
```sh
go test ./...
```
