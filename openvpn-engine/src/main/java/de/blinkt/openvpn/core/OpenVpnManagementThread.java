/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import android.system.Os;
import android.util.Log;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

public class OpenVpnManagementThread implements Runnable, OpenVPNManagement {

    public static final int ORBOT_TIMEOUT_MS = 20 * 1000;
    private static final String TAG = "openvpn";
    private final Handler mResumeHandler;
    private LocalSocket mSocket;
    private VpnProfile mProfile;
    private OpenVPNService mOpenVPNService;
    private LinkedList<FileDescriptor> mFDList = new LinkedList<>();
    private LocalServerSocket mServerSocket;
    private final HoldReleaseController holdController;
    private LocalSocket mServerSocketLocal;

    private volatile pauseReason lastPauseReason = pauseReason.noNetwork;
    private volatile PausedStateCallback mPauseCallback;
    private final Object mPauseCallbackLock = new Object();
    private boolean mShuttingDown;
    private final Runnable mResumeHoldRunnable = () -> releaseHold();
    private Runnable orbotStatusTimeOutRunnable = new Runnable() {
        @Override
        public void run() {
            sendProxyCMD(Connection.ProxyType.SOCKS5, "127.0.0.1", Integer.toString(OrbotHelper.SOCKS_PROXY_PORT_DEFAULT), false);
            OrbotHelper.get(mOpenVPNService).removeStatusCallback(statusCallback);

        }
    };
    private OrbotHelper.StatusCallback statusCallback = new OrbotHelper.StatusCallback() {

        @Override
        public void onStatus(Intent statusIntent) {
            StringBuilder extras = new StringBuilder();
            for (String key : statusIntent.getExtras().keySet()) {
                Object val = statusIntent.getExtras().get(key);

                extras.append(String.format(Locale.ENGLISH, "%s - '%s'", key, val == null ? "null" : val.toString()));
            }
            VpnStatus.logDebug("Got Orbot status: " + extras);
        }

        @Override
        public void onNotYetInstalled() {
            VpnStatus.logDebug("Orbot not yet installed");
        }

        @Override
        public void onOrbotReady(Intent intent, String socksHost, int socksPort) {
            mResumeHandler.removeCallbacks(orbotStatusTimeOutRunnable);
            sendProxyCMD(Connection.ProxyType.SOCKS5, socksHost, Integer.toString(socksPort), false);
            OrbotHelper.get(mOpenVPNService).removeStatusCallback(this);
        }

        @Override
        public void onDisabled(Intent intent) {
            VpnStatus.logWarning("Orbot integration for external applications is disabled. Waiting %ds before connecting to the default port. Enable external app integration in Orbot or use Socks v5 config instead of Orbot to avoid this delay.");
        }
    };
    private transient Connection mCurrentProxyConnection;
    // Bound to the socket producer, not the global selected profile.
    private final String vegasSessionId;
    private final String vegasSessionNonce;
    private final SessionPauseEvents vegasPauseEvents;

    public OpenVpnManagementThread(VpnProfile profile, OpenVPNService openVpnService, String sessionNonce) {
        mProfile = profile;
        vegasSessionId = profile.getUUIDString();
        vegasSessionNonce = sessionNonce;
        vegasPauseEvents = new SessionPauseEvents(vegasSessionId, vegasSessionNonce);
        mOpenVPNService = openVpnService;
        mResumeHandler = new Handler(openVpnService.getMainLooper());
        holdController = new HoldReleaseController(action -> {
            synchronized (mPauseCallbackLock) {
                PausedStateCallback callback = mPauseCallback;
                return callback != null && callback.runIfAllowed(action);
            }
        }, android.os.SystemClock::elapsedRealtime, delay -> {
            mResumeHandler.removeCallbacks(mResumeHoldRunnable);
            mResumeHandler.postDelayed(mResumeHoldRunnable, delay);
        }, new HoldReleaseController.Commands() {
            @Override public boolean release() {
                if (!managmentCommand("hold release\n")) return false;
                managmentCommand("bytecount " + mBytecountInterval + "\n");
                managmentCommand("state on\n");
                return true;
            }
            @Override public void networkChange(boolean sameNetwork) {
                managmentCommand(sameNetwork ? "network-change samenetwork\n" : "network-change\n");
            }
        });

    }

    public boolean openManagementInterface(@NonNull Context c) {
        // Could take a while to open connection
        int tries = 8;

        String socketName = (c.getCacheDir().getAbsolutePath() + "/" + "mgmtsocket");
        // The mServerSocketLocal is transferred to the LocalServerSocket, ignore warning

        mServerSocketLocal = new LocalSocket();

        while (tries > 0 && !mServerSocketLocal.isBound()) {
            try {
                mServerSocketLocal.bind(new LocalSocketAddress(socketName,
                        LocalSocketAddress.Namespace.FILESYSTEM));
            } catch (IOException e) {
                // wait 300 ms before retrying
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }

            }
            tries--;
        }

        try {

            mServerSocket = new LocalServerSocket(mServerSocketLocal.getFileDescriptor());
            return true;
        } catch (IOException e) {
            VpnStatus.logException(e);
        }
        return false;


    }

    /**
     * @param cmd command to write to management socket
     * @return true if command have been sent
     */
    public boolean managmentCommand(String cmd) {
        try {
            if (mSocket != null && mSocket.getOutputStream() != null) {
                mSocket.getOutputStream().write(cmd.getBytes());
                mSocket.getOutputStream().flush();
                return true;
            }
        } catch (IOException e) {
            // Ignore socket stack traces
        }
        return false;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[2048];
        //	mSocket.setSoTimeout(5); // Setting a timeout cannot be that bad

        String pendingInput = "";

        try {
            // Wait for a client to connect
            mSocket = mServerSocket.accept();
            InputStream instream = mSocket.getInputStream();


            // Close the management socket after client connected
            try {
                mServerSocket.close();
            } catch (IOException e) {
                VpnStatus.logException(e);
            }

            // Closing one of the two sockets also closes the other
            //mServerSocketLocal.close();
            managmentCommand("version 3\n");

            while (true) {

                int numbytesread = instream.read(buffer);
                if (numbytesread == -1)
                    return;

                FileDescriptor[] fds = null;
                try {
                    fds = mSocket.getAncillaryFileDescriptors();
                } catch (IOException e) {
                    VpnStatus.logException("Error reading fds from socket", e);
                }
                if (fds != null) {
                    Collections.addAll(mFDList, fds);
                }

                String input = new String(buffer, 0, numbytesread, "UTF-8");

                pendingInput += input;

                pendingInput = processInput(pendingInput);


            }
        } catch (IOException e) {
            if (!"socket closed".equals(e.getMessage()) && !"Connection reset by peer".equals(e.getMessage()))
                VpnStatus.logException(e);
        } finally {
            closeSession();
        }
    }

    // Stop can arrive before the management client has connected.
    void closeSession() {
        mResumeHandler.removeCallbacks(mResumeHoldRunnable);
        mResumeHandler.removeCallbacks(orbotStatusTimeOutRunnable);
        try { if (mSocket != null) mSocket.close(); } catch (IOException ignored) { }
        try { if (mServerSocket != null) mServerSocket.close(); } catch (IOException ignored) { }
        try { if (mServerSocketLocal != null) mServerSocketLocal.close(); } catch (IOException ignored) { }
    }

    private boolean protectFileDescriptor(FileDescriptor fd) {
        // Use public Android APIs, not blocked hidden FileDescriptor.getInt$ reflection.
        // dup() owns a second fd, closed before the received SCM_RIGHTS fd by the handler.
        boolean result = ProtectFdHandler.protect(fd, received -> {
            try (ParcelFileDescriptor duplicate = ParcelFileDescriptor.dup(received)) {
                int number = duplicate.getFd();
                return number >= 0 && mOpenVPNService.protect(number);
            }
        }, Os::close);
        if (!result) VpnStatus.logError("Could not protect VPN socket; stopping session");
        return result;
    }

    private void failSocketProtection() {
        mShuttingDown = true;
        mOpenVPNService.requestStopForManagement(this);
        SessionVpnStatus.emit(vegasSessionId, vegasSessionNonce, SessionVpnStatus.Event.PROTECT_FAILED);
    }

    private String processInput(String pendingInput) {


        while (pendingInput.contains("\n")) {
            String[] tokens = pendingInput.split("\\r?\\n", 2);
            processCommand(tokens[0]);
            if (tokens.length == 1)
                // No second part, newline was at the end
                pendingInput = "";
            else
                pendingInput = tokens[1];
        }
        return pendingInput;
    }

    private void processCommand(String command) {
        //Log.i(TAG, "Line from managment" + command);

        if (command.startsWith(">") && command.contains(":")) {
            String[] parts = command.split(":", 2);
            String cmd = parts[0].substring(1);
            String argument = parts[1];

            switch (cmd) {
                case "INFO":
                /* Ignore greeting from management */
                    return;
                case "PASSWORD":
                    processPWCommand(argument);
                    break;
                case "HOLD":
                    handleHold(argument);
                    break;
                case "NEED-OK":
                    processNeedCommand(argument);
                    break;
                case "BYTECOUNT":
                    processByteCount(argument);
                    break;
                case "STATE":
                    if (!mShuttingDown)
                        processState(argument);
                    break;
                case "PROXY":
                    processProxyCMD(argument);
                    break;
                case "LOG":
                    processLogMessage(argument);
                    break;
                case "PK_SIGN":
                    processSignCommand(argument);
                    break;
                case "INFOMSG":
                    processInfoMessage(argument);
                    break;
                default:
                    VpnStatus.logWarning("MGMT: Got unrecognized command" + command);
                    Log.i(TAG, "Got unrecognized command" + command);
                    break;
            }
        } else if (command.startsWith("SUCCESS:")) {
            /* Ignore this kind of message too */
            return;
        } else if (command.startsWith("PROTECTFD: ")) {
            FileDescriptor fdtoprotect = mFDList.pollFirst();
            // This unsolicited network-change notification has no needok reply.
            if (!protectFileDescriptor(fdtoprotect)) failSocketProtection();
        } else {
            Log.i(TAG, "Got unrecognized line from managment" + command);
            VpnStatus.logWarning("MGMT: Got unrecognized line from management:" + command);
        }
    }

    private void processInfoMessage(String info)
    {
        if (info.startsWith("OPEN_URL:") || info.startsWith("CR_TEXT:"))
        {
            mOpenVPNService.trigger_sso(info);
        }
        else
        {
            VpnStatus.logDebug("Info message from server:" + info);
        }
    }

    private void processLogMessage(String argument) {
        String[] args = argument.split(",", 4);
        // 0 unix time stamp
        // 1 log level N,I,E etc.
                /*
                  (b) zero or more message flags in a single string:
          I -- informational
          F -- fatal error
          N -- non-fatal error
          W -- warning
          D -- debug, and
                 */
        // 2 log message

        Log.d("OpenVPN", argument);

        VpnStatus.LogLevel level;
        switch (args[1]) {
            case "I":
                level = VpnStatus.LogLevel.INFO;
                break;
            case "W":
                level = VpnStatus.LogLevel.WARNING;
                break;
            case "D":
                level = VpnStatus.LogLevel.VERBOSE;
                break;
            case "F":
                level = VpnStatus.LogLevel.ERROR;
                break;
            default:
                level = VpnStatus.LogLevel.INFO;
                break;
        }

        int ovpnlevel = Integer.parseInt(args[2]) & 0x0F;
        String msg = args[3];

        if (msg.startsWith("MANAGEMENT: CMD"))
            ovpnlevel = Math.max(4, ovpnlevel);

        VpnStatus.logMessageOpenVPN(level, ovpnlevel, msg);
    }

    boolean shouldBeRunning() {
        PausedStateCallback callback = mPauseCallback;
        return callback != null && callback.shouldBeRunning();
    }

    private void handleHold(String argument) {
        holdController.hold();
        int waittime = Integer.parseInt(argument.split(":")[1]);
        if (shouldBeRunning()) {
            if (waittime > 1)
                VpnStatus.updateStateString("CONNECTRETRY", String.valueOf(waittime),
                        R.string.state_waitconnectretry, ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET);
            mResumeHandler.postDelayed(mResumeHoldRunnable, waittime * 1000);
            if (waittime > 5)
                VpnStatus.logInfo(R.string.state_waitconnectretry, String.valueOf(waittime));
            else
                VpnStatus.logDebug(R.string.state_waitconnectretry, String.valueOf(waittime));

        } else {
            vegasPauseEvents.pause(lastPauseReason);
            VpnStatus.updateStatePause(lastPauseReason);
        }
    }

    public void releaseHold() {
        holdController.releaseIfAllowed();
    }

    private void processProxyCMD(String argument) {
        String[] args = argument.split(",", 3);

        Connection.ProxyType proxyType = Connection.ProxyType.NONE;

        int connectionEntryNumber = Integer.parseInt(args[0]) - 1;
        String proxyport = null;
        String proxyname = null;
        boolean proxyUseAuth = false;

        if (mProfile.mConnections.length > connectionEntryNumber) {
            Connection connection = mProfile.mConnections[connectionEntryNumber];
            proxyType = connection.mProxyType;
            proxyname = connection.mProxyName;
            proxyport = connection.mProxyPort;
            proxyUseAuth = connection.mUseProxyAuth;

            // Use transient variable to remember http user/password
            mCurrentProxyConnection = connection;

        } else {
            VpnStatus.logError(String.format(Locale.ENGLISH, "OpenVPN is asking for a proxy of an unknown connection entry (%d)", connectionEntryNumber));
        }

        // atuo detection of proxy
        if (proxyType == Connection.ProxyType.NONE) {
            SocketAddress proxyaddr = ProxyDetection.detectProxy(mProfile);
            if (proxyaddr instanceof InetSocketAddress) {
                InetSocketAddress isa = (InetSocketAddress) proxyaddr;
                proxyType = Connection.ProxyType.HTTP;
                proxyname = isa.getHostName();
                proxyport = String.valueOf(isa.getPort());
                proxyUseAuth = false;

            }
        }


        if (args.length >= 2 && proxyType == Connection.ProxyType.HTTP) {
            String proto = args[1];
            if (proto.equals("UDP")) {
                proxyname = null;
                VpnStatus.logInfo("Not using an HTTP proxy since the connection uses UDP");
            }
        }


        if (proxyType == Connection.ProxyType.ORBOT) {
            VpnStatus.updateStateString("WAIT_ORBOT", "Waiting for Orbot to start", R.string.state_waitorbot, ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET);
            OrbotHelper orbotHelper = OrbotHelper.get(mOpenVPNService);
            if (!orbotHelper.checkTorReceier(mOpenVPNService))
                VpnStatus.logError("Orbot does not seem to be installed!");

            mResumeHandler.postDelayed(orbotStatusTimeOutRunnable, ORBOT_TIMEOUT_MS);
            orbotHelper.addStatusCallback(mOpenVPNService, statusCallback);

            orbotHelper.sendOrbotStartAndStatusBroadcast();

        } else {
            sendProxyCMD(proxyType, proxyname, proxyport, proxyUseAuth);
        }
    }

    private void sendProxyCMD(Connection.ProxyType proxyType, String proxyname, String proxyport, boolean usePwAuth) {
        if (proxyType != Connection.ProxyType.NONE && proxyname != null) {

            VpnStatus.logInfo(R.string.using_proxy, proxyname, proxyname);

            String pwstr =  usePwAuth ? " auto" : "";

            String proxycmd = String.format(Locale.ENGLISH, "proxy %s %s %s%s\n",
                    proxyType == Connection.ProxyType.HTTP ? "HTTP" : "SOCKS",
                    proxyname, proxyport, pwstr);
            managmentCommand(proxycmd);
        } else {
            managmentCommand("proxy NONE\n");
        }
    }

    private void processState(String argument) {
        String[] args = argument.split(",", 3);
        if (args.length < 3) return;
        String currentstate = args[1];
        if ("CONNECTED".equals(currentstate))
            vegasPauseEvents.progress(SessionVpnStatus.Event.CONNECTED);
        else if ("CONNECTING".equals(currentstate) || "WAIT".equals(currentstate) ||
                "RECONNECTING".equals(currentstate) || "RESOLVE".equals(currentstate) ||
                "TCP_CONNECT".equals(currentstate) || "AUTH".equals(currentstate) ||
                "GET_CONFIG".equals(currentstate) || "ASSIGN_IP".equals(currentstate) ||
                "ADD_ROUTES".equals(currentstate) || "AUTH_PENDING".equals(currentstate))
            vegasPauseEvents.progress(SessionVpnStatus.Event.CONNECTING);

        if (args[2].equals(",,"))
            VpnStatus.updateStateString(currentstate, "");
        else
            VpnStatus.updateStateString(currentstate, args[2]);
    }

    private void processByteCount(String argument) {
        //   >BYTECOUNT:{BYTES_IN},{BYTES_OUT}
        int comma = argument.indexOf(',');
        long in = Long.parseLong(argument.substring(0, comma));
        long out = Long.parseLong(argument.substring(comma + 1));

        VpnStatus.updateByteCount(in, out);

    }

    private void processNeedCommand(String argument) {
        int p1 = argument.indexOf('\'');
        int p2 = argument.indexOf('\'', p1 + 1);

        String needed = argument.substring(p1 + 1, p2);
        String extra = argument.split(":", 2)[1];

        String status = "ok";


        switch (needed) {
            case "PROTECTFD":
                FileDescriptor fdtoprotect = mFDList.pollFirst();
                ProtectFdHandler.needOk(protectFileDescriptor(fdtoprotect),
                        result -> managmentCommand("needok 'PROTECTFD' " + result + "\n"),
                        this::failSocketProtection);
                return;
            case "DNSSERVER":
            case "DNS6SERVER":
                mOpenVPNService.addDNS(extra);
                break;
            case "DNSDOMAIN":
                mOpenVPNService.setDomain(extra);
                break;
            case "ROUTE": {
                String[] routeparts = extra.split(" ");

            /*
            buf_printf (&out, "%s %s %s dev %s", network, netmask, gateway, rgi->iface);
            else
            buf_printf (&out, "%s %s %s", network, netmask, gateway);
            */

                if (routeparts.length == 5) {
                    //if (BuildConfig.DEBUG)
                    //                assertEquals("dev", routeparts[3]);
                    mOpenVPNService.addRoute(routeparts[0], routeparts[1], routeparts[2], routeparts[4]);
                } else if (routeparts.length >= 3) {
                    mOpenVPNService.addRoute(routeparts[0], routeparts[1], routeparts[2], null);
                } else {
                    VpnStatus.logError("Unrecognized ROUTE cmd:" + Arrays.toString(routeparts) + " | " + argument);
                }

                break;
            }
            case "ROUTE6": {
                String[] routeparts = extra.split(" ");
                mOpenVPNService.addRoutev6(routeparts[0], routeparts[1]);
                break;
            }
            case "IFCONFIG":
                String[] ifconfigparts = extra.split(" ");
                int mtu = Integer.parseInt(ifconfigparts[2]);
                mOpenVPNService.setLocalIP(ifconfigparts[0], ifconfigparts[1], mtu, ifconfigparts[3]);
                break;
            case "IFCONFIG6":
                String[] ifconfig6parts = extra.split(" ");
                mtu = Integer.parseInt(ifconfig6parts[1]);
                mOpenVPNService.setMtu(mtu);
                mOpenVPNService.setLocalIPv6(ifconfig6parts[0]);
                break;
            case "PERSIST_TUN_ACTION":
                // check if tun cfg stayed the same
                status = mOpenVPNService.getTunReopenStatus();
                break;
            case "OPENTUN":
                if (sendTunFD(needed, extra))
                    return;
                else {
                    status = "cancel";
                    failSocketProtection();
                }

                break;
            default:
                Log.e(TAG, "Unknown needok command " + argument);
                return;
        }

        String cmd = String.format("needok '%s' %s\n", needed, status);
        managmentCommand(cmd);
    }

    private boolean sendTunFD(String needed, String extra) {
        if (!extra.equals("tun")) {
            // We only support tun
            VpnStatus.logError(String.format("Device type %s requested, but only tun is possible with the Android API, sorry!", extra));

            return false;
        }
        ParcelFileDescriptor pfd = mOpenVPNService.openTun();
        if (pfd == null)
            return false;

        boolean sent = false;
        try {
            // ParcelFileDescriptor exposes its owned FileDescriptor through a public API.
            mSocket.setFileDescriptorsForSend(new FileDescriptor[]{pfd.getFileDescriptor()});
            sent = managmentCommand(String.format("needok '%s' %s\n", needed, "ok"));
        } catch (Exception exp) {
            VpnStatus.logException("Could not send tun fd over socket", exp);
            sent = false;
        } finally {
            // Never leak this descriptor or attach it accidentally to a later management write.
            try { mSocket.setFileDescriptorsForSend(null); } catch (Exception e) { sent = false; }
            try { pfd.close(); } catch (Exception e) { sent = false; }
        }
        return sent;
    }

    private void processPWCommand(String argument) {
        //argument has the form 	Need 'Private Key' password
        // or  ">PASSWORD:Verification Failed: '%s' ['%s']"
        String needed;


        try {
            // Ignore Auth token message, already managed by openvpn itself
            if (argument.startsWith("Auth-Token:")) {
                return;
            }

            int p1 = argument.indexOf('\'');
            int p2 = argument.indexOf('\'', p1 + 1);
            needed = argument.substring(p1 + 1, p2);
            if (argument.startsWith("Verification Failed")) {
                proccessPWFailed(needed, argument.substring(p2 + 1));
                return;
            }
        } catch (StringIndexOutOfBoundsException sioob) {
            VpnStatus.logError("Could not parse management Password command: " + argument);
            return;
        }

        String pw = null;
        String username = null;

        if (needed.equals("Private Key")) {
            pw = mProfile.getPasswordPrivateKey();
        } else if (needed.equals("Auth")) {
            pw = mProfile.getPasswordAuth();
            username = mProfile.mUsername;

        } else if (needed.equals("HTTP Proxy")) {
            if( mCurrentProxyConnection != null) {
                pw = mCurrentProxyConnection.mProxyAuthPassword;
                username = mCurrentProxyConnection.mProxyAuthUser;
            }
        }
        if (pw != null) {
            if (username !=null) {
                String usercmd = String.format("username '%s' %s\n",
                        needed, VpnProfile.openVpnEscape(username));
                managmentCommand(usercmd);
            }
            String cmd = String.format("password '%s' %s\n", needed, VpnProfile.openVpnEscape(pw));
            managmentCommand(cmd);
        } else {
            SessionVpnStatus.emit(vegasSessionId, vegasSessionNonce, SessionVpnStatus.Event.INPUT_REQUIRED);
            mOpenVPNService.requestInputFromUser(R.string.password, needed);
            VpnStatus.logError(String.format("Openvpn requires Authentication type '%s' but no password/key information available", needed));
        }

    }

    private void proccessPWFailed(String needed, String args) {
        SessionVpnStatus.emit(vegasSessionId, vegasSessionNonce, SessionVpnStatus.Event.AUTH_FAILED);
        VpnStatus.updateStateString("AUTH_FAILED", needed + args, R.string.state_auth_failed, ConnectionStatus.LEVEL_AUTH_FAILED);
    }

    @Override
    public void networkChange(boolean samenetwork) {
        if (holdController.networkChange(samenetwork) && shouldBeRunning())
            vegasPauseEvents.resume();
    }

    @Override
    public void setPauseCallback(PausedStateCallback callback) {
        synchronized (mPauseCallbackLock) {
            mPauseCallback = callback;
        }
    }

    @Override
    public void sendCRResponse(String response) {
        managmentCommand("cr-response "  + response + "\n");
    }

    public void signalusr1() {
        mResumeHandler.removeCallbacks(mResumeHoldRunnable);
        if (!holdController.waiting()) {
            if (!managmentCommand("signal SIGUSR1\n"))
                VpnStatus.logError("OpenVPN management: SIGUSR1 write failed; pause/reconnect is not confirmed");
        } else
            // If signalusr1 is called update the state string
            // if there is another for stopping
            VpnStatus.updateStatePause(lastPauseReason);
    }

    public void reconnect() {
        signalusr1();
        releaseHold();
    }

    private void processSignCommand(String argument) {

        String[] arguments = argument.split(",");

        boolean pkcs1padding = arguments[1].equals("RSA_PKCS1_PADDING");
        String signed_string = mProfile.getSignedData(mOpenVPNService, arguments[0], pkcs1padding);

        if (signed_string == null) {
            managmentCommand("pk-sig\n");
            managmentCommand("\nEND\n");
            mOpenVPNService.requestStopForManagement(this);
            return;
        }
        managmentCommand("pk-sig\n");
        managmentCommand(signed_string);
        managmentCommand("\nEND\n");
    }

    @Override
    public void pause(pauseReason reason) {
        lastPauseReason = reason;
        vegasPauseEvents.pause(reason);
        signalusr1();
    }

    @Override
    public void resume() {
        if (!shouldBeRunning()) return;
        vegasPauseEvents.resume();
        releaseHold();
        /* Reset the reason why we are disconnected */
        lastPauseReason = pauseReason.noNetwork;
    }

    @Override
    public boolean stopVPN(boolean replaceConnection) {
        boolean stopSucceed = managmentCommand("signal SIGINT\n");
        closeSession();
        if (stopSucceed) {
            mShuttingDown = true;
            if (!replaceConnection) {
                // The management connection is closed right away, so OpenVPN's own
                // EXITING state is never read: announce the deliberate stop, so that
                // listeners can tell it from OpenVPN exiting on an error.
                VpnStatus.updateStateString("EXITING", "SIGINT", R.string.state_exiting,
                        ConnectionStatus.LEVEL_NOTCONNECTED);
            }
        }
        return stopSucceed;
    }

}
