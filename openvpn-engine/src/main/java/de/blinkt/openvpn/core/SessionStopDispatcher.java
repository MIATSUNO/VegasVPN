package de.blinkt.openvpn.core;

/** Each instance owns only the captured resources of one session. Neither task
 * runs on the caller or under its lock. A stuck management task cannot prevent
 * the independent process task from being started. Stops are coalesced while a
 * task runs, but may be retried after it returns or throws. */
final class SessionStopDispatcher {
    interface Starter { void start(Runnable task, String name); }
    interface Failure { void report(String name, Throwable error); }
    private final Runnable management;
    private final Runnable process;
    private final Starter starter;
    private final Failure failure;
    private boolean managementRunning, processRunning;

    SessionStopDispatcher(Runnable management, Runnable process, Starter starter, Failure failure) {
        this.management = management;
        this.process = process;
        this.starter = starter;
        this.failure = failure;
    }

    void request() {
        launch(true);
        launch(false);
    }

    private void launch(boolean isManagement) {
        Runnable action = isManagement ? management : process;
        if (action == null) return;
        String name = isManagement ? "VPNManagementStop" : "VPNProcessStop";
        synchronized (this) {
            if (isManagement ? managementRunning : processRunning) return;
            if (isManagement) managementRunning = true; else processRunning = true;
        }
        try {
            starter.start(() -> {
                try { action.run(); }
                catch (Throwable t) { report(name, t); }
                finally { clear(isManagement); }
            }, name);
        } catch (Throwable t) {
            clear(isManagement);
            report(name, t);
        }
    }
    private synchronized void clear(boolean managementTask) {
        if (managementTask) managementRunning = false; else processRunning = false;
    }
    private void report(String name, Throwable error) {
        try { failure.report(name, error); } catch (Throwable ignored) { }
    }
}
