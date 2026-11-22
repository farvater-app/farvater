package libv2ray

import (
	"bytes"
	"fmt"
	"io"
	"sync"
	"time"
)

// Keep a bounded sequence of event severities. Destinations and raw messages stay out of Android logs.
type mobileLog struct {
	mu     sync.Mutex
	output io.Writer
	now    func() time.Time
	last   time.Time
	recent [16]byte
	count  uint64
}

func (l *mobileLog) Write(line []byte) (int, error) {
	kind := byte('I')
	switch {
	case bytes.Contains(line, []byte("[Error]")), bytes.Contains(line, []byte("shutdown error:")):
		kind = 'E'
	case bytes.Contains(line, []byte("[Warning]")):
		kind = 'W'
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	if l.last.IsZero() {
		l.last = now
	}
	l.recent[l.count%uint64(len(l.recent))] = kind
	l.count++
	if now.Sub(l.last) >= 30*time.Second {
		n := min(l.count, uint64(len(l.recent)))
		recent := make([]byte, n)
		for i := range recent {
			recent[i] = l.recent[(l.count-n+uint64(i))%uint64(len(l.recent))]
		}
		fmt.Fprintf(l.output, "Xray: %d events, recent severities %s\n", l.count, recent)
		l.count = 0
		l.last = now
	}
	return len(line), nil
}
