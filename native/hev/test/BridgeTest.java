import hev.sockstun.TProxyService;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BridgeTest {
    static long descriptors() throws Exception {
        try (var files = Files.list(Path.of("/proc/self/fd"))) { return files.count(); }
    }

    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        Field number = FileDescriptor.class.getDeclaredField("fd");
        number.setAccessible(true);
        byte[] config = "socks5: fixture".getBytes(StandardCharsets.UTF_8);
        long before = descriptors();
        for (int i = 0; i < 100; i++) {
            try (FileInputStream input = new FileInputStream("/dev/null")) {
                int fd = number.getInt(input.getFD());
                if (!TProxyService.TProxyStartServiceFromConfig(config, fd)) throw new AssertionError("start");
                if (TProxyService.TProxyStartServiceFromConfig(config, fd)) throw new AssertionError("duplicate");
            }
            if (!TProxyService.TProxyStopService()) throw new AssertionError("stop");
        }
        if (TProxyService.TProxyGetStats()[0] != 100 || descriptors() != before) throw new AssertionError("lifetime");
        if (TProxyService.TProxyStartServiceFromConfig(config, -1)) throw new AssertionError("invalid fd");
    }
}
