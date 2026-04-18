package app

import (
	"context"
	"encoding/base64"
	"fmt"
	"log/slog"
	"net/netip"
	"path"
	"sync"
	"time"

	"github.com/voidr3aper-anon/Vwarp/tunbridge"
	"github.com/voidr3aper-anon/Vwarp/warp"
	"github.com/voidr3aper-anon/Vwarp/wireguard/tun"
	"github.com/voidr3aper-anon/Vwarp/wireguard/tun/netstack"
	"github.com/voidr3aper-anon/Vwarp/wiresocks"
)

// ─── WireGuard Pipeline ────────────────────────────────────────────────
// wgPipeline is a reusable builder that encapsulates the common WireGuard
// setup sequence shared by normal, gool, and psiphon modes.
// It eliminates the repeated identity→config→tunDev→establish→test→proxy
// ceremony that was duplicated across four functions.

// wgPipeline centralises WireGuard tunnel lifecycle.
type wgPipeline struct {
	ctx  context.Context
	l    *slog.Logger
	opts WarpOptions

	// outputs
	ident  *warp.Identity
	conf   wiresocks.Configuration
	tnet   *netstack.Net
	tunDev tun.Device
}

// newWGPipeline creates a pipeline with defaults extracted from opts.
func newWGPipeline(ctx context.Context, l *slog.Logger, opts WarpOptions) *wgPipeline {
	return &wgPipeline{ctx: ctx, l: l, opts: opts}
}

// loadIdentity loads or creates warp identity from the given subdirectory.
func (p *wgPipeline) loadIdentity(subdir string) error {
	ident, err := warp.LoadOrCreateIdentity(
		p.l,
		path.Join(p.opts.CacheDir, subdir),
		p.opts.License,
		p.opts.AnycastIPs,
		p.opts.PreferredFingerprint,
	)
	if err != nil {
		p.l.Error("couldn't load warp identity", "subdir", subdir)
		return err
	}
	p.ident = ident
	p.conf = makeWGConfig(ident)
	return nil
}

// makeWGConfig generates a WireGuard Configuration from a warp Identity.
// Extracted from the old generateWireguardConfig which duplicated this everywhere.
func makeWGConfig(i *warp.Identity) wiresocks.Configuration {
	priv, _ := wiresocks.EncodeBase64ToHex(i.PrivateKey)
	pub, _ := wiresocks.EncodeBase64ToHex(i.Config.Peers[0].PublicKey)
	clientID, _ := base64.StdEncoding.DecodeString(i.Config.ClientID)
	return wiresocks.Configuration{
		Interface: &wiresocks.InterfaceConfig{
			PrivateKey: priv,
			Addresses: []netip.Addr{
				netip.MustParseAddr(i.Config.Interface.Addresses.V4),
				netip.MustParseAddr(i.Config.Interface.Addresses.V6),
			},
		},
		Peers: []wiresocks.PeerConfig{{
			PublicKey:    pub,
			PreSharedKey: "0000000000000000000000000000000000000000000000000000000000000000",
			AllowedIPs: []netip.Prefix{
				netip.MustParsePrefix("0.0.0.0/0"),
				netip.MustParsePrefix("::/0"),
			},
			Endpoint: i.Config.Peers[0].Endpoint.Host,
			Reserved: [3]byte{clientID[0], clientID[1], clientID[2]},
		}},
	}
}

// configureInterface sets MTU, DNS, and peer parameters in a uniform way.
func (p *wgPipeline) configureInterface(mtu int, endpoint string, enableTrick bool) error {
	p.conf.Interface.MTU = mtu
	p.conf.Interface.DNS = p.opts.DnsAddrs

	for i, peer := range p.conf.Peers {
		peer.Endpoint = endpoint

		if enableTrick && p.opts.AtomicNoizeConfig == nil {
			peer.Trick = true
		}
		peer.KeepAlive = 5

		if p.opts.Reserved != "" {
			r, err := wiresocks.ParseReserved(p.opts.Reserved)
			if err != nil {
				return err
			}
			peer.Reserved = r
		}

		p.conf.Peers[i] = peer
	}
	return nil
}

// establishWithRetry performs the WireGuard establishment with t1/t2 retry.
func (p *wgPipeline) establishWithRetry(logLabel string) error {
	atomicNoize := getAtomicNoizeConfig(p.opts)
	var werr error

	for _, t := range []string{"t1", "t2"} {
		var tunDev tun.Device
		var tnet *netstack.Net

		tunDev, tnet, werr = netstack.CreateNetTUN(
			p.conf.Interface.Addresses,
			p.conf.Interface.DNS,
			p.conf.Interface.MTU,
		)
		if werr != nil {
			continue
		}

		logger := p.l
		if logLabel != "" {
			logger = p.l.With("pipeline", logLabel)
		}

		werr = establishWireguard(logger, &p.conf, tunDev, p.opts.FwMark, t, atomicNoize, p.opts.ProxyAddress)
		if werr != nil {
			continue
		}

		// Test connectivity
		werr = usermodeTunTest(p.ctx, p.l, tnet, p.opts.TestURL)
		if werr != nil {
			continue
		}

		p.tunDev = tunDev
		p.tnet = tnet
		break
	}

	return werr
}

// startTunBridge starts the TUN bridging for Android if TunFd is set.
func (p *wgPipeline) startTunBridge(mtu int) {
	if p.opts.TunFd > 0 {
		go func() {
			if err := tunbridge.Start(
				p.ctx,
				p.l.With("subsystem", "tunbridge"),
				p.opts.TunFd,
				mtu,
				p.tnet,
			); err != nil {
				p.l.Error("tunbridge failed", "error", err)
			}
		}()
	}
}

// startProxy starts a SOCKS proxy on the userspace stack.
func (p *wgPipeline) startProxy(bind netip.AddrPort) (netip.AddrPort, error) {
	return wiresocks.StartProxy(p.ctx, p.l, p.tnet, bind, p.opts.ProxyBypass)
}

// ─── Convenience: full pipeline run ──────────────────────────────────

// RunSingleHop runs a complete single-hop WireGuard tunnel.
// Replaces the old runWarp function.
func (p *wgPipeline) RunSingleHop(endpoint string) error {
	if err := p.loadIdentity("primary"); err != nil {
		return err
	}
	if err := p.configureInterface(singleMTU, endpoint, true); err != nil {
		return err
	}
	if err := p.establishWithRetry(""); err != nil {
		return err
	}

	p.startTunBridge(singleMTU)

	actualBind, err := p.startProxy(p.opts.Bind)
	if err != nil {
		return err
	}

	p.l.Info("serving proxy", "address", actualBind)
	return nil
}

// RunDoubleHop runs warp-in-warp (gool) mode.
// Replaces the old runWarpInWarp function.
func (p *wgPipeline) RunDoubleHop(endpoints []string) error {
	// ── Outer hop ──────────────────
	if err := p.loadIdentity("primary"); err != nil {
		return err
	}
	if err := p.configureInterface(singleMTU, endpoints[0], true); err != nil {
		return err
	}
	if err := p.establishWithRetry("outer"); err != nil {
		return err
	}

	// Forward UDP through the outer tunnel
	addr, err := wiresocks.NewVtunUDPForwarder(
		p.ctx,
		netip.MustParseAddrPort("127.0.0.1:0"),
		endpoints[0],
		p.tnet,
		singleMTU,
	)
	if err != nil {
		return err
	}

	// ── Inner hop ──────────────────
	outerTnet := p.tnet // save outer tnet

	if err := p.loadIdentity("secondary"); err != nil {
		return err
	}

	p.conf.Interface.MTU = doubleMTU
	p.conf.Interface.DNS = p.opts.DnsAddrs

	for i, peer := range p.conf.Peers {
		peer.Endpoint = addr.String()
		peer.KeepAlive = 20

		if p.opts.Reserved != "" {
			r, err := wiresocks.ParseReserved(p.opts.Reserved)
			if err != nil {
				return err
			}
			peer.Reserved = r
		}
		p.conf.Peers[i] = peer
	}

	// Inner hop doesn't use AtomicNoize or trick
	tunDev, tnet2, err := netstack.CreateNetTUN(
		p.conf.Interface.Addresses,
		p.conf.Interface.DNS,
		p.conf.Interface.MTU,
	)
	if err != nil {
		return err
	}

	if err := establishWireguard(
		p.l.With("pipeline", "inner"),
		&p.conf, tunDev, p.opts.FwMark, "t0", nil, "",
	); err != nil {
		return err
	}

	if err := usermodeTunTest(p.ctx, p.l, tnet2, p.opts.TestURL); err != nil {
		return err
	}

	// Use inner tnet for proxy
	p.tnet = tnet2
	p.tunDev = tunDev
	_ = outerTnet // outer remains active for forwarding

	p.startTunBridge(doubleMTU)

	actualBind, err := p.startProxy(p.opts.Bind)
	if err != nil {
		return err
	}

	p.l.Info("serving proxy", "address", actualBind)
	return nil
}

// RunWithPsiphon runs WireGuard + Psiphon tunnel.
// Replaces the old runWarpWithPsiphon function.
func (p *wgPipeline) RunWithPsiphon(endpoint string, bindFunc func(warpBind netip.AddrPort) error) error {
	if err := p.loadIdentity("primary"); err != nil {
		return err
	}
	if err := p.configureInterface(singleMTU, endpoint, true); err != nil {
		return err
	}
	if err := p.establishWithRetry(""); err != nil {
		return err
	}

	p.startTunBridge(singleMTU)

	// Start proxy on random port for psiphon to connect to
	warpBind, err := wiresocks.StartProxy(p.ctx, p.l, p.tnet, netip.MustParseAddrPort("127.0.0.1:0"), p.opts.ProxyBypass)
	if err != nil {
		return err
	}

	return bindFunc(warpBind)
}

// ─── Buffer Pool ───────────────────────────────────────────────────────
// Shared buffer pool for packet operations. Avoids per-packet allocations
// in the hot path of the Noize engine and tunnel forwarding.

var packetBufPool = sync.Pool{
	New: func() interface{} {
		buf := make([]byte, 0, 2048)
		return &buf
	},
}

// GetPacketBuf returns a buffer from the pool.
func GetPacketBuf() *[]byte {
	return packetBufPool.Get().(*[]byte)
}

// PutPacketBuf returns a buffer to the pool.
func PutPacketBuf(buf *[]byte) {
	*buf = (*buf)[:0]
	packetBufPool.Put(buf)
}

// ─── Retry Helper ───────────────────────────────────────────────────────

// retry runs fn up to maxAttempts times with exponential backoff.
func retry(ctx context.Context, l *slog.Logger, label string, maxAttempts int, baseSleep time.Duration, fn func() error) error {
	var lastErr error
	for attempt := 1; attempt <= maxAttempts; attempt++ {
		lastErr = fn()
		if lastErr == nil {
			if attempt > 1 {
				l.Info("retry succeeded", "label", label, "attempt", attempt)
			}
			return nil
		}

		l.Warn("attempt failed", "label", label, "attempt", attempt, "error", lastErr)
		if attempt < maxAttempts {
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(baseSleep * time.Duration(attempt)):
			}
		}
	}
	return fmt.Errorf("%s failed after %d attempts: %w", label, maxAttempts, lastErr)
}
