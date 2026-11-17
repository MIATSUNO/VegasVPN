package de.blinkt.openvpn.core;

/** Wait for actual native exit even if this worker is interrupted. */
final class ProcessExitWaiter {
    private ProcessExitWaiter() { }

    /** Null means no child was installed; otherwise return only after confirmed exit. */
    static Integer awaitOwned(ProcessStartGate gate) {
        Process child = gate.child();
        return child == null ? null : await(child);
    }

    /** Only waitFor returning for an owned child is evidence of process exit. */
    static SessionVpnStatus.Event eventFor(Integer confirmedExit) {
        return confirmedExit == null ? SessionVpnStatus.Event.START_ABORTED : SessionVpnStatus.Event.PROCESS_EXITED;
    }

    static int await(Process process) {
        boolean interrupted = false;
        try {
            for (;;) {
                try {
                    return process.waitFor();
                } catch (InterruptedException e) {
                    // An interrupt is not evidence of child termination. Keep waiting;
                    // the caller may remain blocked if the child ignores termination.
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
