package app

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/netip"
	"path"
	"sync"
	"time"

	"github.com/voidr3aper-anon/Vwarp/config/noize"
	"github.com/voidr3aper-anon/Vwarp/iputils"
	"github.com/voidr3aper-anon/Vwarp/masque"
	masquenoize "github.com/voidr3aper-anon/Vwarp/masque/noize"
	"github.com/voidr3aper-anon/Vwarp/psiphon"
	"github.com/voidr3aper-anon/Vwarp/tunbridge"
	"github.com/voidr3aper-anon/Vwarp/warp"
	"github.com/voidr3aper-anon/Vwarp/wireguard/preflightbind"
	"github.com/voidr3aper-anon/Vwarp/wireguard/tun/netstack"
	"github.com/voidr3aper-anon/Vwarp/wiresocks"
)

const singleMTU = 1280 // MASQUE/QUIC tunnel MTU (standard MTU matching usque)
const doubleMTU = 1280 // minimum mtu for IPv6, may cause frag reassembly somewhere

type WarpOptions struct {
	Bind               netip.AddrPort
	Endpoint           string
	License            string
	DnsAddrs           []netip.Addr
	Psiphon            *PsiphonOptions
	Gool               bool
	Masque             bool
	MasquePreferred    bool   // Prefer MASQUE over WireGuard with automatic fallback
	MasqueNoize        bool   // Enable MASQUE noize obfuscation
	MasqueNoizePreset  string // Noize preset: light, medium, heavy, stealth, gfw
	MasqueNoizeConfig  string // Path to custom noize configuration JSON file
	Scan               *wiresocks.ScanOptions
	CacheDir           string
	FwMark             uint32
	WireguardConfig    string
	Reserved           string
	TunFd              int
	TestURL            string
	AtomicNoizeConfig  *preflightbind.AtomicNoizeConfig
	UnifiedNoizeConfig *noize.UnifiedNoizeConfig // Unified configuration for both WireGuard and MASQUE obfuscation
	ProxyAddress       string
	AnycastIPs         string
	PreferredFingerprint string
	ProxyBypass        string
}

type PsiphonOptions struct {
	Country string
	Conduit bool
}

func RunWarp(ctx context.Context, l *slog.Logger, opts WarpOptions) error {
	if opts.WireguardConfig != "" {
		if err := runWireguard(ctx, l, opts); err != nil {
			return err
		}

		return nil
	}

	if opts.Psiphon != nil && opts.Gool {
		return errors.New("can't use psiphon and gool at the same time")
	}

	if opts.Masque && opts.Gool {
		return errors.New("can't use masque and gool at the same time")
	}

	if opts.Masque && opts.Psiphon != nil {
		return errors.New("can't use masque and psiphon at the same time")
	}

	if opts.Psiphon != nil && opts.Psiphon.Country == "" {
		return errors.New("must provide country for psiphon")
	}

	// Decide Working Scenario
	endpoints := []string{opts.Endpoint, opts.Endpoint}

	if opts.Scan != nil {
		// make primary identity
		ident, err := warp.LoadOrCreateIdentity(l, path.Join(opts.CacheDir, "primary"), opts.License, opts.AnycastIPs, opts.PreferredFingerprint)
		if err != nil {
			l.Error("couldn't load primary warp identity")
			return err
		}

		// Reading the private key from the 'Interface' section
		opts.Scan.PrivateKey = ident.PrivateKey

		// Reading the public key from the 'Peer' section
		opts.Scan.PublicKey = ident.Config.Peers[0].PublicKey

		res, err := wiresocks.RunScan(ctx, l, *opts.Scan)
		if err != nil {
			return err
		}

		l.Debug("scan results", "endpoints", res)

		endpoints = make([]string, len(res))
		for i := 0; i < len(res); i++ {
			endpoints[i] = res[i].AddrPort.String()
		}
	} else {
		for i, ep := range endpoints {
			addr, err := iputils.ParseResolveAddressPort(ep, false, opts.DnsAddrs[0].String())
			if err == nil {
				endpoints[i] = addr.String()
			} else {
				l.Warn("failed to resolve endpoint", "endpoint", ep, "error", err)
			}
		}
	}
	l.Info("using warp endpoints", "endpoints", endpoints)

	pipe := newWGPipeline(ctx, l, opts)

	var warpErr error
	switch {
	case opts.Masque:
		l.Info("running in MASQUE mode")
		warpErr = runWarpWithMasque(ctx, l, opts, endpoints[0])
	case opts.MasquePreferred:
		l.Info("running in MASQUE-preferred mode")
		warpErr = runWarpWithMasque(ctx, l, opts, endpoints[0])
		if warpErr != nil {
			l.Warn("MASQUE preferred but failed, falling back to WireGuard", "error", warpErr)
			warpErr = pipe.RunSingleHop(endpoints[0])
			if warpErr == nil {
				l.Info("WireGuard fallback successful")
			}
		} else {
			l.Info("MASQUE preferred mode successful")
		}
	case opts.Psiphon != nil:
		l.Info("running in Psiphon (cfon) mode", "conduit", opts.Psiphon.Conduit)
		warpErr = pipe.RunWithPsiphon(endpoints[0], func(warpBind netip.AddrPort) error {
			err := psiphon.RunPsiphon(ctx, l.With("subsystem", "psiphon"), warpBind, opts.CacheDir, opts.Bind, opts.Psiphon.Country, opts.Psiphon.Conduit)
			if err != nil {
				return fmt.Errorf("unable to run psiphon %w", err)
			}
			l.Info("serving proxy", "address", opts.Bind)
			return nil
		})
	case opts.Gool:
		l.Info("running in warp-in-warp (gool) mode")
		warpErr = pipe.RunDoubleHop(endpoints)
	default:
		l.Info("running in normal warp mode")
		warpErr = pipe.RunSingleHop(endpoints[0])
	}

	return warpErr
}

// runWireguard handles the custom WireGuard config file case.
func runWireguard(ctx context.Context, l *slog.Logger, opts WarpOptions) error {
	conf, err := wiresocks.ParseConfig(opts.WireguardConfig)
	if err != nil {
		return err
	}

	// Use the pipeline for the common setup steps
	pipe := newWGPipeline(ctx, l, opts)
	pipe.conf = *conf

	atomicNoizeConfig := getAtomicNoizeConfig(opts)

	// Custom config needs special peer handling (resolve domains, etc)
	pipe.conf.Interface.MTU = singleMTU
	pipe.conf.Interface.DNS = opts.DnsAddrs

	for i, peer := range pipe.conf.Peers {
		if atomicNoizeConfig == nil {
			peer.Trick = true
		}
		peer.KeepAlive = 5

		// Try resolving if the endpoint is a domain
		addr, err := iputils.ParseResolveAddressPort(peer.Endpoint, false, opts.DnsAddrs[0].String())
		if err == nil {
			peer.Endpoint = addr.String()
		}

		pipe.conf.Peers[i] = peer
	}

	if err := pipe.establishWithRetry(""); err != nil {
		return err
	}

	pipe.startTunBridge(singleMTU)

	actualBind, err := pipe.startProxy(opts.Bind)
	if err != nil {
		return err
	}

	l.Info("serving proxy", "address", actualBind)
	return nil
}

// NOTE: runWarp, runWarpInWarp, runWarpWithPsiphon have been replaced
// by wgPipeline.RunSingleHop, RunDoubleHop, RunWithPsiphon in pipeline.go
// This eliminates ~300 lines of duplicated code.

// getAtomicNoizeConfig extracts AtomicNoize configuration from options
func getAtomicNoizeConfig(opts WarpOptions) *preflightbind.AtomicNoizeConfig {
	// Check unified config first
	if opts.UnifiedNoizeConfig != nil && opts.UnifiedNoizeConfig.IsWireGuardEnabled() {
		return opts.UnifiedNoizeConfig.WireGuard.AtomicNoize
	}
	// Fallback to legacy config
	return opts.AtomicNoizeConfig
}

// getMASQUEPresetConfig returns the MASQUE noize configuration for a given preset
func getMASQUEPresetConfig(preset string, l *slog.Logger) *masquenoize.NoizeConfig {
	switch preset {
	case "minimal":
		return masquenoize.MinimalObfuscationConfig()
	case "light":
		return masquenoize.LightObfuscationConfig()
	case "medium":
		return masquenoize.MediumObfuscationConfig()
	case "heavy":
		return masquenoize.HeavyObfuscationConfig()
	case "stealth":
		return masquenoize.StealthObfuscationConfig()
	case "gfw":
		return masquenoize.GFWBypassConfig()
	case "firewall":
		return masquenoize.FirewallBypassConfig()
	case "none":
		return nil
	default:
		l.Warn("Unknown MASQUE noize preset, using medium", "preset", preset)
		return masquenoize.MediumObfuscationConfig()
	}
}

func runWarpWithMasque(ctx context.Context, l *slog.Logger, opts WarpOptions, endpoint string) error {
	l.Info("running in MASQUE mode")

	// Check network MTU compatibility for MASQUE
	iputils.DetectAndCheckMTUForMasque(l)

	// Convert endpoint to MASQUE endpoint (port 443)
	// The endpoint may be from scanner (port 2408) or user-provided (any port)
	var masqueEndpoint string
	l.Info("using endpoint as MASQUE server", "endpoint", endpoint)
	if host, _, err := net.SplitHostPort(endpoint); err == nil {
		// Successfully split, use the host with port 443
		masqueEndpoint = net.JoinHostPort(host, "443")
		l.Debug("Converted endpoint to MASQUE endpoint", "from", endpoint, "to", masqueEndpoint)
	} else {
		// No port specified, assume it's just a host, add port 443
		masqueEndpoint = net.JoinHostPort(endpoint, "443")
		l.Debug("Added MASQUE port to endpoint", "from", endpoint, "to", masqueEndpoint)
	}

	// Create MASQUE adapter using usque library
	masqueConfigPath := path.Join(opts.CacheDir, "masque_config.json")
	l.Debug("Creating MASQUE adapter", "masqueEndpoint", masqueEndpoint, "configPath", masqueConfigPath)

	// Configure noize obfuscation using unified configuration system
	var noizeConfig *masquenoize.NoizeConfig

	// Check for unified configuration first
	if opts.UnifiedNoizeConfig != nil && opts.UnifiedNoizeConfig.IsMASQUEEnabled() {
		l.Info("Using unified MASQUE noize configuration")
		masqueConfig := opts.UnifiedNoizeConfig.MASQUE

		if masqueConfig.Config != nil {
			// Use custom configuration
			noizeConfig = masqueConfig.Config
			l.Info("Using custom unified MASQUE noize configuration")
		} else if masqueConfig.Preset != "" {
			// Use preset from unified config
			l.Info("Using unified MASQUE noize preset", "preset", masqueConfig.Preset)
			noizeConfig = getMASQUEPresetConfig(masqueConfig.Preset, l)
		}
	} else if opts.MasqueNoize {
		// Fallback to legacy configuration for backward compatibility
		l.Info("Using legacy MASQUE noize configuration")

		// Check for custom config file first
		if opts.MasqueNoizeConfig != "" {
			l.Info("Loading custom MASQUE noize configuration", "configPath", opts.MasqueNoizeConfig)
			customConfig, err := masquenoize.LoadConfigFromFile(opts.MasqueNoizeConfig)
			if err != nil {
				l.Warn("Failed to load custom noize config, falling back to preset", "error", err, "preset", opts.MasqueNoizePreset)
			} else {
				noizeConfig = customConfig
				l.Info("Custom noize configuration loaded successfully")
			}
		}

		// Use preset if no custom config loaded
		if noizeConfig == nil {
			preset := opts.MasqueNoizePreset
			if preset == "" {
				preset = "medium"
			}
			l.Info("Using legacy MASQUE noize preset", "preset", preset)
			noizeConfig = getMASQUEPresetConfig(preset, l)
		}
	}

	// Create MASQUE adapter with retry for Android connectivity issues
	var adapter *masque.MasqueAdapter
	var err error

	// Try creating adapter with retries for Android initialization issues
	for attempt := 1; attempt <= 3; attempt++ {
		l.Debug("Creating MASQUE adapter", "attempt", attempt)

		adapter, err = masque.NewMasqueAdapter(ctx, masque.AdapterConfig{
			ConfigPath:  masqueConfigPath,
			DeviceName:  "vwarp-masque",
			Endpoint:    masqueEndpoint,
			Logger:      l,
			License:     opts.License,
			NoizeConfig: noizeConfig,
		})

		if err == nil {
			l.Info("MASQUE adapter created successfully", "attempt", attempt)
			break
		}

		l.Warn("Failed to create MASQUE adapter", "attempt", attempt, "error", err)

		// On Android, sometimes network interfaces need time to stabilize
		if attempt < 3 {
			retryDelay := time.Duration(attempt) * 2 * time.Second
			l.Info("Retrying MASQUE adapter creation", "delay", retryDelay)
			time.Sleep(retryDelay)
		}
	}

	if err != nil {
		return fmt.Errorf("failed to establish MASQUE connection after retries: %w", err)
	}
	defer adapter.Close()

	l.Info("MASQUE tunnel established successfully")

	// Get tunnel addresses
	ipv4, ipv6 := adapter.GetLocalAddresses()
	l.Info("MASQUE tunnel addresses", "ipv4", ipv4, "ipv6", ipv6)

	// Create TUN device configuration for the MASQUE tunnel
	tunAddresses := []netip.Addr{}
	if ipv4 != "" {
		if addr, err := netip.ParseAddr(ipv4); err == nil {
			tunAddresses = append(tunAddresses, addr)
		}
	}
	if ipv6 != "" {
		if addr, err := netip.ParseAddr(ipv6); err == nil {
			tunAddresses = append(tunAddresses, addr)
		}
	}

	if len(tunAddresses) == 0 {
		return errors.New("no valid tunnel addresses received from MASQUE")
	}

	// Use multiple DNS servers for redundancy - primary and fallbacks
	dnsServers := append([]netip.Addr{}, opts.DnsAddrs...)

	// Add fallback DNS servers to improve reliability
	fallbackDNS := []string{
		"8.8.8.8", // Google DNS
		"8.8.4.4", // Google DNS secondary
		"1.0.0.1", // Cloudflare DNS secondary
		"9.9.9.9", // Quad9 DNS
	}

	for _, dns := range fallbackDNS {
		if addr, err := netip.ParseAddr(dns); err == nil && addr != opts.DnsAddrs[0] {
			dnsServers = append(dnsServers, addr)
		}
	}

	l.Info("DNS servers configured", "primary", opts.DnsAddrs[0], "fallback_count", len(dnsServers)-1)

	// Create netstack TUN
	tunDev, tnet, err := netstack.CreateNetTUN(tunAddresses, dnsServers, singleMTU)
	if err != nil {
		return fmt.Errorf("failed to create netstack: %w", err)
	}

	l.Info("netstack created on MASQUE tunnel")

	// Create adapter for the netstack device
	tunAdapter := &netstackTunAdapter{
		dev:             tunDev,
		tunnelBufPool:   &sync.Pool{New: func() interface{} { buf := make([][]byte, 1); return &buf }},
		tunnelSizesPool: &sync.Pool{New: func() interface{} { sizes := make([]int, 1); return &sizes }},
	}

	// Create adapter factory for reconnection
	adapterFactory := func() (*masque.MasqueAdapter, error) {
		l.Info("Recreating MASQUE adapter with fresh configuration")
		return masque.NewMasqueAdapter(ctx, masque.AdapterConfig{
			ConfigPath:  masqueConfigPath,
			DeviceName:  "vwarp-masque",
			Endpoint:    masqueEndpoint,
			Logger:      l,
			License:     opts.License,
			NoizeConfig: noizeConfig,
		})
	}

	// Start tunnel maintenance goroutine
	go maintainMasqueTunnel(ctx, l, adapter, adapterFactory, tunAdapter, singleMTU, tnet, opts.TestURL)

	// Test connectivity
	if err := usermodeTunTest(ctx, l, tnet, opts.TestURL); err != nil {
		l.Warn("connectivity test failed", "error", err)
		// Don't fail completely, just warn
	} else {
		l.Info("MASQUE connectivity test passed")
	}

	if opts.TunFd > 0 {
		go func() {
			if err := tunbridge.Start(ctx, l.With("subsystem", "tunbridge"), opts.TunFd, singleMTU, tnet); err != nil {
				l.Error("tunbridge failed", "error", err)
			}
		}()
	}

	// Start SOCKS proxy on the netstack
	actualBind, err := wiresocks.StartProxy(ctx, l, tnet, opts.Bind, opts.ProxyBypass)
	if err != nil {
		return fmt.Errorf("failed to start proxy: %w", err)
	}

	l.Info("serving proxy via MASQUE tunnel", "address", actualBind)

	// Keep running until context is cancelled
	<-ctx.Done()
	return nil
}

// generateWireguardConfig is kept as an alias for backward compatibility.
// The canonical version is makeWGConfig in pipeline.go.
func generateWireguardConfig(i *warp.Identity) wiresocks.Configuration {
	return makeWGConfig(i)
}
