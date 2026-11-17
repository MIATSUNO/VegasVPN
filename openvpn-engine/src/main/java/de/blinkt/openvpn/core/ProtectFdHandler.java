package de.blinkt.openvpn.core;

import java.io.FileDescriptor;

/** Fail closed on a missing, invalid or unprotectable management socket descriptor. */
final class ProtectFdHandler {
    interface Protector { boolean protect(FileDescriptor fd) throws Exception; }
    interface Closer { void close(FileDescriptor fd) throws Exception; }
    interface Reply { boolean send(String status); }

    static boolean protect(FileDescriptor fd, Protector protector, Closer closer) {
        boolean result = false;
        try {
            if (fd != null && fd.valid()) result = protector.protect(fd);
        } catch (Exception failure) {
            result = false;
        } finally {
            // SCM_RIGHTS gives us our own duplicate; never close the engine's original.
            if (fd != null) {
                try { closer.close(fd); } catch (Exception failure) { result = false; }
            }
        }
        return result;
    }

    /** Start fail-closed teardown before a potentially blocking cancel write. */
    static void needOk(boolean protectedFd, Reply reply, Runnable stop) {
        if (!protectedFd) {
            try { stop.run(); } finally { reply.send("cancel"); }
        } else {
            boolean sent = false;
            try { sent = reply.send("ok"); } finally { if (!sent) stop.run(); }
        }
    }

    private ProtectFdHandler() { }
}
