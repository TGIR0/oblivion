package tun2socks

import (
	"fmt"
	"strings"
	"sync"
)

var (
	mu          sync.Mutex
	logMessages []string
)

// StartOptions keeps the public gomobile surface while the tunnel core is absent.
type StartOptions struct {
	TunFd                int
	Path                 string
	FakeIPRange          string
	MTU                  int
	Verbose              bool
	BindAddress          string
	Endpoint             string
	License              string
	Gool                 bool
	Masque               bool
	MasquePreferred      bool
	MasqueNoize          bool
	MasqueNoizePreset    string
	Region               int
	DNS                  string
	EndpointType         int
	AnycastIPs           string
	PreferredFingerprint string
	PsiphonCountry       string
	PsiphonConduit       bool
}

// Start preserves the old exported API and fails explicitly until a new core lands.
func Start(opt *StartOptions) error {
	appendLog(fmt.Sprintf("tun2socks core removed; start ignored: %+v", opt))
	return fmt.Errorf("tun2socks core is not available")
}

// Stop is a no-op placeholder.
func Stop() {
	appendLog("tun2socks core removed; stop ignored")
}

// GetLogMessages retrieves placeholder log messages and clears the buffer.
func GetLogMessages() string {
	mu.Lock()
	defer mu.Unlock()
	if len(logMessages) == 0 {
		return ""
	}
	logs := strings.Join(logMessages, "\n")
	logMessages = nil
	return logs
}

func appendLog(message string) {
	mu.Lock()
	defer mu.Unlock()
	logMessages = append(logMessages, message)
}
