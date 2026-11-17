package br.com.vegas.independente;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/** Fail-closed, bounded parser for a deliberately small OpenVPN subset. Never authorizes a connection. */
public final class VpnGateProfileValidator {
    private static final int MAX_ENCODED=192*1024, MAX_DECODED=144*1024, MAX_LINES=4096, MAX_LINE=4096, MAX_BLOCK=32768;
    public enum Reason { INVALID_ENCODING, TOO_LARGE, INVALID_TEXT, BAD_SYNTAX, UNSUPPORTED_DIRECTIVE,
        DUPLICATE_DIRECTIVE, MISSING_FIELD, ENDPOINT_MISMATCH, INVALID_CA, INVALID_CLIENT_CERT, INVALID_CLIENT_KEY,
        CLIENT_KEY_MISMATCH, CA_NOT_INDEPENDENTLY_TRUSTED, RELAY_IDENTITY_UNVERIFIED,
        SERVER_ROLE_NOT_SPECIFIED, ENGINE_UNAVAILABLE }
    /** Secret-bearing and ephemeral: never log, persist, serialize or display. Strings cannot be zeroized. */
    public static final class ParsedProfile {
        public final String ip, port, protocol, cipher, dataCiphers, auth, serverName, caPem, certPem, keyPem;
        private ParsedProfile(Map<String,String> d, Map<String,String> b) {
            String[] remote=d.get("remote").split(" "); ip=remote[0]; port=remote[1]; protocol=d.get("proto");
            cipher=d.get("cipher"); dataCiphers=d.get("data-ciphers"); auth=d.get("auth"); serverName=d.get("verify-x509-name");
            caPem=b.get("ca"); certPem=b.get("cert"); keyPem=b.get("key");
        }
        @Override public String toString() { return "ParsedProfile[redacted]"; }
    }
    public static final class Result {
        public final Set<Reason> reasons;
        /** Non-null only after parsing and key/certificate checks; still NOT trusted for connecting. */
        public final ParsedProfile profile;
        private Result(EnumSet<Reason> reasons, ParsedProfile profile) {
            this.reasons=Collections.unmodifiableSet(EnumSet.copyOf(reasons)); this.profile=profile;
        }
        public boolean mayConnect() { return false; }
    }
    private VpnGateProfileValidator() {}
    public static Result validate(String encoded, String selectedIp) {
        EnumSet<Reason> reasons=EnumSet.of(Reason.CA_NOT_INDEPENDENTLY_TRUSTED, Reason.RELAY_IDENTITY_UNVERIFIED, Reason.ENGINE_UNAVAILABLE);
        if (encoded == null || encoded.isEmpty() || encoded.length()>MAX_ENCODED) {
            reasons.add(encoded!=null && encoded.length()>MAX_ENCODED ? Reason.TOO_LARGE : Reason.INVALID_ENCODING); return new Result(reasons,null);
        }
        final String text;
        try {
            byte[] raw=ProfileInspection.decodeStrict(encoded);
            if(raw.length>MAX_DECODED) { reasons.add(Reason.TOO_LARGE); return new Result(reasons,null); }
            text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        } catch(Exception e) { reasons.add(Reason.INVALID_ENCODING); return new Result(reasons,null); }
        if(text.indexOf('\0')>=0 || text.replace("\r\n", "").indexOf('\r')>=0) { reasons.add(Reason.INVALID_TEXT); return new Result(reasons,null); }
        String[] lines=text.split("\\r?\\n",-1);
        if(lines.length>MAX_LINES) { reasons.add(Reason.TOO_LARGE); return new Result(reasons,null); }
        Map<String,String> directives=new HashMap<>(), blocks=new HashMap<>(); String block=null; StringBuilder content=null;
        boolean fatal=false;
        for(String line:lines) {
            if(line.length()>MAX_LINE) { reasons.add(Reason.TOO_LARGE); fatal=true; break; }
            for(int i=0;i<line.length();i++) { char c=line.charAt(i); if((c<32 && c!='\t') || c==127) { reasons.add(Reason.INVALID_TEXT); fatal=true; break; } }
            if(fatal) break;
            String s=line.trim();
            if(block!=null) {
                if(s.equals("</"+block+">")) { blocks.put(block,content.toString()); block=null; content=null; continue; }
                if(s.startsWith("<") || content.length()+s.length()+1>MAX_BLOCK) { reasons.add(Reason.BAD_SYNTAX); fatal=true; break; }
                content.append(s).append('\n'); continue;
            }
            if(s.isEmpty() || s.startsWith("#") || s.startsWith(";")) continue;
            if(s.equals("<ca>") || s.equals("<cert>") || s.equals("<key>")) {
                block=s.substring(1,s.length()-1); if(blocks.containsKey(block)) { reasons.add(Reason.DUPLICATE_DIRECTIVE); fatal=true; break; }
                content=new StringBuilder(); continue;
            }
            if(s.indexOf('"')>=0 || s.indexOf('\'')>=0 || s.indexOf('\\')>=0 || s.indexOf('#')>=0 || s.indexOf(';')>=0 || s.startsWith("--") || s.startsWith("<")) { reasons.add(Reason.BAD_SYNTAX); fatal=true; break; }
            String[] p=s.split("[ \\t]+"); String key=p[0];
            if(!Arrays.asList("client","dev","proto","remote","nobind","persist-key","persist-tun","remote-cert-tls","verify-x509-name","verb","cipher","data-ciphers","auth","resolv-retry").contains(key)) { reasons.add(Reason.UNSUPPORTED_DIRECTIVE); fatal=true; break; }
            if(directives.containsKey(key)) { reasons.add(Reason.DUPLICATE_DIRECTIVE); fatal=true; break; }
            boolean ok=false; String value=p.length>1 ? p[1] : "";
            switch(key) {
                case "client": case "nobind": case "persist-key": case "persist-tun": ok=p.length==1; break;
                case "dev": ok=p.length==2 && value.equals("tun"); break;
                case "proto": ok=p.length==2 && Arrays.asList("udp","tcp","tcp-client").contains(value); break;
                case "remote-cert-tls": ok=p.length==2 && value.equals("server"); break;
                case "verify-x509-name": ok=p.length==3 && p[2].equals("name") && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,252}"); break;
                case "verb": ok=p.length==2 && value.matches("[0-4]"); break;
                case "cipher": case "data-ciphers": ok=p.length==2 && value.equals("AES-128-CBC"); break;
                case "auth": ok=p.length==2 && value.equals("SHA1"); break;
                case "resolv-retry": ok=p.length==2 && value.equals("infinite"); break;
                case "remote":
                    ok=p.length==3 && ipv4(value) && p[2].matches("[0-9]{1,5}");
                    if(ok) { int port=Integer.parseInt(p[2]); ok=port>0 && port<=65535; }
                    if(ok && !value.equals(selectedIp)) reasons.add(Reason.ENDPOINT_MISMATCH);
                    if(ok) value=value+" "+p[2]; break;
                default: break;
            }
            if(!ok) { reasons.add(Reason.BAD_SYNTAX); fatal=true; break; }
            directives.put(key,value);
        }
        if(block!=null) { reasons.add(Reason.BAD_SYNTAX); fatal=true; }
        if(!directives.keySet().containsAll(Arrays.asList("client","dev","proto","remote")) || !blocks.keySet().containsAll(Arrays.asList("ca","cert","key"))) { reasons.add(Reason.MISSING_FIELD); fatal=true; }
        if(!directives.containsKey("remote-cert-tls")) reasons.add(Reason.SERVER_ROLE_NOT_SPECIFIED);
        X509Certificate cert=null;
        if(blocks.containsKey("ca")) try { X509Certificate ca=certificate(blocks.get("ca")); if(ca.getBasicConstraints()<0) throw new IllegalArgumentException(); }
            catch(Exception e) { reasons.add(Reason.INVALID_CA); fatal=true; }
        if(blocks.containsKey("cert")) try { cert=certificate(blocks.get("cert")); } catch(Exception e) { reasons.add(Reason.INVALID_CLIENT_CERT); fatal=true; }
        if(blocks.containsKey("key")) try {
            PrivateKey key=key(blocks.get("key"));
            if(cert!=null) { Signature sig=Signature.getInstance("SHA256withRSA"); byte[] challenge="Vegas parser key match".getBytes(StandardCharsets.US_ASCII); sig.initSign(key); sig.update(challenge); byte[] signed=sig.sign(); sig.initVerify(cert.getPublicKey()); sig.update(challenge); if(!sig.verify(signed)) { reasons.add(Reason.CLIENT_KEY_MISMATCH); fatal=true; } }
        } catch(Exception e) { reasons.add(Reason.INVALID_CLIENT_KEY); fatal=true; }
        if(reasons.contains(Reason.ENDPOINT_MISMATCH)) fatal=true;
        return new Result(reasons, fatal ? null : new ParsedProfile(directives,blocks));
    }
    private static boolean ipv4(String ip) {
        if(!ip.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) return false;
        for(String n:ip.split("\\.")) if(Integer.parseInt(n)>255) return false; return true;
    }
    private static byte[] pem(String text,String type) {
        String begin="-----BEGIN "+type+"-----\n", end="-----END "+type+"-----\n";
        if(text==null) throw new IllegalArgumentException();
        // VPN Gate currently emits an empty line after the PEM footer inside the block.
        // Keep the original block untouched in ParsedProfile, normalize only for decoding.
        text=text.trim()+"\n";
        if(!text.startsWith(begin) || !text.endsWith(end)) throw new IllegalArgumentException();
        String body=text.substring(begin.length(),text.length()-end.length());
        if(!body.matches("(?:[A-Za-z0-9+/=]+\\n)+")) throw new IllegalArgumentException();
        return ProfileInspection.decodeStrict(body.replace("\n",""));
    }
    private static X509Certificate certificate(String text) throws Exception {
        byte[] der=pem(text,"CERTIFICATE"); ByteArrayInputStream in=new ByteArrayInputStream(der);
        X509Certificate cert=(X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        if(in.available()!=0) throw new IllegalArgumentException(); return cert;
    }
    private static PrivateKey key(String text) throws Exception {
        byte[] der;
        if(text.startsWith("-----BEGIN RSA PRIVATE KEY-----\n")) {
            byte[] rsa=pem(text,"RSA PRIVATE KEY");
            byte[] alg={0x30,0x0d,0x06,0x09,0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,0x01,0x01,0x01,0x05,0x00};
            byte[] oct=tlv(0x04,rsa), version={0x02,0x01,0x00}; byte[] inner=new byte[version.length+alg.length+oct.length];
            System.arraycopy(version,0,inner,0,version.length); System.arraycopy(alg,0,inner,version.length,alg.length); System.arraycopy(oct,0,inner,version.length+alg.length,oct.length); der=tlv(0x30,inner);
        } else der=pem(text,"PRIVATE KEY");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
    private static byte[] tlv(int tag,byte[] body) {
        int len=body.length; if(len>65535) throw new IllegalArgumentException();
        byte[] h=len<128 ? new byte[]{(byte)tag,(byte)len} : len<256 ? new byte[]{(byte)tag,(byte)0x81,(byte)len} : new byte[]{(byte)tag,(byte)0x82,(byte)(len>>8),(byte)len};
        byte[] out=Arrays.copyOf(h,h.length+len); System.arraycopy(body,0,out,h.length,len); return out;
    }
}
