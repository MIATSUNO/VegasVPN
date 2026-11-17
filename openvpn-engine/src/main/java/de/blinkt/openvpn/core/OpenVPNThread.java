/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.blinkt.openvpn.R;

public class OpenVPNThread implements Runnable {
    private static final String DUMP_PATH_STRING = "Dump path: ";
    @SuppressLint("SdCardPath")
    private static final String BROKEN_PIE_SUPPORT = "/data/data/de.blinkt.openvpn/cache/pievpn";
    private final static String BROKEN_PIE_SUPPORT2 = "syntax error";
    private static final String TAG = "OpenVPN";
    // 1380308330.240114 18000002 Send to HTTP proxy: 'X-Online-Host: bla.blabla.com'
    private static final Pattern LOG_PATTERN = Pattern.compile("(\\d+).(\\d+) ([0-9a-f])+ (.*)");
    public static final int M_FATAL = (1 << 4);
    public static final int M_NONFATAL = (1 << 5);
    public static final int M_WARN = (1 << 6);
    public static final int M_DEBUG = (1 << 7);
    private String[] mArgv;
    private volatile Process mProcess;
    private String mNativeDir;
    private String mTmpDir;
    private OpenVPNService mService;
    private static volatile OpenVPNThread lastThread;
    private String mDumpPath;
    private boolean mBrokenPie = false;
    private boolean mNoProcessExitStatus = false;
    private String vegasSessionId;
    private String vegasSessionNonce;
    private final ProcessStartGate processStartGate = new ProcessStartGate();

    public OpenVPNThread(OpenVPNService service, String[] argv, String nativelibdir, String tmpdir) {
        mArgv = argv;
        mNativeDir = nativelibdir;
        mTmpDir = tmpdir;
        mService = service;
        lastThread = this;
    }

    public OpenVPNThread(OpenVPNService service, String[] argv, String nativelibdir, String tmpdir, String sessionId, String sessionNonce) {
        this(service, argv, nativelibdir, tmpdir);
        vegasSessionId = sessionId;
        vegasSessionNonce = sessionNonce;
    }

    public OpenVPNThread() {
    }

    public void stopProcess() {
        processStartGate.stop();
    }

    void setReplaceConnection() {
        mNoProcessExitStatus = true;
    }

    @Override
    public void run() {
        try {
            Log.i(TAG, "Starting openvpn");
            startOpenVPNThreadArgs(mArgv);
            Log.i(TAG, "OpenVPN reader finished; checking process outcome");
        } catch (Exception e) {
            VpnStatus.logException("Starting OpenVPN Thread", e);
            Log.e(TAG, "OpenVPNThread Got " + e.toString());
        } finally {
            int exitvalue = 0;
            // start() may throw after publishing the child (e.g. destroy() failed).
            // The gate remains the authoritative owner even if assignment never ran.
            SessionVpnStatus.Event outcome = null;
            try {
                // An interrupt cannot substitute for native process termination.
                Integer confirmedExit = ProcessExitWaiter.awaitOwned(processStartGate);
                if (confirmedExit != null) exitvalue = confirmedExit;
                outcome = ProcessExitWaiter.eventFor(confirmedExit);
            } catch (IllegalThreadStateException ite) {
                VpnStatus.logError("Illegal Thread state: " + ite.getLocalizedMessage());
            }
            if (exitvalue != 0) {
                VpnStatus.logError("Process exited with exit value " + exitvalue);
                if (mBrokenPie && vegasSessionId == null) {
                    /* This will probably fail since the NoPIE binary is probably not written */
                    String[] noPieArgv = VPNLaunchHelper.replacePieWithNoPie(mArgv);

                    // We are already noPIE, nothing to gain
                    if (!noPieArgv.equals(mArgv)) {
                        mArgv = noPieArgv;
                        VpnStatus.logInfo("PIE Version could not be executed. Trying no PIE version");
                        run();
                    }

                }

            }

            // Status/log failures must not suppress the established outcome callback.
            try {
            if (outcome != null && !mNoProcessExitStatus)
                VpnStatus.updateStateString("NOPROCESS", "No process running.", R.string.state_noprocess, ConnectionStatus.LEVEL_NOTCONNECTED);

            if (mDumpPath != null) {
                try {
                    BufferedWriter logout = new BufferedWriter(new FileWriter(mDumpPath + ".log"));
                    SimpleDateFormat timeformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN);
                    for (LogItem li : VpnStatus.getlogbuffer()) {
                        String time = timeformat.format(new Date(li.getLogtime()));
                        logout.write(time + " " + li.getString(mService) + "\n");
                    }
                    logout.close();
                    VpnStatus.logError(R.string.minidump_generated);
                } catch (IOException e) {
                    VpnStatus.logError("Writing minidump log: " + e.getLocalizedMessage());
                }
            }

            } finally {
                if (outcome != null)
                    mService.openvpnStopped(this, vegasSessionId, vegasSessionNonce, outcome);
            }
            Log.i(TAG, "Exiting");
        }
    }

    public static boolean stop() {
        OpenVPNThread thread = lastThread;
        if (thread == null) return false;
        thread.stopProcess();
        return true;
    }

    private void startOpenVPNThreadArgs(String[] argv) {
        LinkedList<String> argvlist = new LinkedList<>();

        Collections.addAll(argvlist, argv);

        ProcessBuilder pb = new ProcessBuilder(argvlist);
        // Hack O rama

        String lbpath = genLibraryPath(argv, pb);
        Log.i(TAG, lbpath);

        pb.environment().put("LD_LIBRARY_PATH", lbpath);
        pb.environment().put("TMPDIR", mTmpDir);

        pb.redirectErrorStream(true);
        try {
            mProcess = processStartGate.start(pb::start);
            if (mProcess == null) return;
            // Close the output, since we don't need it
//            mProcess.getOutputStream().close();
            InputStream in = mProcess.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(DUMP_PATH_STRING))
                    mDumpPath = line.substring(DUMP_PATH_STRING.length());

                if (line.startsWith(BROKEN_PIE_SUPPORT) || line.contains(BROKEN_PIE_SUPPORT2))
                    mBrokenPie = true;

                Matcher m = LOG_PATTERN.matcher(line);
                int logerror = 0;
                if (m.matches()) {
                    int flags = Integer.parseInt(m.group(3), 16);
                    String msg = m.group(4);
                    int logLevel = flags & 0x0F;

                    VpnStatus.LogLevel logStatus = VpnStatus.LogLevel.INFO;

                    if ((flags & M_FATAL) != 0)
                        logStatus = VpnStatus.LogLevel.ERROR;
                    else if ((flags & M_NONFATAL) != 0)
                        logStatus = VpnStatus.LogLevel.WARNING;
                    else if ((flags & M_WARN) != 0)
                        logStatus = VpnStatus.LogLevel.WARNING;
                    else if ((flags & M_DEBUG) != 0)
                        logStatus = VpnStatus.LogLevel.VERBOSE;

                    assert msg != null;
                    if (msg.startsWith("MANAGEMENT: CMD"))
                        logLevel = Math.max(4, logLevel);

                    if ((msg.endsWith("md too weak") && msg.startsWith("OpenSSL: error")) || msg.contains("error:140AB18E"))
                        logerror = 1;

                    VpnStatus.logMessageOpenVPN(logStatus, logLevel, msg);
                    if (logerror == 1)
                        VpnStatus.logError("OpenSSL reported a certificate with a weak hash, please the in app FAQ about weak hashes");

                } else {
                    VpnStatus.logInfo("P:" + line);
                }

                if (Thread.interrupted()) {
                    throw new InterruptedException("OpenVpn process was killed form java code");
                }
            }
        } catch (InterruptedException | IOException e) {
            VpnStatus.logException("Error reading from output of OpenVPN process", e);
            stopProcess();
        }


    }

    private String genLibraryPath(String[] argv, ProcessBuilder pb) {
        // Hack until I find a good way to get the real library path
        String applibpath = argv[0].replaceFirst("/cache/.*$", "/lib");

        String lbpath = pb.environment().get("LD_LIBRARY_PATH");
        if (lbpath == null)
            lbpath = applibpath;
        else
            lbpath = applibpath + ":" + lbpath;

        if (!applibpath.equals(mNativeDir)) {
            lbpath = mNativeDir + ":" + lbpath;
        }
        return lbpath;
    }
}
