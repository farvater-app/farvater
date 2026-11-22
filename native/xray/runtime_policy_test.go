package libv2ray

import (
	"runtime/debug"
	"runtime/metrics"
	"testing"
)

func TestMobileRuntimePolicy(t *testing.T) {
	savedLimit := debug.SetMemoryLimit(-1)
	savedPercent := debug.SetGCPercent(100)
	defer debug.SetMemoryLimit(savedLimit)
	defer debug.SetGCPercent(savedPercent)
	for _, tc := range []struct {
		class                int
		low                  bool
		wantMiB, wantPercent uint64
	}{
		{64, true, 48, 75},
		{256, true, 96, 75},
		{256, false, 128, 100},
		{1024, false, 192, 100},
	} {
		ConfigureMobileRuntime(tc.class, tc.low)
		m := []metrics.Sample{{Name: "/gc/gomemlimit:bytes"}, {Name: "/gc/gogc:percent"}}
		metrics.Read(m)
		if m[0].Value.Uint64() != tc.wantMiB<<20 || m[1].Value.Uint64() != tc.wantPercent {
			t.Errorf("class=%d low=%t: limit=%d gc=%d", tc.class, tc.low, m[0].Value.Uint64(), m[1].Value.Uint64())
		}
	}
}
