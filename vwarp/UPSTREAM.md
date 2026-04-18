# Upstream: Vwarp

- Repository: https://github.com/voidr3aper-anon/Vwarp
- Pinned commit: `1486fc2b6c0936e617391977d8069a05ad1bb512`
- Imported via: GitHub `tar.gz` snapshot (no `.git/`)

## How to update

1. Pick a commit SHA from upstream.
2. From repo root, re-vendor into `vwarp/`:

   ```powershell
   $sha = "<commit-sha>"
   rmdir /s /q .\\vwarp
   mkdir .\\vwarp
   curl.exe -L --fail "https://codeload.github.com/voidr3aper-anon/Vwarp/tar.gz/$sha" -o - | tar -xzf - -C .\\vwarp --strip-components=1
   ```

3. Re-apply local patches (currently: `TunFd` + `tunbridge`).
4. Rebuild `tun2socks.aar`:

   ```powershell
   .\\gradlew.bat :app:buildTun2SocksAar
   ```

