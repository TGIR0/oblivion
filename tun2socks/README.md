# Oblivion native bridge

This module contains the production contract and security gates for the replacement native core.
It contains WARP and standard WireGuard adapters based on official `wireguard-go`, VWarp MASQUE
through the pinned usque adapter, reusable encrypted identity storage, an authenticated loopback
SOCKS5 server, and real tunnel health verification. Psiphon and all Psiphon chain modes return
`CORE_UNAVAILABLE`. Android keeps every mode disabled until its live release gates pass.

Public gomobile API:

```text
NewEngine(listener, socketProtector, secureStore)
ValidateConfig(configJson)
Engine.Start(configJson)
Engine.Stop()
Engine.GetStatus()
VerifyFeatureManifest(envelopeJson, publicKey, minimumSequence, now)
VerifyFeatureManifestKeyset(envelopeJson, publicKeysJson, minimumSequence, now)
```

Production `NewEngine` instances require a signed remote-policy configuration. `Start` verifies
the exact envelope, expiry, sequence, same-sequence payload hash, selected-mode entry, and
kill-switch before constructing a transport session. The accepted sequence and payload hash are
stored together through `SecureStore`.

Build and verify:

```text
go test -race ./...
go vet ./...
staticcheck ./...
govulncheck ./...
./build-aar.ps1
```

The generated AAR is written to `app/libs` and is intentionally ignored by Git.
