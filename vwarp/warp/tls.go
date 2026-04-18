package warp

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/netip"
	"strings"
	"time"

	"github.com/avast/retry-go"
	"github.com/voidr3aper-anon/Vwarp/iputils"

	"github.com/noql-net/certpool"
	tls "github.com/refraction-networking/utls"
)

// Dialer is a struct that holds various options for custom dialing.
type Dialer struct {
	l                    *slog.Logger
	anycastIPs           string
	preferredFingerprint string
}

const utlsExtensionSNICurve uint16 = 0x15

// SNICurveExtension implements SNICurve (0x15) extension
type SNICurveExtension struct {
	*tls.GenericExtension
	SNICurveLen int
	WillPad     bool // set false to disable extension
}

// Len returns the length of the SNICurveExtension.
func (e *SNICurveExtension) Len() int {
	if e.WillPad {
		return 4 + e.SNICurveLen
	}
	return 0
}

// Read reads the SNICurveExtension.
func (e *SNICurveExtension) Read(b []byte) (n int, err error) {
	if !e.WillPad {
		return 0, io.EOF
	}
	if len(b) < e.Len() {
		return 0, io.ErrShortBuffer
	}
	// https://tools.ietf.org/html/rfc7627
	b[0] = byte(utlsExtensionSNICurve >> 8)
	b[1] = byte(utlsExtensionSNICurve)
	b[2] = byte(e.SNICurveLen >> 8)
	b[3] = byte(e.SNICurveLen)
	y := make([]byte, 1200)
	copy(b[4:], y)
	return e.Len(), io.EOF
}

const SNICurveSize = 1200

func spec(sni string) *tls.ClientHelloSpec {
	return &tls.ClientHelloSpec{
		TLSVersMax: tls.VersionTLS12,
		TLSVersMin: tls.VersionTLS12,
		CipherSuites: []uint16{
			tls.GREASE_PLACEHOLDER,
			tls.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305,
			tls.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
			tls.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
			tls.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
			tls.TLS_AES_128_GCM_SHA256, // tls 1.3
			tls.FAKE_TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
			tls.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
			tls.TLS_RSA_WITH_AES_256_CBC_SHA,
		},
		Extensions: []tls.TLSExtension{
			&SNICurveExtension{
				SNICurveLen: SNICurveSize,
				WillPad:     true,
			},
			&tls.SupportedCurvesExtension{Curves: []tls.CurveID{tls.X25519, tls.CurveP256}},
			&tls.SupportedPointsExtension{SupportedPoints: []byte{0}}, // uncompressed
			&tls.SessionTicketExtension{},
			&tls.ALPNExtension{AlpnProtocols: []string{"http/1.1"}},
			&tls.SignatureAlgorithmsExtension{
				SupportedSignatureAlgorithms: []tls.SignatureScheme{
					tls.ECDSAWithP256AndSHA256,
					tls.ECDSAWithP384AndSHA384,
					tls.ECDSAWithP521AndSHA512,
					tls.PSSWithSHA256,
					tls.PSSWithSHA384,
					tls.PSSWithSHA512,
					tls.PKCS1WithSHA256,
					tls.PKCS1WithSHA384,
					tls.PKCS1WithSHA512,
					tls.ECDSAWithSHA1,
					tls.PKCS1WithSHA1,
				},
			},
			&tls.KeyShareExtension{KeyShares: []tls.KeyShare{
				{Group: tls.CurveID(tls.GREASE_PLACEHOLDER), Data: []byte{0}},
				{Group: tls.X25519},
			}},
			&tls.PSKKeyExchangeModesExtension{Modes: []uint8{1}}, // pskModeDHE
			&tls.SNIExtension{ServerName: sni},
		},
		GetSessionID: nil,
	}
}

func makeTLSHelloPacketWithSNICurve(plainConn net.Conn, config *tls.Config, sni string) (*tls.UConn, error) {
	utlsConn := tls.UClient(plainConn, config, tls.HelloCustom)
	err := utlsConn.ApplyPreset(spec(sni))
	if err != nil {
		return nil, fmt.Errorf("uTlsConn.Handshake() error: %w", err)
	}

	err = utlsConn.Handshake()
	if err != nil {
		return nil, fmt.Errorf("uTlsConn.Handshake() error: %w", err)
	}

	return utlsConn, nil
}

func dialCurve(network string, ip netip.Addr, sni string) (net.Conn, error) {
	plainDialer := &net.Dialer{
		Timeout:   5 * time.Second,
		KeepAlive: 5 * time.Second,
	}

	plainConn, err := plainDialer.Dial(network, netip.AddrPortFrom(ip, 443).String())
	if err != nil {
		return nil, err
	}

	config := tls.Config{
		ServerName: sni,
		MinVersion: tls.VersionTLS12,
		RootCAs:    certpool.Roots(),
	}

	tlsConn, handshakeErr := makeTLSHelloPacketWithSNICurve(plainConn, &config, sni)
	if handshakeErr != nil {
		_ = plainConn.Close()
		return nil, handshakeErr
	}
	return tlsConn, nil
}

func dial2(network string, ip netip.Addr, sni string) (net.Conn, error) {
	plainDialer := &net.Dialer{
		Timeout:   5 * time.Second,
		KeepAlive: 5 * time.Second,
	}

	plainConn, err := plainDialer.Dial(network, netip.AddrPortFrom(ip, 443).String())
	if err != nil {
		return nil, err
	}

	tlsConfig := tls.Config{
		ServerName: sni,
		MinVersion: tls.VersionTLS13,
		RootCAs:    certpool.Roots(),
	}

	tlsConn := tls.Client(plainConn, &tlsConfig)
	err = tlsConn.Handshake()
	if err != nil {
		_ = plainConn.Close()
		return nil, err
	}

	return tlsConn, nil
}
func dial3(network string, ip netip.Addr, sni string) (net.Conn, error) {
	plainDialer := &net.Dialer{
		Timeout:   5 * time.Second,
		KeepAlive: 5 * time.Second,
	}

	plainConn, err := plainDialer.Dial(network, netip.AddrPortFrom(ip, 443).String())
	if err != nil {
		return nil, err
	}

	tlsConfig := tls.Config{
		ServerName: sni,
		MinVersion: tls.VersionTLS13,
		RootCAs:    certpool.Roots(),
	}

	tlsConn := tls.UClient(plainConn, &tlsConfig, tls.HelloChrome_Auto)
	err = tlsConn.Handshake()
	if err != nil {
		_ = plainConn.Close()
		return nil, err
	}

	return tlsConn, nil
}

// TLSDial dials a TLS connection.
func (d *Dialer) TLSDial(network, addr string) (net.Conn, error) {
	sni, _, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, err
	}
	// Determine trial IPs
	var ips []netip.Addr
	if d.anycastIPs != "" {
		prefixes := strings.Split(d.anycastIPs, ",")
		for _, p := range prefixes {
			p = strings.TrimSpace(p)
			prefix, err := netip.ParsePrefix(p)
			if err != nil {
				addr, err := netip.ParseAddr(p)
				if err == nil {
					ips = append(ips, addr)
				}
				continue
			}
			ip, err := iputils.RandomIPFromPrefix(prefix)
			if err == nil {
				ips = append(ips, ip)
			}
		}
	}

	// Fallback to defaults if no IPs found or provided
	if len(ips) == 0 {
		defaultPrefixes := []string{"141.101.113.0/24", "188.114.97.0/24", "162.159.192.0/24"}
		for _, p := range defaultPrefixes {
			prefix, _ := netip.ParsePrefix(p)
			ip, _ := iputils.RandomIPFromPrefix(prefix)
			ips = append(ips, ip)
		}
	}

	// Pick up to 3 target IPs for parallel dialing
	if len(ips) > 3 {
		ips = ips[:3]
	}

	type dialResult struct {
		conn net.Conn
		err  error
	}

	resChan := make(chan dialResult, len(ips))
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	dialFuncs := []struct {
		name string
		f    func(string, netip.Addr, string) (net.Conn, error)
	}{
		{"fprint1", dialCurve},
		{"fprint2", dial2},
		{"fprint3", dial3},
	}

	for _, targetIP := range ips {
		go func(ip netip.Addr) {
			var tlsConn net.Conn
			var lastErr error

			// Create a local copy of dial functions to avoid race
			localDialFuncs := make([]struct {
				name string
				f    func(string, netip.Addr, string) (net.Conn, error)
			}, len(dialFuncs))
			copy(localDialFuncs, dialFuncs)

			// Prioritize preferred fingerprint
			if d.preferredFingerprint != "" {
				for i, df := range localDialFuncs {
					if df.name == d.preferredFingerprint && i > 0 {
						localDialFuncs[0], localDialFuncs[i] = localDialFuncs[i], localDialFuncs[0]
						break
					}
				}
			}

			for _, df := range localDialFuncs {
				err := retry.Do(
					func() error {
						var rerr error
						tlsConn, rerr = df.f(network, ip, sni)
						if rerr == nil {
							d.l.Info("[STAT] DIAL_SUCCESS: " + df.name)
						}
						return rerr
					},
					retry.Attempts(2),
					retry.Delay(200*time.Millisecond),
				)
				if err == nil {
					resChan <- dialResult{conn: tlsConn, err: nil}
					return
				}
				lastErr = err
			}
			resChan <- dialResult{conn: nil, err: lastErr}
		}(targetIP)
	}

	var lastErr error
	for i := 0; i < len(ips); i++ {
		select {
		case res := <-resChan:
			if res.err == nil {
				// Success! Close any other winner that might come after this
				go func(idx int) {
					for j := idx + 1; j < len(ips); j++ {
						r := <-resChan
						if r.conn != nil {
							r.conn.Close()
						}
					}
				}(i)
				return res.conn, nil
			}
			lastErr = res.err
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}

	return nil, lastErr
}
