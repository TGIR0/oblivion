package masque

import (
	"context"
	crand "crypto/rand"
	"crypto/tls"
	"errors"
	"fmt"
	"log/slog"
	"math/rand"
	"net"
	"net/http"
	"os"

	connectip "github.com/Diniboy1123/connect-ip-go"
	"github.com/quic-go/quic-go"
	"github.com/quic-go/quic-go/http3"
	"github.com/voidr3aper-anon/Vwarp/masque/noize"
	"github.com/yosida95/uritemplate/v3"
)

// ─── Unified ConnectTunnel ─────────────────────────────────────────────
// The old codebase had ConnectTunnelWithNoize and ConnectTunnelOptimized
// which were ~90% identical. This unified version uses TunnelOptions to
// optionally enable noize, keeping a single code path.

// TunnelOptions controls optional behaviour of ConnectTunnel.
type TunnelOptions struct {
	// NoizeConfig enables QUIC packet obfuscation when non-nil.
	NoizeConfig *noize.NoizeConfig
	// Logger, if non-nil, enables structured logging during connection.
	Logger *slog.Logger
}

// ConnectTunnel establishes a MASQUE connect-ip tunnel.
//
// When opts.NoizeConfig is non-nil, the UDP connection is wrapped with
// noize obfuscation that automatically disables after tunnel establishment.
func ConnectTunnel(
	ctx context.Context,
	tlsConfig *tls.Config,
	quicConfig *quic.Config,
	connectUri string,
	endpoint *net.UDPAddr,
	opts *TunnelOptions,
) (*net.UDPConn, *http3.Transport, *connectip.Conn, *http.Response, error) {

	// Normalise opts
	if opts == nil {
		opts = &TunnelOptions{}
	}
	l := opts.Logger // may be nil → guarded below

	// ── Create UDP connection ────────────────────────────────────────
	udpConn, err := listenUDP(endpoint)
	if err != nil {
		return nil, nil, nil, nil, err
	}

	// ── Wrap with noize if configured ─────────────────────────────────
	var quicConn net.PacketConn = udpConn
	var noizeConn *noize.NoizeUDPConn

	if opts.NoizeConfig != nil {
		noizeConn = noize.WrapUDPConn(udpConn, opts.NoizeConfig)
		quicConn = noizeConn

		if l != nil {
			l.Info("Noize wrapper created",
				"jcBeforeHS", opts.NoizeConfig.JcBeforeHS,
				"jcAfterI1", opts.NoizeConfig.JcAfterI1)
		}

		if os.Getenv("VWARP_NOIZE_DEBUG") == "1" {
			noizeConn.EnableDebugPadding()
		}
	} else if l != nil {
		l.Warn("No noize config provided - using plain UDP connection")
	}

	// ── UDP buffer optimisation ──────────────────────────────────────
	if l != nil {
		if bufferErr := configureUDPBuffer(udpConn, l); bufferErr != nil {
			l.Warn("Failed to optimize UDP buffer settings", "error", bufferErr)
		}
	}

	// ── Pre-handshake obfuscation ────────────────────────────────────
	if noizeConn != nil {
		if l != nil {
			l.Info("Sending pre-handshake obfuscation before QUIC dial")
		}
		// Random trigger payload to avoid fixed fingerprint
		triggerSize := 16 + rand.Intn(48)
		testData := make([]byte, triggerSize)
		crand.Read(testData)
		if _, err := quicConn.WriteTo(testData, endpoint); err != nil && l != nil {
			l.Warn("Pre-handshake trigger failed", "error", err)
		}
	}

	// ── QUIC dial ────────────────────────────────────────────────────
	if l != nil {
		l.Info("About to call quic.Dial",
			"packetConnType", fmt.Sprintf("%T", quicConn),
			"hasNoize", noizeConn != nil)
	}

	conn, err := quic.Dial(ctx, quicConn, endpoint, tlsConfig, quicConfig)
	if err != nil {
		return udpConn, nil, nil, nil, err
	}

	// ── HTTP/3 transport ─────────────────────────────────────────────
	tr := &http3.Transport{
		EnableDatagrams: true,
		AdditionalSettings: map[uint64]uint64{
			0x276: 1, // SETTINGS_H3_DATAGRAM_00
		},
		DisableCompression: true,
	}

	hconn := tr.NewClientConn(conn)

	additionalHeaders := http.Header{
		"User-Agent": []string{""},
	}

	template := uritemplate.MustNew(connectUri)
	ipConn, rsp, err := connectip.Dial(ctx, hconn, template, "cf-connect-ip", additionalHeaders, true)
	if err != nil {
		if err.Error() == "CRYPTO_ERROR 0x131 (remote): tls: access denied" {
			return udpConn, nil, nil, nil, errors.New(
				"login failed! Please double-check if your tls key and cert is enrolled in the Cloudflare Access service")
		}
		return udpConn, nil, nil, nil, fmt.Errorf("failed to dial connect-ip: %v", err)
	}

	// ── Disable noize post-establishment ─────────────────────────────
	if noizeConn != nil {
		noizeConn.DisableObfuscation()
		if l != nil {
			l.Info("Noize obfuscation disabled after successful tunnel establishment")
		}
	}

	return udpConn, tr, ipConn, rsp, nil
}

// ─── Backward-compatible wrappers ──────────────────────────────────────

// ConnectTunnelWithNoize is the legacy API. Use ConnectTunnel instead.
func ConnectTunnelWithNoize(
	ctx context.Context,
	tlsConfig *tls.Config,
	quicConfig *quic.Config,
	connectUri string,
	endpoint *net.UDPAddr,
	noizeConfig *noize.NoizeConfig,
	logger *slog.Logger,
) (*net.UDPConn, *http3.Transport, *connectip.Conn, *http.Response, error) {
	return ConnectTunnel(ctx, tlsConfig, quicConfig, connectUri, endpoint, &TunnelOptions{
		NoizeConfig: noizeConfig,
		Logger:      logger,
	})
}

// ConnectTunnelOptimized is the legacy API. Use ConnectTunnel instead.
func ConnectTunnelOptimized(
	ctx context.Context,
	tlsConfig *tls.Config,
	quicConfig *quic.Config,
	connectUri string,
	endpoint *net.UDPAddr,
	logger *slog.Logger,
) (*net.UDPConn, *http3.Transport, *connectip.Conn, *http.Response, error) {
	return ConnectTunnel(ctx, tlsConfig, quicConfig, connectUri, endpoint, &TunnelOptions{
		Logger: logger,
	})
}

// ── Helpers ──────────────────────────────────────────────────────────

// listenUDP creates a UDP listener appropriate for the endpoint's address family.
func listenUDP(endpoint *net.UDPAddr) (*net.UDPConn, error) {
	bindIP := net.IPv4zero
	if endpoint.IP.To4() == nil {
		bindIP = net.IPv6zero
	}
	return net.ListenUDP("udp", &net.UDPAddr{IP: bindIP, Port: 0})
}
