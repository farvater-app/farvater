package hev.sockstun;

public final class TProxyService {
    private TProxyService() {}

    public static native boolean TProxyStartServiceFromConfig(byte[] config, int fd);

    public static native boolean TProxyStopService();

    public static native boolean TProxyIsRunning();

    public static native long[] TProxyGetStats();
}
