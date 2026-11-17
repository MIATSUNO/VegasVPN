package de.blinkt.openvpn.core;

import java.io.IOException;

/** Serializes ownership and cancellation without blocking stop on process creation. */
final class ProcessStartGate {
    interface Factory { Process start() throws IOException; }
    private boolean stopped;
    private boolean startAttempted;
    private boolean destroyRequested;
    private Process child;

    Process start(Factory factory) throws IOException {
        synchronized (this) {
            if (stopped) return null;
            if (startAttempted) throw new IllegalStateException("Process already started");
            startAttempted = true;
        }
        // ProcessBuilder.start() can block. Never hold the gate lock around it.
        Process created = factory.start();
        boolean destroy;
        synchronized (this) {
            // Publish ownership before any operation on the child can throw.
            child = created;
            destroy = stopped && child != null && !destroyRequested;
            if (destroy) destroyRequested = true;
        }
        if (destroy) destroyOutsideLock(created);
        return created;
    }

    synchronized Process child() { return child; }

    void stop() {
        Process toDestroy;
        synchronized (this) {
            stopped = true;
            toDestroy = child != null && !destroyRequested ? child : null;
            if (toDestroy != null) destroyRequested = true;
        }
        if (toDestroy != null) destroyOutsideLock(toDestroy);
    }

    private void destroyOutsideLock(Process process) {
        try {
            process.destroy();
        } catch (RuntimeException | Error failure) {
            // Preserve ownership and permit a later stop to retry; propagate failure.
            synchronized (this) { destroyRequested = false; }
            throw failure;
        }
    }
}
