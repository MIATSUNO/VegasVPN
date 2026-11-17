package br.com.vegas.independente;

/** Tests a deliberately isolated design model, not Android or real privacy guarantees. */
public final class TorSafetyModelTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TorSafetyModel.Evidence[] values = TorSafetyModel.Evidence.values();
        // Every proper subset must remain STARTING, including bootstrap-only and an open port.
        for (int mask = 0; mask < (1 << values.length) - 1; mask++) {
            TorSafetyModel m = new TorSafetyModel();
            long run = m.start();
            for (int i = 0; i < values.length; i++)
                if ((mask & (1 << i)) != 0) m.report(run, values[i]);
            check(m.phase() == TorSafetyModel.Phase.STARTING, "premature candidate: " + mask);
        }
        TorSafetyModel m = new TorSafetyModel();
        long old = m.start();
        m.report(old, TorSafetyModel.Evidence.BOOTSTRAP);
        m.report(old, TorSafetyModel.Evidence.BOOTSTRAP); // duplicate
        check(m.phase() == TorSafetyModel.Phase.STARTING, "bootstrap alone");
        m.fail(old);
        for (TorSafetyModel.Evidence e : values) m.report(old, e);
        check(m.phase() == TorSafetyModel.Phase.FAILED, "failure must be terminal");
        long current = m.start();
        for (TorSafetyModel.Evidence e : values) m.report(old, e);
        check(m.phase() == TorSafetyModel.Phase.STARTING, "stale generation");
        for (TorSafetyModel.Evidence e : values) m.report(current, e);
        check(m.phase() == TorSafetyModel.Phase.CANDIDATE, "all evidence required");
        m.fail(old);
        check(m.phase() == TorSafetyModel.Phase.CANDIDATE, "stale failure");
        m.fail(current);
        check(m.phase() == TorSafetyModel.Phase.FAILED, "loss after candidate");
        current = m.start();
        m.stop();
        for (TorSafetyModel.Evidence e : values) m.report(current, e);
        check(m.phase() == TorSafetyModel.Phase.STOPPED, "late callback after stop");
        System.out.println("PASS: Tor safety model: 1023 partial evidence sets; failure, stale callbacks and stop");
    }
}
