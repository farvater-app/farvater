package app.farvater.vpn;

import android.content.Intent;
import app.farvater.vpn.IVpnEndpoint;

oneway interface ITunnelControl {
    void command(IVpnEndpoint endpoint, in Intent intent, int startId);
    void ended(IVpnEndpoint endpoint, boolean revoked);
}
