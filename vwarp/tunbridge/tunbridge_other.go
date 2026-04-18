//go:build !linux && !android

package tunbridge

import (
	"context"
	"errors"
	"log/slog"

	"github.com/voidr3aper-anon/Vwarp/wireguard/tun/netstack"
)

// Start is a stub for non-Linux platforms where fdbased endpoint might not be available or needed in this form.
func Start(ctx context.Context, l *slog.Logger, tunFd int, mtu int, tnet *netstack.Net) error {
	return errors.New("tunbridge is not supported on this platform")
}
