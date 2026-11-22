package libv2ray

import (
	"bytes"
	"strings"
	"testing"
	"time"
)

func TestMobileLogBoundsAndRedactsBursts(t *testing.T) {
	var output bytes.Buffer
	now := time.Unix(100, 0)
	sink := mobileLog{output: &output, now: func() time.Time { return now }}
	for i := 0; i < 1000; i++ {
		sink.Write([]byte("[Warning] private.example.test/token"))
	}
	if output.Len() != 0 {
		t.Fatal("burst escaped the aggregation window")
	}
	now = now.Add(30 * time.Second)
	sink.Write([]byte("[Error] token"))
	want := "Xray: 1001 events, recent severities " + strings.Repeat("W", 15) + "E\n"
	if got := output.String(); got != want {
		t.Fatalf("unexpected summary: %q", got)
	}
}
