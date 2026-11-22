package libv2ray

import (
	"log"
	"runtime/debug"
	"sync"
	"time"
)

var configureLog sync.Once

func ConfigureMobileRuntime(memoryClassMiB int, lowRAM bool) {
	limitMiB := min(max(memoryClassMiB/2, 48), 192)
	gcPercent := 100
	if lowRAM {
		limitMiB = min(limitMiB, 96)
		gcPercent = 75
	}
	debug.SetMemoryLimit(int64(limitMiB) << 20)
	debug.SetGCPercent(gcPercent)
	configureLog.Do(func() {
		log.SetOutput(&mobileLog{output: log.Writer(), now: time.Now})
	})
}
