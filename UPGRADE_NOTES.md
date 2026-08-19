# Upgrade Notes

This file records dependency/toolchain upgrades and any required rollbacks or workarounds.

## 2026-08-19 - WarpPlus experimental bounded cover-traffic prototype

- `warpplus/internal/hardening/noise.go` adds validated `OFF`, `LIGHT`, `BALANCED`, and
  `AGGRESSIVE` budgets for optional cover traffic. The default is `OFF`; packet count, size,
  delay, and handshake delay are hard-bounded and unknown profiles fail closed.
- `warpplus/internal/warpcore/noise_bind.go` applies the selected profile only to the first
  WireGuard send path: pre-handshake cover packets precede initiation, and post-handshake cover
  packets precede the first data packet. The underlying protected bind remains the owner of all
  sockets. This is an experimental transport seam, not a replacement for the planned controlled
  AmneziaWG fork patch and not an implementation of Aether's protocol encoders.
- Android carries `noiseProfile` in `TunnelConfigV2`, defaulting to `OFF`; the internal-only
  `USERSETTING_noise_profile` storage/intent key can select a profile for field experiments, but
  no Settings UI enables it.
- The profile values are temporary internal test parameters informed by the Aether study. The
  current WarpPlus module remains marked AGPL-3.0-only; this change does not establish the
  clean-room, license-independent Rust rewrite target.
- Verification: `go test -race ./...`, `go vet ./...`, `:app:testOssDebugUnitTest`, and a fresh
  `app/libs/warpplus.aar` build passed.

## 2026-08-17 — W4 stability watchdog: anomaly-triggered teardown and rebuild

- **`warpplus/internal/warpcore/watchdog.go`** — stability inspections run only at existing
  wake boundaries (accepted SOCKS connections via a new `SocksServer.SetConnectionHook`, and
  engine `GetStatus()` queries): no timers, no resting goroutines, stats fetches rate-limited
  to one per 5 s. Anomaly rules from device stats: stale handshake (>180 s) while
  transmitting, rx stall (zero received bytes over a ≥15 s window with traffic in flight),
  and rekey storm (handshakes advancing with flat rx twice in a row). Idle silence never
  escalates.
- **Fail-closed response**: any anomaly — including non-error irregularities — tears the whole
  data plane down and rebuilds from endpoint discovery (Start refactored into `establish()`;
  rebuild on a gated one-shot goroutine, rate-limited to one per 10 s; failed rebuilds leave
  the session down). Anomalies are recorded as `telemetry.EventWatchdogAnomaly` with stable
  codes (`1` stale, `2` rx stall, `3` rekey storm) and flushed through the engine log.
- Tests: parse matrix, rule matrix (healthy/idle/stale/stall/window edges), consecutive-strike
  storm counting with reset, session-level teardown+rebuild, inspection rate limiting,
  telemetry flush, rebuild-failure fail-closed. Full `go test -race ./...` green;
  `go vet`/`gofmt` clean. No engine/AAR contract or Android wire change.

## 2026-08-17 — Warp/Warp-on-Warp program started: field telemetry (W3) shipped

New standing objective recorded in `docs/WARP_WOW_PLAN.md` (complete Warp + WoW, efficiency
above all, absolute leak sensitivity, teardown-and-rebuild on any anomaly, dense battery-free
logging, DPI learning, Amnezia anti-DPI mode, best-values profiling).

- **`warpplus/internal/telemetry`** — fixed-size (4096) event ring with stable integer event
  IDs, no hot-path formatting, no timers, no goroutines: `Record` measures 30 ns/op with
  **0 allocations**; `Flush` batches through the existing engine log callback. Drop-oldest
  semantics keep memory bounded for days-long field tests.
- **warpcore wiring** — probe starts/outcomes (healthy probes carry health-check latency in
  ms), observed failure signatures, handshake-policy switches, candidate commits, session
  failures, and stops are recorded; flushes happen only at state transitions (commit, failure,
  cancel, stop) so the device wakes exactly when the engine already runs. Endpoint addresses
  are never recorded (leak policy); the attempt index correlates with the engine log.
- AmneziaWG feasibility verified: the pinned warp-plus fork vendors stock wireguard-go (no
  Amnezia UAPI) — the anti-DPI mode is staged as a controlled patch set (W5) in the plan.
- Tests: ring clamps, ordering, drop-oldest accounting, snapshot non-destructiveness,
  concurrent recording, session-level flush flow. `go test -race ./...` green.

## 2026-08-17 — WarpPlus hardening Layer 5: handshake-level resilience

Executed per `docs/HARDENING_ROADMAP.md`; upstream re-check on this date found usque
(`6aa03fc`) and Aether (`e0b1d146`) unchanged.

- **`hardening.HandshakePolicy`** — evidence-derived handshake profiles: baseline (250 ms
  status poll, 25 s keepalive, 15 s inner budget, no MTU clamp) latches to the degraded
  profile after a stall (125 ms poll, 15 s keepalive, 20 s budget, 1280-byte DPI-resilient
  MTU ceiling). `ValidateHandshakePolicy` enforces absolute bounds (probe [10ms,1s], budget
  [0,2m], keepalive [10,120], ceiling ≥ IPv6 minimum) and fails closed at Start.
- **warpcore plumbing** — the transport `Up` interface carries the policy: `waitHandshake`
  poll pacing, `persistent_keepalive_interval` from the policy into the device IPC, and
  `ClampMTU` on the netstack MTU. The session keeps failure-signature memory across probes
  (atomic) so the degraded profile stays latched while stalls persist; a zero policy inside
  the transport falls back to baseline.
- Level-5 invariants hold the Level-4 envelope; `MaxSupportedLevel = 5`. Tests: policy
  matrix, validation escapes, clamp matrix, session-level latch (baseline → stall →
  throttled). Benchmarks: `BenchmarkHardeningLadder/level5/*`. Full `go test -race ./...`
  green; `go vet`/`gofmt` clean. No engine/AAR contract or Android wire change.

## 2026-08-17 — WarpPlus hardening Layer 4: address-family and port-space coverage

Executed per `docs/HARDENING_ROADMAP.md`; the IPv6 technique is lifted directly from the
archived Aether seed list.

- **`hardening.FamilyPolicy`** — DUAL_STACK (default; interleaves IPv4 samples with their
  derived IPv6 siblings), IPV4_ONLY (Layer-1 behavior), IPV6_ONLY. Single-family modes fail
  closed when the allowlist lacks that family's prefixes; seeds follow the family policy while
  explicit endpoints (custom, last-good, registered) keep their own validation.
- **Edge-encoded IPv6 derivation** — `2606:4700:d0::a29f:c602 ↔ 162.159.198.2` in the archived
  seed list revealed that Cloudflare embeds the IPv4 edge bytes in the low 32 bits of the warp
  IPv6 hosts. `sampleV6Candidate` embeds each sampled v4 edge into both official warp /64s, so
  derived v6 candidates track real edges instead of random noise; containment is asserted on
  every draw. On IPv6-hostile networks the Level-2 adaptive policy demotes the failing /64 and
  rotation continues over IPv4 — family failover composes with the existing mechanisms.
- **Port rotation widened** from 8 to 16 official WARP ports (all inside `WarpPorts()`).
- **Level-4 invariants** hold the Level-3 envelope (32 attempts / 20 s / 300 s; monotone by
  equality). `MaxSupportedLevel = 4`; the warpcore session now defaults to the Level-4
  envelope. No engine/AAR contract or Android wire change (family policy is engine-internal).
- **Tests** — family mixes, derivation encoding with the upstream anchor, containment across
  128 rounds × 3 policies, family fail-closed on missing prefixes, v6 /64 neighborhood
  demotion, rotation width, envelope monotonicity. Full `go test -race ./...` green;
  `go vet`/`gofmt` clean.
- **Benchmarks** — `BenchmarkHardeningLadder/level4/*` (dual-stack candidate set, isolated v6
  edge derivation, family-failover reorder where every v6 candidate blackholes).

## 2026-08-17 — WarpPlus hardening Layer 3: network-condition scan profiles + coordinated probe pool

Executed per `docs/HARDENING_ROADMAP.md`; ports the archived Aether profile/strategy tables
(aethercore `profileFor` + scanner `strategy.go`) into the shipping engine.

- **`hardening.ScanProfile`** — TURBO (concurrency 4, early exit on the first success, 45 s
  overall), BALANCED (default: concurrency 2, quiet period, target 3 successes, 120 s),
  THOROUGH (32 attempts across the full 300 s envelope, no target), STEALTH (strictly serial,
  20 s per attempt, 180 s). Budgets re-tuned to WireGuard handshake scale; the four-class shape
  follows upstream. `ValidateScanProfile` fails closed on any escape from the Level-3 envelope
  (attempts 32 / per-attempt 20 s / overall 300 s, concurrency [1,8], early-exit/target
  coupling, quiet-window containment). `MaxSupportedLevel = 3`.
- **Coordinated probe pool in warpcore** — `Concurrency` workers share the now thread-safe
  `hardening.Selector`, probe each candidate (bring-up + timed end-to-end health check) under
  the per-attempt timeout, and feed a collector through a proceed-gated protocol: no probe
  starts until the collector has processed the previous results, so EarlyExitFirst and
  quiet-period targets stop probing deterministically. The quiet window is evidence-based
  (arms only after a healthy candidate exists); the fastest healthy candidate wins and loser
  transports are torn down. Overall-deadline expiry now reports a cancellation error before
  the exhaustion error. No engine/AAR contract or Android wire change (scan mode is
  engine-internal; warpcore `Config` gained only the optional `ScanProfile` field).
- **Tests** — profile matrix and envelope-escape table, evidence-based quiet rule, concurrent
  selector budget/no-repeat under 4 workers, session-level early-exit bounding, target-based
  fastest-candidate selection, budget consumption through failures, healthy-start teardown
  accounting. Full `go test -race ./...` green; `go vet`/`gofmt` clean.
- **Benchmarks** — `BenchmarkHardeningLadder/level3/*` (profile validation, 4-worker
  concurrent selector pool, quiet-period decision loop); the ratchet test forces every shipped
  level to register its set.
- Fixed two concurrency defects found by the new tests: a buffered result channel let workers
  race ahead of the early-exit decision (now unbuffered + proceed gates), and overall-deadline
  expiry was reported as plain exhaustion (now an explicit cancellation).

## 2026-08-17 — WarpPlus hardening Layer 2: adaptive selection policy

Executed per `docs/HARDENING_ROADMAP.md`; ports the archived masquecore (VWarp) three-class
policy structure into the shipping engine.

- **`hardening.PolicyMode`** — STANDARD (fixed order, flat backoff), ADAPTIVE (signature
  reactive; the default), FOCUSED (the TCP_ONLY analogue: pinned address with port rotation
  only, auto-selected when the user forces an endpoint). Unknown modes fail closed.
- **`hardening.Selector`** — owns the try-list and adapts it inside the level budget:
  blackhole demotes the whole routing neighborhood (/24 IPv4, /64 IPv6), stall demotes the
  exact address+port pair, unhealthy promotes same-address port variants (allowlist-contained,
  deduplicated, never budget-extending). Backoff escalates 200 ms → 2 s and resets after an
  unhealthy report.
- **warpcore classification** — `waitHandshake` now returns a typed `HandshakeTimeoutError`
  carrying the observed `rx_bytes`, which distinguishes a silent blackhole from a stalled
  handshake; health failures report their own signature. The discovery loop applies the
  selector's backoff through a cancellable wait seam.
- **Level-2 invariants** — 12 attempts / 15 s per attempt / 150 s overall, monotone over
  Level 1 (LadderMonotonic + test). `MaxSupportedLevel = 2`.
- **Fine-grained tests** — classification matrix (typed/wrapped/deadline errors), mode
  resolution, neighborhood vs exact-pair demotion, port-variant promotion containment,
  backoff escalation/cap/reset, focused rotation on the pinned address, budget and no-repeat
  guarantees, session-level backoff application, fail-closed unknown policy. Full
  `go test -race ./...` green; `go vet`/`gofmt` clean.
- **Benchmarks** — `BenchmarkHardeningLadder/level2/*` (12-budget candidate set,
  adaptive reorder under mixed blackhole/stalled/unhealthy/unknown signatures, focused port
  rotation); the ratchet test requires every shipped level to register its set.
- No engine/AAR contract change and no Android wire change (the policy is engine-internal;
  warpcore `Config` gained only the optional `Policy` field).

## 2026-08-16 — WarpPlus hardening Layer 1: bounded endpoint discovery (lifetime ladder started)

Executed per `docs/HARDENING_ROADMAP.md` after re-verifying the latest usque (`6aa03fc`) and
Aether (`e0b1d146`) upstream heads and studying their archived structures
(`docs/research/upstream-techniques.md` holds the technique→disposition mapping).

- **New package `warpplus/internal/hardening`** — the lifetime hardening ladder:
  `Level`/`Invariants` with fail-closed unknown levels, `LadderMonotonic` (weakening a shipped
  level is a test failure), and allowlist-contained candidate generation
  (custom → last-good → registered → seeds → sampled, deduplicated, prefix-rotated,
  crypto/rand-entropy, port rotation inside `WarpPorts()`). Every candidate source — including
  the persisted last-good value — is validated against the warp-plus prefixes/ports before any
  socket opens (poison-resistant).
- **warpcore discovery loop** — `Start` now rotates bounded candidates: per-attempt timeout and
  overall deadline from the L1 invariants (8 × 15 s within 90 s); transport bring-up failures
  rotate candidates; health failures rotate candidates; SOCKS configuration/bind failures abort
  immediately (endpoint rotation cannot fix them); the committed SOCKS/transport bind to the
  session context so the discovery deadline can never kill a healthy tunnel. The winning
  endpoint is cached under `warp.last_good.v1` and tried first on the next start.
- **Fine-grained tests**: hardening (invariants ladder, containment over 256 random rounds,
  poison resistance, determinism under replayed entropy, budget clamps, entropy exhaustion) and
  warpcore discovery lifecycle (retry-and-cache, last-good-first, single-attempt custom,
  exhaustion reporting, health rotation, overall-deadline cancellation). Full module
  `go test -race` green; `go vet` and `gofmt` clean.
- **Benchmark hardness ladder**: `BenchmarkHardeningLadder/level1/*` (candidate set, 32-budget
  set, selection under 0/50/90 % failure). `TestBenchmarkLadderCoversShippedLevels` makes
  shipping a level without benchmarks impossible; lowering an existing benchmark's difficulty is
  a documented policy violation.
- No engine/AAR contract change and no Android wire change: the discovery layer is internal to
  warpcore (config gained only the optional `LastGoodKeyRef`, defaulting internally).

## 2026-08-16 — Project consolidated onto hev-socks5-tunnel; Tun2socks module removed

The whole project now runs on one tunnel data plane and two cores, per the reduction plan:

- **hev-socks5-tunnel is the sole TUN data plane.** The gomobile module formerly known as
  `tun2socks/` was renamed to **`warpplus/`** (module `github.com/bepass-org/oblivion/warpplus`,
  Kotlin package `warpplus`, AAR `app/libs/warpplus.aar`, Gradle/proguard/manifest references all
  updated). The name "tun2socks" no longer appears outside `attic/` and history.
- **Removed cores:** Aether (`aethercore`), VWarp/usque MASQUE (`masquecore`), WireGuard user
  profiles (`wgcore`), the endpoint `scanner`, `meekcore`/`mitmtest` leftovers, the
  `protectednet` WireGuard bind, `wireguard_keys.go`, the `fronts` manifest field
  (PSIPHON_FRONTED-only), and all chain modes. Go `go.mod` dropped the `usque` dependency and
  replace; indirect pins were re-resolved by `go mod tidy`.
- **Remaining cores:** `WARP` is the enabled default (warp-plus control plane + its userspace
  WireGuard stack; Cloudflare terms consent now applies to WARP via `CloudflareTosPolicy` and
  `USERSETTING_cloudflare_terms_accepted_version`); `PSIPHON` stays declared, disabled, and
  `CORE_UNAVAILABLE` in the engine until integration is authorized.
- **Removed cores are archived, not lost:** full sources, patches, and a reuse plan live in
  `attic/` (see `attic/README.md`) — scanner sampling, last-good reconnect, adaptive fallback
  racing, and profile-sanitization structures are earmarked for future WarpPlus hardening.
- **Android migration:** `CORE_SCHEMA_VERSION=4` collapses persisted removed-core selections to
  WARP and deletes `USERSETTING_aether_*`/`USERSETTING_vwarp_policy`; `VpnCoreType.fromInt`
  independently fails over unknown ids to WARP. Aether endpoint allowlist and
  `saved_endpoints_aether` were dropped; the endpoint sheet is single-policy (WARP ranges/ports).
- **Native pins:** `core-upstreams.json` and `verify-core-upstreams.ps1` reduced from six to four
  upstreams (warp-plus, wireguard-go, hev-socks5-tunnel, go-socks5); `native/usque` and
  `native/aether` moved to `attic/native/`.
- **Docs:** ARCHITECTURE/CORE_UPSTREAMS/PRODUCTION_GATES/THREAT_MODEL/SUPPLY_CHAIN/
  INCIDENT_RESPONSE/THIRD_PARTY_NOTICES rewritten for the two-core layout; the VWarp release plan
  moved to `attic/docs/`.
- **Evidence:** `go test -race ./...` + `go vet` + `gofmt` green on the reduced module; fresh
  `warpplus.aar` built (34.5 MB vs the old 47.9 MB tun2socks AAR); both flavor unit suites,
  Spotless, Detekt (zero findings), and `lintOssDebug` pass; `verify-core-upstreams.ps1` verifies
  the four pins.

## 2026-08-16 — VWarp release-plan Phase 1 executed (core remains UI-gated)

Executed against docs/VWARP_RELEASE_PLAN.md; `VWARP.isEnabled` intentionally stays false.

- **VWarpPolicy selector landed behind the UI gate**: `USERSETTING_vwarp_policy` storage key,
  service wiring into `TunnelConfigV2.vwarpPolicy` (fail-safe ADAPTIVE default), a Settings
  row visible only when VWARP is the selected core, localized strings (en/fa/ru/tr/zh), and
  `VWarpPolicySerializationTest` pinning the Kotlin wire names to the Go engine constants
  (STANDARD/ADAPTIVE/TCP_ONLY under JSON field `vwarpPolicy`).
- **`internal/scanner` direct tests added** (`scanner_test.go`): strategy table invariants,
  maxCandidates clamping, deterministic candidate generation (xorshift reader; the first
  pattern-reader version could stall the dedup loop with a repeating 256-byte period),
  seed/port ordering, IP-family filtering, upstream-range containment, sampler boundaries.
- **`internal/masquecore` coverage 85.5% → 93.3%** via `session_lifecycle_test.go`. Start
  gained two test seams with unchanged production defaults (`launchTransport`,
  `verifyHealth`), enabling deterministic tests of the STANDARD failure path, the ADAPTIVE
  HTTP/3→HTTP/2 fallback (asserting the second launch uses HTTP/2), and the healthy
  loopback-SOCKS lifecycle.
- **Aether re-pinned to upstream latest `e0b1d146`** (v1.6.0 line; 27 commits ahead of the
  old pin). Ported lists aligned to that commit's `aether/src/prober.rs`: scanner/aethercore
  gained seeds 162.159.197.3 and 162.159.197.1, port 4500, and the full 14-range IPv4 pool;
  aethercore's positional `masquePrefixes[:5]` slice replaced with a named IPv4 list; the
  Android manual-endpoint allowlist (`TunnelEndpointPolicy`) widened to the same upstream
  superset including 2606:4700:0102::/48. `EndpointPolicyTest` extended with the new ranges
  and neighbouring rejections. All six pinned cores are now at their upstream heads.
- **Whole Go tree normalized to LF/gofmt**: the 2026-07-19 Windows edit had left every
  tun2socks source CRLF (the same root cause as the usque patch corruption); `gofmt -w`
  restored canonical formatting and line endings across the module.
- Waiver assessment for the VWarp path recorded in the plan (LINT-FGS-VPN-001 and
  DETEKT-V7-COMPOSE-001 are the only applicable waivers, both justified and expiring).

## 2026-08-16 — VWarp core advanced to usque upstream latest + release plan started

- usque pin moved from tag `v4.2.0` (`0fa6da9e`) to **upstream latest** `refs/heads/main`
  (`6aa03fc`, quic `PROTOCOL_VIOLATION` fix using `quic.Transport{ConnectionIDLength: 20}`).
  The three controlled patches apply byte-identically on the new base (upstream fix touches
  `connectTunnelHTTP3`/`getOrCreateClientConn`, disjoint from our hunks) and are compatible
  with the injected `SocketFactory` UDP conn.
- Verified end to end at the new pin: `prepare-usque.ps1` (reset, patch, `go test -race ./api`,
  `go vet`), full `buildNativeCore` (`go test -race ./...` green including `masquecore`,
  fresh 4-ABI AAR 47.9 MB), and the whole Android verification chain (`assembleOssDebug`,
  both flavor unit suites, Lint, Detekt, Spotless). APK packages `libgojni` + `libhev-socks5-tunnel`
  for all four ABIs.
- Upstream freshness audited for all six pinned cores: warp-plus, usque, wireguard-go,
  hev-socks5-tunnel, go-socks5 are at their latest upstream commits; **aether has drift**
  (locked `db287a8e`, remote `e0b1d146`) and is recorded as a standing blocker in
  docs/VWARP_RELEASE_PLAN.md Phase 1.
- New long-term enablement plan: docs/VWARP_RELEASE_PLAN.md — absolute 10/10 constitution
  (zero warnings, zero outdated, evidence-only gates, weekly re-checks, kill-switch on first
  violation).

## 2026-08-16 — VWarp/HevSocks core pass (build unblocked, supply chain restored)

### Root cause of the buildNativeCore failure
- All three `native/usque/*.patch` files had been rewritten with **CRLF line endings** (2026-07-19
  Windows edit), so their sha256 no longer matched `core-upstreams.json` pins and `git apply`
  also failed. Proven not content drift: after LF normalization the hashes are byte-identical to
  the pinned values (`2846bc1a…`, `143db47f…`, `3dc558a8…`) and the patch blob-index chains
  (`masque 1f690e4→7735307`, `tunnel 5f06187→07c3e37→04dd766`, `cloudflare 850c3fc→f9b84c0`)
  match the pinned usque commit `0fa6da9e` exactly.
- The patch files were restored to their pinned LF bytes; no lock changes were needed.

### Verification evidence (official scripts, full pipeline)
- `native/verify-core-upstreams.ps1`: "Verified local pins for 6 native cores."
- `native/usque/prepare-usque.ps1`: fresh reset to `0fa6da9e`, clean sequential patch apply,
  `go mod verify`, `go test -race ./api`, `go vet ./api` — all pass.
- `:app:buildNativeCore`: warp-plus `f70ea7e4` re-verified, **`go test -race ./...` green for the
  whole module including `internal/masquecore` (VWarp)**, gomobile bind produced a fresh
  `app/libs/tun2socks.aar` (47.9 MB, 4 ABIs).

### VWarp + HevSocks status after review
- Go side complete: `masquecore` implements enrollment/identity, endpoint selection,
  HTTP/3 transport via patched usque `MaintainTunnel` with `SocketFactory`-protected sockets,
  HTTP/2 fallback under ADAPTIVE policy, TCP_ONLY policy, tunnel health verification, and the
  authenticated loopback SOCKS server; `engine_v2` wires `ModeVWarpMasque` with all three
  policies and the remote-policy gate.
- Android side complete: `OblivionVpnService` starts the engine, then HEV
  (`hev.htproxy.TProxyService`) with the loopback SOCKS address, per-session credentials, and the
  TUN fd; a matching R8 keep rule now protects the JNI bridge (added in the Android pass).
- `VWARP` remains UI-gated (`isEnabled=false`) per docs/PRODUCTION_GATES.md — implementation
  completeness is not release authorization.
- Follow-up when VWarp gates pass: expose the `VWarpPolicy` (STANDARD/ADAPTIVE/TCP_ONLY) setting
  in Settings (currently fixed to the ADAPTIVE default); `internal/scanner` still has no direct
  unit tests (covered indirectly today).

## 2026-08-16 — Full Android-side modernization pass

### Upgraded (all verified against live Maven/Google Maven metadata via `version_audit.ps1`)
- **Gradle:** 9.6.1 → **9.7.0** (wrapper)
- **AGP:** 9.3.0 → **9.3.1**
- **KSP:** 2.3.10 → **2.3.11**
- **Compose BOM:** 2026.06.01 → **2026.08.00** (Compose 1.11.4 → 1.12.0, material3 1.4.0)
- **OkHttp:** 5.4.0 → **5.5.0**
- **Okio:** 3.17.0 → **3.18.1**
- **MMKV:** 2.4.0 → **2.4.1**
- **Detekt:** 2.0.0-alpha.5 → **2.0.0-alpha.6**
- **Spotless:** 8.8.0 → **8.9.0**
- **Dependency Analysis:** 3.17.0 → **3.18.0**
- **CycloneDX:** 3.3.0 → **3.4.1**
- Kotlin 2.4.10, Hilt 2.60.1, Lifecycle 2.11.0, Navigation 2.9.8, Glide 5.0.9, Coroutines 1.11.0
  already latest stable and unchanged.

### Supply chain
- `gradle/verification-metadata.xml` regenerated for Gradle 9.7.0's kotlin-dsl 6.7.3 and all new
  versions; `org.apache:apache:39` pinned by cross-verified sha256 (local cache vs independent
  Maven Central download) because its signing key sits in the ignored-keys list on this network.
- `app/gradle.lockfile` re-persisted with `--write-locks` under STRICT locking.
- `buildSrc/build` artifacts untracked from git (already covered by `.gitignore`).

### Android platform compliance (documented APIs)
- VPN foreground service migrated from `specialUse` (+ subtype property, `VpnServicePolicy`
  lint suppression) to **`systemExempted`** with `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, matching
  the official foreground-service-types guidance for VPN apps; `startForeground` call updated.
- `themes.xml`: removed `statusBarColor`/`navigationBarColor`/`windowLight*` items deprecated by
  Android 15 edge-to-edge enforcement; `enableEdgeToEdge` remains the single source of truth.
- `Uri.parse` replaced with the KTX `String.toUri` extension (lint `UseKtx`).
- Deprecated `TileService.startActivityAndCollapse(Intent)` legacy branch documented and
  suppressed per lint waiver LINT-TILE-COLLAPSE-001 (API 24-33 fallback is unavoidable).

### Responsiveness (phones, tablets, foldables, desktop mode, TV)
- `OblivionApp` now wraps all destinations in a single-pane adaptive frame using the documented
  Material 3 window width breakpoints (compact <600 dp; medium/expanded capped at a 600 dp
  centered column). No new dependency: uses `BoxWithConstraints`, matching the existing
  `SplashScreen` pattern; `material3-adaptive` was evaluated and rejected (alpha-only, not in the
  Compose BOM). Phone (compact) layout is pixel-identical to before.
- TV: banner, LEANBACK_LAUNCHER, `leanback`/`touchscreen` feature declarations verified; focus
  traversal comes from standard Material3 clickable semantics.

### Dead code / resource removal (Android side only)
- Deleted unused sources: `utils/NetworkUtils`, `utils/LogFiles`, `utils/ColorUtils`,
  `utils/SystemUtils` (deprecated window APIs), `wireguard/WireGuardProfileRepository`,
  `ui/SettingsDialogs.EditValueDialog`, `MainActivity.start()`.
- Deleted unused resources: 6 unreferenced fonts (emoji/oxygen x3/shabnam-light/thin),
  `values/attrs.xml` (unused `Icon` styleable), empty `res/color`, `res/layout`,
  `res/values-night`, `res/values-television` dirs, and the empty nested
  `service/org/bepass/...` directory tree.
- ProGuard: removed 4 stale `base.*Initializer` keep rules; added the missing
  `-keep class hev.htproxy.TProxyService` rule that protects the name-based JNI binding under
  full-mode R8 repackaging.
- `TunnelEndpointPolicy` rewritten with named constants and split helpers to satisfy the zero
  Detekt findings gate (MagicNumber, ReturnCount); policy behavior unchanged and unit-tested.
- `EndpointSheet`: duplicate endpoint content is now rejected on save and `LazyColumn` rows use
  content keys; `SplitTunnelScreen` bitmap assertion `!!` replaced with null-safe `?.let`.

### Verification evidence (this pass)
- `compileOssDebugKotlin`/`compilePlayDebugKotlin` with Kotlin warnings-as-errors: PASS
- `testOssDebugUnitTest` + `testPlayDebugUnitTest`: PASS
- `detekt` zero findings, `spotlessCheck`, `lintOssDebug` (warningsAsErrors, one documented
  waiver pair): PASS under strict dependency verification
- Native core tasks were excluded (`-x buildNativeCore -x buildHev`) because pre-existing
  core-side edits (native/ + tun2socks inputs modified 2026-07-19) fail the pinned usque
  patch-hash gate against the AAR built 2026-07-16. That rebuild is core-side work and is
  intentionally untouched per project boundaries; see docs/PRODUCTION_GATES.md.

## 2026-06-21 — Gradle 9.6.0 Full Feature Adoption

### Upgraded
- **Gradle:** 9.5.0 → **9.6.0** (wrapper + distribution)
- **Compose BOM:** 2026.05.01 → **2026.06.01**
- **Core KTX:** 1.19.0-rc01 → **1.19.0** (stable)

### Gradle 9.6.0 Features Enabled
- **Configuration Cache:** Improved hit rates for project properties via system properties/env vars (auto-enabled)
- **`NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS`** feature preview enabled in `settings.gradle.kts` (prepares for Gradle 10)
- **Performance optimizations** in `gradle.properties`: `org.gradle.parallel=true`, `org.gradle.vfs.watch=true`, `org.gradle.configuration-cache.problems=fail`, increased JVM heap (3GB daemon, 2GB Kotlin daemon)
- **CLI improvements:** `--non-interactive` support, `NO_COLOR` env var, sortable HTML test reports (auto)

### Deprecation Fixes (Gradle 10 Preparation)
- **Keystore loading migrated** from `Properties` + `inputStream()` to `OptionalPropertiesValueSource` (CC-compatible, lazy Provider API)
- **Removed deprecated Kotlin DSL delegated property** (`by providers.of(...)` → explicit `providers.of(...)`)
- **Verified no task action deprecations** (taskDependencies, getExtensions, injected Project/Gradle services)
- **Spotless 8.6.0** retained (8.7.0 available but CC compatibility unverified); explicit incompatibility removed

### Build Infrastructure
- **buildSrc:** Added JetBrains Kotlin EAP repository for plugin compatibility
- **Configuration Cache:** Verified working with cache storage and reuse (`help`, `tasks`, `versionAudit`)

### Known Considerations
- AGP 9.2.1 retained (9.3.0+ not yet stable)
- Hilt 2.59.2, Navigation Compose 2.10.0-alpha05, Lifecycle 2.11.0-rc01 retained (stable versions not yet released)
- Deprecation warning: "Using a Project object as a dependency notation" — likely from AGP/internal plugin, not project code
- `buildNeeded`/`buildDependents` tasks deprecated in Gradle 9.6 (will be removed in Gradle 10)

## 2026-06-20 — Tun2socks Core Reset

- Removed the local `tun2socks.aar` dependency and Gradle verification step.
- Reduced `tun2socks/` to a dependency-free placeholder module.
- Added a Kotlin `tun2socks` placeholder API so the Android app still compiles while failing fast if the absent core is started.
- Removed the stale `warp-plus` linkage from the Go module.

## 2026-06-14 — Comprehensive Version Audit & Fixes

### Current Versions (from actual project files)

| Component | Version | Status |
|-----------|---------|--------|
| **Gradle** | 9.5.0 | Latest stable |
| **AGP** | 9.2.1 | Latest stable |
| **Kotlin** | 2.3.21 | Latest stable |
| **KSP** | 2.3.9 | Compatible with Kotlin 2.3.21 |
| **JDK** | 25 | Toolchain + daemon |
| **NDK** | 29.0.14206865 | Stable |
| **Compose BOM** | 2026.05.01 | Latest |
| **Navigation Compose** | 2.10.0-alpha05 | Latest (alpha) |
| **Activity Compose/KTX** | 1.13.0 | Latest stable |
| **Core KTX** | 1.19.0-rc01 | Release candidate |
| **Lifecycle** | 2.11.0-rc01 | Release candidate |
| **Hilt** | 2.59.2 | Latest stable |
| **Hilt Navigation Compose** | 1.4.0-rc01 | Release candidate |
| **OkHttp** | 5.3.2 | Latest stable |
| **Coroutines** | 1.11.0 | Latest |
| **MMKV** | 2.4.0 | Latest stable |
| **Glide** | 5.0.7 | Latest stable |
| **Timber** | 5.0.1 | Latest stable |
| **Detekt** | 2.0.0-alpha.3 | Alpha (awaiting stable) |
| **Spotless** | 8.6.0 | Latest stable |
| **Dependency Analysis** | 3.14.0 | Latest stable |
| **Go (tun2socks)** | 1.24.1 | Module version |
| **compileSdk** | 37 | Preview SDK |
| **targetSdk** | 36 | Android 16 |
| **minSdk** | 24 | Android 7.0 |

### Changes Made
- Synchronized this document with actual version catalog (`gradle/libs.versions.toml`)
- Added google() repository to `buildSrc/build.gradle.kts` for AGP compatibility
- Fixed `compileSdk` consistency (37 → aligned with targetSdk documentation)
- Replaced direct `android.util.Log` calls with `Timber` throughout `OblivionVpnService.kt`
- Fixed ProGuard `-assumenosideeffects` for Log to avoid breaking Timber
- Refactored `tun2socks.go` to use struct-based encapsulation (eliminated global mutable state)
- Updated `tools:targetApi` in AndroidManifest.xml

### Known Considerations
- Several libraries use alpha/rc versions intentionally (bleeding-edge policy)
- Navigation Compose 2.10.0-alpha05: using latest features, stable expected soon
- Lifecycle 2.11.0-rc01: release candidate, stable imminent
- Detekt 2.0.0-alpha.3: major rewrite, alpha quality
- compileSdk 37 is a preview SDK; verify compatibility with AGP 9.2.1
- Go module at `go 1.24.1`; `tun2socks` is currently a dependency-free placeholder.

## 2026-04-25 (initial changes)

### Upgraded

#### Core Toolchain
- Kotlin: `2.3.20-RC` → `2.4.0` (later reverted, see final corrections)
- KSP: `2.3.6` → `2.4.0-1.0.31` (later aligned with Kotlin downgrade, see final corrections)
- AGP: `8.14.0` → `9.1.0` (`gradle/libs.versions.toml`)
- Gradle: `9.2.1` → `9.3.1` (wrapper + `gradle-wrapper.properties`)
- JDK: `21` → `25` (toolchain JVM, daemon JVM, `gradle.properties`)
- NDK: `29.0.14206865` (unchanged, but verified on AGP 9)

#### AndroidX & Compose
- Compose BOM: `2026.02.00` → `2026.04.00`
- Navigation Compose: `2.9.7` → `2.10.0-alpha01`
- Activity Compose: `1.13.0-alpha01` → `1.13.0-alpha02`
- Core KTX: `1.18.0-rc01` → `1.18.0`
- Lifecycle: `2.10.0` (unchanged)
- AppCompat: `1.7.1` → `1.7.2`
- Material Design: `1.14.0-alpha09` → `1.14.0-alpha10`
- ConstraintLayout: `2.2.1` (unchanged)
- Fragment: `1.8.9` (unchanged)
- Core Splashscreen: `1.2.0` (unchanged)
- ProfileInstaller: `1.4.1` (unchanged)

#### Plugin Dependencies
- Google Services: `4.4.4` → `4.4.5`
- Firebase Plugins: Crashlytics `3.0.6` → `3.0.7`; Perf `2.0.2` → `2.0.3`
- Detekt: `1.23.8` → `1.24.0`
- Spotless: `8.2.1` → `8.3.0`
- Dependency Analysis: `3.5.1` → `3.6.0`

#### Core Libraries
- Hilt: `2.59.2` (unchanged)
- OkHttp: `5.3.2` (unchanged)
- Coroutines: `1.10.2` (unchanged)
- MMKV: `2.3.0` (unchanged here; later bumped, see final corrections)
- Coil: `3.3.0` → `3.4.0-alpha01`

### Massive Java to Kotlin Migration
- Converted all remaining Java source files to Kotlin, including:
  - `ApplicationLoader` (now fully Hilt-aware, with proper error isolation and Timber logging)
  - `BaseActivity`, `StateAwareBaseActivity`
  - All activities: `MainActivity`, `SettingsActivity`, `LogActivity`, `SplashScreenActivity`, `SplitTunnelActivity`, `InfoActivity`
  - All adapters: `BypassListAppsAdapter`, `EndpointsBottomSheet`, `SplitTunnelOptionsAdapter`
  - Utility classes: `FileManager`, `SystemUtils`, `ColorUtils`, `LocaleManager`, `HostPortParser`, etc.
  - Custom views: `Icon`, `TouchAwareSwitch`
  - DNS and network modules: `NetworkModule`, `DnsUriParserTest`, `DnsExecutionPlannerTest`, etc.
  - Build-time classes: `FileExistsValueSource`, `OptionalPropertiesValueSource`
  - Removed all Java sources; project is now fully Kotlin-based.
- Updated `app/build.gradle` to use `alias(libs...)` exclusively for plugins and dependencies.
- Removed deprecated `vectorDrawables.useSupportLibrary` and redundant `buildConfig` flag from `app/build.gradle`.
- Migrated release signing configuration from hardcoded values to external `keystore.properties` (gitignored), with a `validateReleaseSigning` task.
- Previously rewrote gomobile task wiring; that native-core path was removed by the 2026-06-20 reset.
- Switched `FileManager.initialize` call from `BaseActivity` to `ApplicationLoader` to avoid redundant initialisation.
- Refactored `FileManager` into a singleton `object`, added `Keys` constants, and replaced manual locking with `ReentrantReadWriteLock`.
- Centralised all VPN configuration building in `FileManager.getVpnConfig()` under a single read-lock for consistency.

### Lint & Code Quality
- Lint baseline (`lint-baseline.xml`) completely emptied after fixing all reported issues:
  - Updated dependency versions.
  - Removed unused resources (drawables, colors, strings, dimensions/styles, arrays).
  - Fixed icon density issues and removed redundant `png` copies in favour of `anydpi` vector drawables.
  - Replaced `LinearLayout` in `toast.xml` with a single `TextView` using compound drawables.
  - Removed overdraw on root elements by relying on theme backgrounds.
  - Hardcoded endpoint text moved to string resources.
- Upgraded `detekt.yml` to the latest official baseline, updated rule names and properties.
- **Deleted the lint baseline file entirely** (zero lint warnings now).
- Removed `gradle-versions-plugin` because it crashes on this Gradle/AGP stack.
  Use `version_audit.ps1` instead.

### ProGuard / R8
- Rewrote `proguard-rules.pro` with modern best practices:
  - Enabled line number retention and source file renaming for Crashlytics visibility.
  - Added `-assumenosideeffects` to strip `android.util.Log` calls in release builds.
  - Previously retained native library rules; those rules were removed by the 2026-06-20 reset.

### Build Infrastructure
- Updated `gradle.properties`:
  - JVM args: `-Xmx2048M` (from 1536M); removed obsolete `-XX:MaxPermSize`.
  - Enabled parallel builds (`org.gradle.parallel=true`).
  - Set `org.gradle.warning.mode=all`.
  - Added `android.builtInKotlin=true` for AGP 9.x (though it is the default, explicit is safer).
  - `android.enableR8.fullMode=true` kept for clarity (default since AGP 8).
- Updated wrapper scripts (`gradlew`, `gradlew.bat`, `gradle-wrapper.properties`) to match the current Gradle wrapper version.
- Updated `devshell.nix` and `flake.nix` to target JDK 26 and Go 1.26 for future‑proofing (the project still compiles with JDK 25).
- Updated `version_audit.ps1` to compare against the **latest stable** Gradle version (not RCs), avoiding false positives.
- Revised `.gitignore` to track new build artifacts and Kotlin caches properly.

### Configuration & App Manifest
- Overhauled `AndroidManifest.xml`:
  - Added `android:foregroundServiceType="dataSync|specialUse"` and the corresponding `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` to the VPN service (required since Android 14).
  - Removed the dangerous `QUERY_ALL_PACKAGES` permission; kept only safe `<queries>` entries.
  - Ensured compatibility with Predictive Back gesture on Android 16+.
  - Removed deprecated `usesCleartextTraffic` attribute; network security is now controlled exclusively via `networkSecurityConfig`.
- Redesigned `dns_providers.json` into a structured catalog (`pinned`, `verified`, `community`) with Iranian providers, `selectionWeight` fields, and health‑check rules for the custom DNS engine.
- Resolved Git conflict in `dimens.xml`, keeping all spacing and radius tokens with proper documentation.

### Core Tunnel (Go) — tun2socks.go
- Complete rewrite of the tunnel core for safety and resource hygiene:
  - Eliminated global mutable state; all lifecycle is now managed by a `Tunnel` struct.
  - Added `Start`/`Wait`/`Stop` non‑blocking semantics with proper goroutine draining.
  - Added panic recovery in the main worker goroutine with full stack‑trace logging.
  - Capturing stdout/stderr is done with `os.Pipe`; both are restored cleanly on shutdown.
  - Used `sync.Pool` for zero‑allocation log pipeline buffers.
  - Input validation (`validateOptions`) reports all errors at once using `errors.Join`.
  - All file descriptors and goroutines are guaranteed to be released before `Stop` returns.

## 2026-04-25 (final corrections — post‑audit)

### Version Corrections
- **Kotlin:** `2.4.0` reverted to `2.3.21` — `2.4.0` is not yet released (planned June‑July 2026).
- **KSP:** `2.4.0-1.0.31` → `2.3.21-1.0.31` (aligned with Kotlin downgrade).
- **Gradle:** `9.3.1` → `9.4.1` — latest stable as of late April 2026.
- **MMKV:** `2.3.0` → `2.4.0` (released March 2026).
- **Coil:** `3.4.0-alpha01` → `3.4.0` (stable release).
- **Navigation Compose:** `2.10.0-alpha01` → `2.9.7` (reverted to stable; the alpha is not recommended for production).
- **Spotless:** `8.3.0` → `8.4.0` (latest release).
- **Go (devshell):** `1.25` → `1.26.1` (latest stable; aligned with `devshell.nix`).
- **JDK (devshell):** `25` → `26` (aligned with `devshell.nix`; the project's compilation toolchain remains on JDK 25 until full validation).

### Config Adjustments
- Added `android.builtInKotlin=true` to `gradle.properties` (explicit for AGP 9.x).
- Updated `Gradle_Playbook.md` and `devshell.nix` to reflect final version numbers.
- Finalised `tun2socks.go` with all reviewed improvements (race‑condition fix, panic recovery, stdout/stderr capture hygiene, resource‑pool cleanup).
- Updated `flake.nix` to pin NDK 29.0.14206865 (matching the project) and JDK 26.

### Notes
- Kotlin `2.4.0` should be adopted when it reaches stable (target: mid‑2026).
- Gradle `9.4.1` officially supports JDK 26, but the project's compilation toolchain remains on JDK 25 until all dependencies are verified against JDK 26.
- The dependency analysis plugin (`com.autonomousapps.dependency-analysis`) stays at `3.6.0` — no newer stable version available.
- All Java files have been deleted and the project is now **100% Kotlin** (including `buildSrc` and tests). No Java source remains.
