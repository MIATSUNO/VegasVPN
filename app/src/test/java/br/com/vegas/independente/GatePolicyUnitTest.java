package br.com.vegas.independente;

import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GatePolicyUnitTest {
    private String encode(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    @Test public void exactBytesAndOneByteChange() throws Exception {
        String a=encode("test profile\n"), b=encode("test profile\r\n");
        String digest=ExperimentalGatePolicy.hash(a);
        assertTrue(ExperimentalGatePolicy.consentMatches(digest,a));
        assertFalse(ExperimentalGatePolicy.consentMatches(digest,b));
        assertFalse(ExperimentalGatePolicy.consentMatches(digest,null));
    }
    @Test public void releaseAndMalformedProfilesFailClosed() {
        String invalid=encode("client\ndev tun\nup /bin/sh\n");
        assertNull(ExperimentalGatePolicy.validate(false,invalid,"1.2.3.4"));
        assertNull(ExperimentalGatePolicy.validate(true,invalid,"1.2.3.4"));
        assertNull(ExperimentalGatePolicy.validate(true,"!bad!","1.2.3.4"));
        assertNull(ExperimentalGatePolicy.validate(true,null,"1.2.3.4"));
    }
}
