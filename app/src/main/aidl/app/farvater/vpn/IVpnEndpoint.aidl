package app.farvater.vpn;

import android.app.Notification;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface IVpnEndpoint {
    int startCore(in Bundle configuration);
    void stopCore();
    ParcelFileDescriptor establish(String label, int mtu, boolean ipv6, in String[] excluded);
    boolean startBridge(in ParcelFileDescriptor tun, in byte[] configuration);
    void stopBridge();
    long[] counters();
    int vpnPolicy();
    void foreground(in Notification notification);
    oneway void received(int startId);
    void finishStart(int startId);
}
