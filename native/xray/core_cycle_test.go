//go:build linux && xrayintegration

package libv2ray

import (
	"bytes"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"runtime"
	"testing"
	"time"

	"golang.org/x/net/proxy"
)

type cycleEvents struct{ starts, stops int }

func (e *cycleEvents) Startup() int               { e.starts++; return 0 }
func (e *cycleEvents) Shutdown() int              { e.stops++; return 0 }
func (*cycleEvents) OnEmitStatus(int, string) int { return 0 }

func TestMobileCoreCycles(t *testing.T) {
	ConfigureMobileRuntime(128, true)
	payload := bytes.Repeat([]byte("farvater"), 32768)
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.Write(payload) }))
	defer target.Close()
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	listener.Close()
	configuration := fmt.Sprintf(`{"log":{"loglevel":"warning"},"inbounds":[{"listen":"127.0.0.1","port":%d,"protocol":"socks","settings":{"auth":"password","accounts":[{"user":"fixture","pass":"offline-only"}]}}],"outbounds":[{"protocol":"freedom"}]}`, port)
	dial, err := proxy.SOCKS5("tcp", fmt.Sprintf("127.0.0.1:%d", port), &proxy.Auth{User: "fixture", Password: "offline-only"}, &net.Dialer{Timeout: 3 * time.Second})
	if err != nil {
		t.Fatal(err)
	}
	client := &http.Client{Timeout: 5 * time.Second, Transport: &http.Transport{Dial: dial.Dial, DisableKeepAlives: true}}
	events := new(cycleEvents)
	sample := func() (int, uint64) {
		runtime.GC()
		var stats runtime.MemStats
		runtime.ReadMemStats(&stats)
		files, err := os.ReadDir("/proc/self/fd")
		if err != nil {
			t.Fatal(err)
		}
		return len(files), stats.HeapAlloc
	}
	idleFD, _ := sample()
	sampleAfterStop := func() (int, uint64) {
		// Linux TCP splice pipes live in a Go pool. Allow GC and its asynchronous
		// cleanups to release them, while still detecting a retained descriptor.
		deadline := time.Now().Add(2 * time.Second)
		for {
			fd, heap := sample()
			if fd <= idleFD || !time.Now().Before(deadline) {
				return fd, heap
			}
			time.Sleep(10 * time.Millisecond)
		}
	}
	var baseFD int
	var baseHeap uint64
	const cycles = 60
	for i := 0; i < cycles; i++ {
		core := NewCoreController(events)
		if err := core.StartLoop(configuration, 0); err != nil {
			t.Fatal(err)
		}
		if err := core.StartLoop(configuration, 0); err != nil {
			t.Fatal(err)
		}
		res, err := client.Get(target.URL)
		if err != nil {
			core.StopLoop()
			t.Fatal(err)
		}
		data, err := io.ReadAll(res.Body)
		res.Body.Close()
		if err != nil || !bytes.Equal(data, payload) {
			core.StopLoop()
			t.Fatal("incorrect loopback payload", err)
		}
		if err := core.StopLoop(); err != nil {
			t.Fatal(err)
		}
		if err := core.StopLoop(); err != nil {
			t.Fatal(err)
		}
		if i == 9 {
			baseFD, baseHeap = sampleAfterStop()
		}
	}
	endFD, endHeap := sampleAfterStop()
	t.Logf("%d cycles, %d bytes, descriptors %d -> %d, live Go heap %d -> %d bytes",
		cycles, cycles*len(payload), baseFD, endFD, baseHeap, endHeap)
	if events.starts != cycles || events.stops != cycles {
		t.Fatalf("callbacks: starts=%d stops=%d", events.starts, events.stops)
	}
	if baseFD > idleFD || endFD > idleFD || endHeap > baseHeap+4<<20 {
		t.Fatal("resources continued to grow after warm-up")
	}
}
