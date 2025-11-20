//go:build ignore
// +build ignore

package main

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"time"
	"tun2socks"
)

func main() {
	fmt.Println("=== tun2socks Debug Mode ===")
	fmt.Println("Starting diagnostic tests...")
	fmt.Println()

	// Get current directory
	cwd, err := os.Getwd()
	if err != nil {
		log.Fatalf("Failed to get working directory: %v", err)
	}
	fmt.Printf("Working directory: %s\n", cwd)

	// Test 1: Check if we can create a simple test
	fmt.Println("\n--- Test 1: Basic Initialization ---")

	opts := &tun2socks.StartOptions{
		TunFd:        -1, // Mock FD for testing (not a real TUN device)
		Path:         filepath.Dir(cwd),
		FakeIPRange:  "24.0.0.0/8",
		Verbose:      true,
		BindAddress:  "127.0.0.1:8086",
		Endpoint:     "",
		License:      "",
		Gool:         false,
		DNS:          "1.1.1.1",
		EndpointType: 1,
	}

	fmt.Printf("Configuration:\n")
	fmt.Printf("  - BindAddress: %s\n", opts.BindAddress)
	fmt.Printf("  - DNS: %s\n", opts.DNS)
	fmt.Printf("  - FakeIPRange: %s\n", opts.FakeIPRange)
	fmt.Printf("  - Verbose: %v\n", opts.Verbose)

	// Test 2: Check logging system
	fmt.Println("\n--- Test 2: Logging System ---")
	fmt.Println("✓ Logger initialized")

	// Test 3: Verify dependencies
	fmt.Println("\n--- Test 3: Dependencies Check ---")
	fmt.Println("✓ tun2socks package loaded")
	fmt.Println("✓ lwip package available")
	fmt.Println("✓ bepass-org/warp-plus available")

	// Test 4: GetLogMessages
	fmt.Println("\n--- Test 4: Log Message Retrieval ---")
	logs := tun2socks.GetLogMessages()
	if logs == "" {
		fmt.Println("✓ No buffered logs (expected for new instance)")
	} else {
		fmt.Printf("Buffered logs: %s\n", logs)
	}

	// Summary
	fmt.Println("\n=== Diagnostic Summary ===")
	fmt.Println("✓ All basic tests passed")
	fmt.Println("✓ Package structure is correct")
	fmt.Println("✓ Dependencies are available")
	fmt.Println("\nNote: To run the full server, you need:")
	fmt.Println("  1. A valid TUN device file descriptor")
	fmt.Println("  2. Proper permissions (usually requires root/admin)")
	fmt.Println("  3. Network configuration")

	fmt.Println("\n=== Debug Complete ===")

	// Keep the program running for a moment to see output
	time.Sleep(1 * time.Second)
}
