/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Handler;
import android.preference.PreferenceManager;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.core.VpnStatus.ByteCountListener;

import java.util.LinkedList;
import java.util.Objects;
import java.util.StringTokenizer;

import static de.blinkt.openvpn.core.OpenVPNManagement.pauseReason;

public class DeviceStateReceiver extends BroadcastReceiver implements ByteCountListener, OpenVPNManagement.PausedStateCallback {
    private final Handler mDisconnectHandler;
    private int lastNetwork = -1;
    private OpenVPNManagement mManagement;

    // Window time in s
    private final int TRAFFIC_WINDOW = 60;
    // Data traffic limit in bytes
    private final long TRAFFIC_LIMIT = 64 * 1024;

    // Time to wait after network disconnect to pause the VPN
    private final int DISCONNECT_WAIT = 20;


    private final PauseRunPolicy runPolicy = new PauseRunPolicy();
    private final NetworkDisconnectTimer disconnectTimer;
    private final ReceiverOperationQueue operations;

    private String lastStateMsg = null;
    private NetworkInfo lastConnectedNetwork;

    @Override
    public boolean shouldBeRunning() {
        return shouldBeConnected();
    }

    @Override
    public boolean runIfAllowed(Runnable action) {
        return runPolicy.runIfAllowed(action);
    }

    private static class Datapoint {
        private Datapoint(long t, long d) {
            timestamp = t;
            data = d;
        }

        long timestamp;
        long data;
    }

    private LinkedList<Datapoint> trafficdata = new LinkedList<>();


    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        // VpnStatus calls this under its global monitor; management may log there.
        // Never wait for the operation monitor while holding the VpnStatus monitor.
        operations.submit(() -> disconnectTimer.runIfOpen(() -> processByteCount(diffIn, diffOut)));
    }

    private void processByteCount(long diffIn, long diffOut) {
        if (runPolicy.screen() != PauseRunPolicy.State.PENDINGDISCONNECT)
            return;

        long total = diffIn + diffOut;
        trafficdata.add(new Datapoint(System.currentTimeMillis(), total));

        while (!trafficdata.isEmpty() && trafficdata.getFirst().timestamp <= (System.currentTimeMillis() - TRAFFIC_WINDOW * 1000)) {
            trafficdata.removeFirst();
        }

        long windowtraffic = 0;
        for (Datapoint dp : trafficdata)
            windowtraffic += dp.data;

        if (windowtraffic < TRAFFIC_LIMIT) {
            if (!runPolicy.disconnectPendingScreen()) return;
            VpnStatus.logInfo(R.string.screenoff_pause,
                    "64 kB", TRAFFIC_WINDOW);

            mManagement.pause(getPauseReason());
        }
    }


    public void userPause(boolean pause) {
        operations.submit(() -> disconnectTimer.runIfOpen(() -> {
            PauseRunPolicy.Action action = runPolicy.setUserPaused(pause);
            if (action == PauseRunPolicy.Action.PAUSE) mManagement.pause(getPauseReason());
            else if (action == PauseRunPolicy.Action.RESUME) mManagement.resume();
        }));
    }

    public DeviceStateReceiver(OpenVPNManagement magnagement, Runnable onFailure) {
        super();
        mManagement = magnagement;
        mDisconnectHandler = new Handler();
        operations = new ReceiverOperationQueue(runPolicy, onFailure, 256, 5000);
        disconnectTimer = new NetworkDisconnectTimer(runPolicy, new NetworkDisconnectTimer.Scheduler() {
            @Override public void postDelayed(Runnable callback, long delayMillis) {
                mDisconnectHandler.postDelayed(() -> operations.submit(callback), delayMillis);
            }
            @Override public void remove(Runnable callback) {
                // Wrapper may still fire; timer generation rejects stale callbacks.
            }
        }, DISCONNECT_WAIT * 1000L, () -> mManagement.pause(getPauseReason()));
        mManagement.setPauseCallback(this);
    }

    void unregister() {
        operations.close();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);


        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            networkStateChange(context);
        } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            boolean screenOffPause = prefs.getBoolean("screenoff", false);

            if (screenOffPause) {
                operations.submit(() -> disconnectTimer.runIfOpen(() -> {
                    if (ProfileManager.getLastConnectedVpn() != null && !ProfileManager.getLastConnectedVpn().mPersistTun)
                        VpnStatus.logError(R.string.screen_nopersistenttun);
                    runPolicy.screenOff();
                    fillTrafficData();
                }));
            }
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            // The disconnect timer belongs to network loss, not screen state.
            operations.submit(() -> disconnectTimer.runIfOpen(() -> {
                if (runPolicy.screenOn() == PauseRunPolicy.Action.RESUME)
                    mManagement.resume();
            }));

        }
    }


    private void fillTrafficData() {
        trafficdata.add(new Datapoint(System.currentTimeMillis(), TRAFFIC_LIMIT));
    }

    public static boolean equalsObj(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }


    public void networkStateChange(Context context) {
        Context appContext = context.getApplicationContext();
        operations.submit(() -> disconnectTimer.runIfOpen(() -> processNetworkStateChange(appContext)));
    }

    private void processNetworkStateChange(Context context) {
        NetworkInfo networkInfo = getCurrentNetworkInfo(context);
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);
        boolean sendusr1 = prefs.getBoolean("netchangereconnect", true);


        String netstatestring;
        if (networkInfo == null) {
            netstatestring = "not connected";
        } else {
            String subtype = networkInfo.getSubtypeName();
            if (subtype == null)
                subtype = "";
            String extrainfo = networkInfo.getExtraInfo();
            if (extrainfo == null)
                extrainfo = "";

			/*
            if(networkInfo.getType()==android.net.ConnectivityManager.TYPE_WIFI) {
				WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
				WifiInfo wifiinfo = wifiMgr.getConnectionInfo();
				extrainfo+=wifiinfo.getBSSID();

				subtype += wifiinfo.getNetworkId();
			}*/


            netstatestring = String.format("%2$s %4$s to %1$s %3$s", networkInfo.getTypeName(),
                    networkInfo.getDetailedState(), extrainfo, subtype);
        }

        if (networkInfo != null && networkInfo.getState() == State.CONNECTED) {
            int newnet = networkInfo.getType();

            // Cancel even when another pause condition prevents immediate reconnection.
            NetworkDisconnectTimer.Return result = disconnectTimer.networkReturned();
            if (result == NetworkDisconnectTimer.Return.CLOSED) return;
            boolean pendingDisconnect = result == NetworkDisconnectTimer.Return.PENDING;

            boolean sameNetwork;
            if (lastConnectedNetwork == null
                    || lastConnectedNetwork.getType() != networkInfo.getType()
                    || !equalsObj(lastConnectedNetwork.getExtraInfo(), networkInfo.getExtraInfo())
                    )
                sameNetwork = false;
            else
                sameNetwork = true;

            /* Same network, connection still 'established' */
            if (pendingDisconnect && sameNetwork) {
                // Reprotect the sockets just be sure
                mManagement.networkChange(true);
            } else {
                /* Different network or connection not established anymore */

                runPolicy.disconnectPendingScreen();

                if (shouldBeConnected()) {

                    if (pendingDisconnect || !sameNetwork)
                        mManagement.networkChange(sameNetwork);
                    else
                        mManagement.resume();
                }

                lastNetwork = newnet;
                lastConnectedNetwork = networkInfo;
            }
        } else {
            // Track unavailability even when automatic reconnect is disabled.
            lastNetwork = -1;
            disconnectTimer.networkLost(sendusr1);
        }


        if (!netstatestring.equals(lastStateMsg))
            VpnStatus.logInfo(R.string.netstatus, netstatestring);
        VpnStatus.logDebug(String.format("Debug state info: %s, pause: %s, shouldbeconnected: %s, network: %s ",
                netstatestring, getPauseReason(), shouldBeConnected(), runPolicy.network()));
        lastStateMsg = netstatestring;

    }


    public boolean isUserPaused() {
        return runPolicy.user() == PauseRunPolicy.State.DISCONNECTED;
    }

    private boolean shouldBeConnected() {
        return runPolicy.shouldRun();
    }

    private pauseReason getPauseReason() {
        if (runPolicy.user() == PauseRunPolicy.State.DISCONNECTED)
            return pauseReason.userPause;

        if (runPolicy.screen() == PauseRunPolicy.State.DISCONNECTED)
            return pauseReason.screenOff;

        if (runPolicy.network() == PauseRunPolicy.State.DISCONNECTED)
            return pauseReason.noNetwork;

        return pauseReason.userPause;
    }

    private NetworkInfo getCurrentNetworkInfo(Context context) {
        ConnectivityManager conn = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return conn.getActiveNetworkInfo();
    }
}
