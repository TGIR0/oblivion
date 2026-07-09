module github.com/bepass-org/oblivion/tun2socks

go 1.26.4

tool golang.org/x/mobile/cmd/gobind

require (
	github.com/Diniboy1123/usque v4.2.0+incompatible
	github.com/things-go/go-socks5 v0.1.1
	golang.zx2c4.com/wireguard v0.0.0-20260522210424-ecfc5a8d5446
)

require (
	github.com/andybalholm/brotli v1.1.1 // indirect
	github.com/avast/retry-go v3.0.0+incompatible // indirect
	github.com/klauspost/compress v1.18.0 // indirect
	github.com/noql-net/certpool v0.0.0-20250417123926-688b52c002ee // indirect
	github.com/refraction-networking/utls v1.8.2 // indirect
	golang.org/x/crypto v0.53.0 // indirect
)

require (
	github.com/Diniboy1123/connect-ip-go v0.0.0-20260613064811-66cba32d7d33 // indirect
	github.com/bepass-org/warp-plus v0.0.0-20251117204114-f70ea7e4f193
	github.com/dunglas/httpsfv v1.1.0 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/patrickmn/go-cache v2.1.0+incompatible // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/quic-go/quic-go v0.60.0 // indirect
	github.com/songgao/water v0.0.0-20200317203138-2b4b6d7c09d8 // indirect
	github.com/txthinking/runnergroup v0.0.0-20250224021307-5864ffeb65ae // indirect
	github.com/txthinking/socks5 v0.0.0-20260601051520-339b044ab0eb // indirect
	github.com/yosida95/uritemplate/v3 v3.0.2 // indirect
	golang.org/x/exp v0.0.0-20260611194520-c48552f49976 // indirect
	golang.org/x/mobile v0.0.0-20260611195102-4dd8f1dbf5d2 // indirect
	golang.org/x/mod v0.37.0 // indirect
	golang.org/x/net v0.56.0 // indirect
	golang.org/x/sync v0.21.0 // indirect
	golang.org/x/sys v0.46.0 // indirect
	golang.org/x/text v0.38.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	golang.org/x/tools v0.46.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	gvisor.dev/gvisor v0.0.0-20260616165937-8e4bc62602eb // indirect
)

replace github.com/Diniboy1123/usque => ../build/native/usque/source

replace github.com/bepass-org/warp-plus => ../build/native/warp-plus/source
