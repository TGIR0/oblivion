package tun2socks

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"log/slog"
	"net/netip"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"
	"tun2socks/lwip"

	"github.com/bepass-org/warp-plus/app"
	"github.com/bepass-org/warp-plus/wiresocks"
)

// Variables to hold flag values.
var (
	logMessages []string
	mu          sync.Mutex
	ctx         context.Context
	cancelFunc  context.CancelFunc
	l           *slog.Logger
)

type StartOptions struct {
	TunFd        int
	Path         string
	FakeIPRange  string
	MTU          int
	Verbose      bool
	BindAddress  string
	Endpoint     string
	License      string
	Gool         bool
	DNS          string
	EndpointType int
}

type logWriter struct{}

func (writer logWriter) Write(bytes []byte) (int, error) {
	mu.Lock()
	defer mu.Unlock()
	logMessages = append(logMessages, string(bytes))
	return len(bytes), nil
}

func Start(opt *StartOptions) error {
	ctx, cancelFunc = signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)

	if err := os.Chdir(opt.Path); err != nil {
		return fmt.Errorf("error changing to 'main' directory: %v", err)
	}

	logger := logWriter{}

	lOpts := slog.HandlerOptions{
		Level: func() slog.Level {
			if opt.Verbose {
				return slog.LevelDebug
			}
			return slog.LevelInfo
		}(),
		ReplaceAttr: func(groups []string, a slog.Attr) slog.Attr {
			if (a.Key == slog.TimeKey || a.Key == slog.LevelKey) && len(groups) == 0 {
				return slog.Attr{} // remove excess keys
			}
			return a
		},
	}

	l = slog.New(slog.NewTextHandler(logger, &lOpts))
	r, w, _ := os.Pipe()
	os.Stdout = w
	os.Stderr = w

	go func(reader io.Reader) {
		scanner := bufio.NewScanner(reader)
		for scanner.Scan() {
			logger.Write([]byte(scanner.Text()))
		}
		if err := scanner.Err(); err != nil {
			// Log to internal logger instead of stderr to avoid recursion/loops
			logger.Write([]byte(fmt.Sprintf("scanner error: %v", err)))
		}
	}(r)
	l.Info(fmt.Sprintf("%+v", *opt))
	var scanOpts *wiresocks.ScanOptions
	if opt.Endpoint == "" {
		scanOpts = &wiresocks.ScanOptions{
			V4:     false,
			V6:     false,
			MaxRTT: 1500 * time.Millisecond,
		}
		switch opt.EndpointType {
		case 0:
			scanOpts.V4 = true
			scanOpts.V6 = true
		case 1:
			scanOpts.V4 = true
		case 2:
			scanOpts.V6 = true
		}
	}
	go func() {
		err := app.RunWarp(ctx, l, app.WarpOptions{
			Bind:     netip.MustParseAddrPort(opt.BindAddress),
			DnsAddr:  netip.MustParseAddr(opt.DNS),
			Endpoint: opt.Endpoint,
			License:  opt.License,
			Gool:     opt.Gool,
			Scan:     scanOpts,
			TestURL:  "http://connectivity.cloudflareclient.com/cdn-cgi/trace",
		})
		if err != nil {
			l.Error(fmt.Sprintf("failed to run warp: %v", err))
			cancelFunc()
		}
	}()

	fakeRange := opt.FakeIPRange
	if fakeRange == "" {
		fakeRange = "198.18.0.0/15"
	}

	mtu := opt.MTU

	tun2socksStartOptions := &lwip.Tun2socksStartOptions{
		TunFd:        opt.TunFd,
		Socks5Server: strings.Replace(opt.BindAddress, "0.0.0.0", "127.0.0.1", -1),
		FakeIPRange:  fakeRange,
		MTU:          mtu,
		EnableIPv6:   true,
		AllowLan:     true,
	}
	if ret := lwip.Start(tun2socksStartOptions); ret != 0 {
		return fmt.Errorf("failed to start LWIP, return code: %d", ret)
	}

	go func() {
		<-ctx.Done()
		lwip.Stop()

		l.Info("server shut down gracefully")
	}()

	return nil
}

func Stop() {
	if cancelFunc != nil {
		cancelFunc()
	}
}

func GetLogMessages() string {
	mu.Lock()
	defer mu.Unlock()
	if len(logMessages) == 0 {
		return ""
	}
	logs := strings.Join(logMessages, "\n")
	logMessages = nil // Clear logMessages for better memory management
	return logs
}
