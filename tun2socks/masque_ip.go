package tun2socks

import (
	"context"
	"crypto/tls"
	"errors"
	"io"
	"net/url"
	"os"
	"time"

	connectip "github.com/quic-go/connect-ip-go"
	"github.com/quic-go/quic-go"
	"github.com/quic-go/quic-go/http3"
	"github.com/yosida95/uritemplate/v3"
)

func runMasqueIP(ctx context.Context, opt *StartOptions) error {
	if opt.TunFd <= 0 {
		return errors.New("invalid tun fd")
	}
	if opt.MasqueURL == "" {
		return errors.New("empty MASQUE url")
	}
	u, err := url.Parse(opt.MasqueURL)
	if err != nil {
		return err
	}
	tlsConf := &tls.Config{
		ServerName:         u.Hostname(),
		InsecureSkipVerify: opt.MasqueInsecure,
		NextProtos:         []string{http3.NextProtoH3},
	}
	qconf := &quic.Config{EnableDatagrams: true}
	addr := u.Hostname()
	if u.Port() != "" {
		addr = u.Host
	} else {
		addr = u.Hostname() + ":443"
	}
	qconn, err := quic.DialAddr(ctx, addr, tlsConf, qconf)
	if err != nil {
		return err
	}
	h3c := http3.NewClientConn(qconn)
	tpl, err := uritemplate.New(opt.MasqueURL)
	if err != nil {
		_ = h3c.Close()
		return err
	}
	ipConn, _, err := connectip.Dial(ctx, h3c, tpl)
	if err != nil {
		_ = h3c.Close()
		return err
	}

	tunFile := os.NewFile(uintptr(opt.TunFd), "tun")
	if tunFile == nil {
		_ = ipConn.Close()
		_ = h3c.Close()
		return errors.New("failed to open tun fd")
	}

	// TUN -> MASQUE
	go func() {
		buf := make([]byte, 65536)
		for {
			select {
			case <-ctx.Done():
				return
			default:
			}
			n, err := tunFile.Read(buf)
			if err != nil {
				if err == io.EOF {
					return
				}
				// transient: sleep briefly
				time.Sleep(10 * time.Millisecond)
				continue
			}
			_, werr := ipConn.WritePacket(buf[:n])
			if werr != nil {
				time.Sleep(10 * time.Millisecond)
				continue
			}
		}
	}()

	// MASQUE -> TUN
	go func() {
		buf := make([]byte, 65536)
		for {
			select {
			case <-ctx.Done():
				return
			default:
			}
			n, rerr := ipConn.ReadPacket(buf)
			if rerr != nil {
				time.Sleep(10 * time.Millisecond)
				continue
			}
			_, werr := tunFile.Write(buf[:n])
			if werr != nil {
				time.Sleep(10 * time.Millisecond)
				continue
			}
		}
	}()

	go func() {
		<-ctx.Done()
		_ = ipConn.Close()
		_ = h3c.Close()
		_ = tunFile.Close()
	}()

	return nil
}
