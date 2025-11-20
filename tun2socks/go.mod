module tun2socks

go 1.25.3

replace github.com/eycorsican/go-tun2socks => github.com/trojan-gfw/go-tun2socks v1.16.3-0.20210702214000-083d49176e05

require (
	github.com/bepass-org/warp-plus v1.2.6
	github.com/eycorsican/go-tun2socks v1.16.11
	github.com/songgao/water v0.0.0-20200317203138-2b4b6d7c09d8
	golang.org/x/mobile v0.0.0-20251113184115-a159579294ab // Revert to known good version
	golang.org/x/tools v0.39.0 // indirect; Try newer version for Go 1.25 fix
)

require (
	filippo.io/bigmod v0.1.0 // indirect
	filippo.io/edwards25519 v1.1.0 // indirect
	filippo.io/keygen v0.0.0-20251031143024-42ba85b9427e // indirect
	github.com/AndreasBriese/bbloom v0.0.0-20190825152654-46b345b51c96 // indirect
	github.com/Psiphon-Labs/bolt v0.0.0-20200624191537-23cedaef7ad7 // indirect
	github.com/Psiphon-Labs/consistent v0.0.0-20240322131436-20aaa4e05737 // indirect
	github.com/Psiphon-Labs/goptlib v0.0.0-20200406165125-c0e32a7a3464 // indirect
	github.com/Psiphon-Labs/psiphon-tls v0.0.0-20250318183125-2a2fae2db378 // indirect
	github.com/Psiphon-Labs/psiphon-tunnel-core v1.0.11-0.20250122170852-4ba6e22a08f1 // indirect
	github.com/Psiphon-Labs/quic-go v0.0.0-20240821052333-b6316b594e39 // indirect
	github.com/Psiphon-Labs/utls v1.1.1-0.20241107183331-b18909f8ccaa // indirect
	github.com/andybalholm/brotli v1.2.0 // indirect
	github.com/armon/go-proxyproto v0.1.0 // indirect
	github.com/avast/retry-go v3.0.0+incompatible // indirect
	github.com/bifurcation/mint v0.0.0-20210616192047-fd18df995463 // indirect
	github.com/bits-and-blooms/bitset v1.24.4 // indirect
	github.com/bits-and-blooms/bloom/v3 v3.7.1 // indirect
	github.com/cespare/xxhash v1.1.0 // indirect
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	github.com/cheekybits/genny v1.0.0 // indirect
	github.com/cloudflare/circl v1.6.1 // indirect
	github.com/coder/websocket v1.8.14 // indirect
	github.com/cognusion/go-cache-lru v0.0.0-20170419142635-f73e2280ecea // indirect
	github.com/davecgh/go-spew v1.1.2-0.20180830191138-d8f796af33cc // indirect
	github.com/dblohm7/wingoes v0.0.0-20250822163801-6d8e6105c62d // indirect
	github.com/dchest/siphash v1.2.3 // indirect
	github.com/dgraph-io/badger v1.6.2 // indirect
	github.com/dgraph-io/ristretto v0.2.0 // indirect
	github.com/dgryski/go-farm v0.0.0-20240924180020-3414d57e47da // indirect
	github.com/djherbis/buffer v1.2.0 // indirect
	github.com/djherbis/nio v2.0.3+incompatible // indirect
	github.com/dustin/go-humanize v1.0.1 // indirect
	github.com/flynn/noise v1.1.0 // indirect
	github.com/fxamacker/cbor/v2 v2.9.0 // indirect
	github.com/go-ini/ini v1.67.0 // indirect
	github.com/go-json-experiment/json v0.0.0-20251027170946-4849db3c2f7e // indirect
	github.com/go-logr/logr v1.4.3 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/golang/groupcache v0.0.0-20241129210726-2c02b8208cf8 // indirect
	github.com/golang/protobuf v1.5.4 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/grafov/m3u8 v0.12.1 // indirect
	github.com/hashicorp/golang-lru v1.0.2 // indirect
	github.com/jsimonetti/rtnetlink v1.4.2 // indirect
	github.com/klauspost/compress v1.18.1 // indirect
	github.com/libp2p/go-reuseport v0.4.0 // indirect
	github.com/marusama/semaphore v0.0.0-20190110074507-6952cef993b2 // indirect
	github.com/mdlayher/netlink v1.8.0 // indirect
	github.com/mdlayher/socket v0.5.1 // indirect
	github.com/miekg/dns v1.1.68 // indirect
	github.com/mroth/weightedrand v1.0.0 // indirect
	github.com/noql-net/certpool v0.0.0-20251031010508-a6d1add8fb9e // indirect
	github.com/onsi/gomega v1.27.10 // indirect
	github.com/pelletier/go-toml v1.9.5 // indirect
	github.com/pion/datachannel v1.5.10 // indirect
	github.com/pion/dtls/v2 v2.2.12 // indirect
	github.com/pion/ice/v2 v2.3.38 // indirect
	github.com/pion/interceptor v0.1.42 // indirect
	github.com/pion/logging v0.2.4 // indirect
	github.com/pion/mdns v0.0.12 // indirect
	github.com/pion/randutil v0.1.0 // indirect
	github.com/pion/rtcp v1.2.16 // indirect
	github.com/pion/rtp v1.8.25 // indirect
	github.com/pion/sctp v1.8.40 // indirect
	github.com/pion/sdp/v3 v3.0.16 // indirect
	github.com/pion/srtp/v2 v2.0.20 // indirect
	github.com/pion/stun v0.6.1 // indirect
	github.com/pion/transport/v2 v2.2.10 // indirect
	github.com/pion/transport/v3 v3.1.1 // indirect
	github.com/pion/turn/v2 v2.1.6 // indirect
	github.com/pion/webrtc/v3 v3.3.6 // indirect
	github.com/pkg/errors v0.9.1 // indirect
	github.com/pmezard/go-difflib v1.0.1-0.20181226105442-5d4384ee4fb2 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/refraction-networking/conjure v0.9.1 // indirect
	github.com/refraction-networking/ed25519 v0.1.2 // indirect
	github.com/refraction-networking/gotapdance v1.7.10 // indirect
	github.com/refraction-networking/obfs4 v0.1.2 // indirect
	github.com/refraction-networking/utls v1.8.1 // indirect
	github.com/sagernet/gvisor v0.0.0-20250811-sing-box-mod.1 // indirect
	github.com/sagernet/sing v0.7.13 // indirect
	github.com/sergeyfrolov/bsbuffer v0.0.0-20180903213811-94e85abb8507 // indirect
	github.com/sirupsen/logrus v1.9.3 // indirect
	github.com/stretchr/testify v1.11.1 // indirect
	github.com/syndtr/gocapability v0.0.0-20200815063812-42c35b437635 // indirect
	github.com/tailscale/goupnp v1.0.1-0.20210804011211-c64d0f06ea05 // indirect
	github.com/v2pro/plz v0.0.0-20221028024117-e5f9aec5b631 // indirect
	github.com/wader/filtertransport v0.0.0-20200316221534-bdd9e61eee78 // indirect
	github.com/wlynxg/anet v0.0.5 // indirect
	github.com/x448/float16 v0.8.4 // indirect
	gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/goptlib v1.6.0 // indirect
	gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2 v2.11.0 // indirect
	go.uber.org/mock v0.5.2 // indirect
	go4.org/mem v0.0.0-20240501181205-ae6ca9944745 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.45.0 // indirect
	golang.org/x/exp v0.0.0-20251113190631-e25ba8c21ef6 // indirect
	golang.org/x/mod v0.30.0 // indirect
	golang.org/x/net v0.47.0 // indirect
	golang.org/x/sync v0.18.0 // indirect
	golang.org/x/sys v0.38.0 // indirect
	golang.org/x/text v0.31.0 // indirect
	golang.org/x/time v0.14.0 // indirect
	golang.zx2c4.com/wireguard v0.0.0-20250521234502-f333402bd9cb // indirect
	golang.zx2c4.com/wireguard/windows v0.5.3 // indirect
	google.golang.org/grpc v1.75.1 // indirect
	google.golang.org/protobuf v1.36.10 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
	tailscale.com v1.90.8 // indirect
)
