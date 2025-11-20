//go:build !cgo
// +build !cgo

package lwip

type Tun2socksStartOptions struct {
	TunFd        int
	Socks5Server string
	FakeIPRange  string
	MTU          int
	EnableIPv6   bool
	AllowLan     bool
}

func Start(opt *Tun2socksStartOptions) int {
	return 0
}

func Stop() {}
