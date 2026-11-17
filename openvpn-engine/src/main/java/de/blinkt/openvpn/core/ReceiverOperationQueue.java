package de.blinkt.openvpn.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Receiver effects run serially away from Android callbacks. A timed-out effect
 * closes the permission gate and requests stop; in-flight I/O cannot be recalled. */
final class ReceiverOperationQueue {
    private final Object lock = new Object();
    private final PauseRunPolicy policy;
    private final Runnable failed;
    private final long timeoutMs;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService watchdog;
    private boolean closed;
    private long sequence;
    private long active;

    ReceiverOperationQueue(PauseRunPolicy policy, Runnable failed, int capacity, long timeoutMs) {
        this.policy = policy;
        this.failed = failed;
        this.timeoutMs = timeoutMs;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), r -> daemon(r, "VPNReceiverEffects"));
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "VPNReceiverWatchdog"));
    }
    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    boolean submit(Runnable operation) {
        synchronized (lock) {
            if (closed) return false;
            try {
                executor.execute(() -> run(operation));
                return true;
            } catch (RejectedExecutionException error) {
                closeLocked();
            }
        }
        notifyFailure();
        return false;
    }

    private void run(Runnable operation) {
        final long token;
        final ScheduledFuture<?> timer;
        synchronized (lock) {
            if (closed) return;
            token = ++sequence;
            active = token;
            timer = watchdog.schedule(() -> timeout(token), timeoutMs, TimeUnit.MILLISECONDS);
        }
        try { operation.run(); }
        catch (Throwable error) { fail(token); }
        finally {
            synchronized (lock) {
                if (active == token) active = 0;
                timer.cancel(false);
            }
        }
    }
    private void timeout(long token) { fail(token); }
    private void fail(long token) {
        synchronized (lock) {
            if (closed || active != token) return;
            closeLocked();
        }
        notifyFailure();
    }
    void close() {
        synchronized (lock) {
            if (!closed) closeLocked();
        }
    }
    private void closeLocked() {
        closed = true;
        policy.close();
        executor.shutdownNow();
        watchdog.shutdownNow();
    }
    private void notifyFailure() {
        daemon(failed, "VPNReceiverFailure").start();
    }
}
