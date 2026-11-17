package br.com.vegas.independente;

import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.util.Locale;

/** Indicative metadata only, not a profile validator or authorization to connect. */
public final class ProfileInspection {
    private static final int MAX_ENCODED = 192 * 1024;
    public enum Summary { UNREADABLE, NO_EXPLICIT_SERVER_CHECK, HAS_SERVER_CHECK }
    private ProfileInspection() {}

    /** Never returns profile data; it is not persisted. Presence of a directive proves nothing. */
    public static Summary inspect(String encoded) {
        try {
            byte[] data = decodeStrict(encoded);
            String text = Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
            boolean found = false;
            String block = null;
            for (String line : text.split("\\r?\\n", -1)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#") || s.startsWith(";")) continue;
                if (s.startsWith("<") && s.endsWith(">")) {
                    if (s.matches("<[A-Za-z0-9_-]+>")) {
                        if (block != null) return Summary.UNREADABLE;
                        block = s.substring(1, s.length() - 1);
                    } else if (s.matches("</[A-Za-z0-9_-]+>")) {
                        if (block == null || !s.substring(2, s.length() - 1).equals(block)) return Summary.UNREADABLE;
                        block = null;
                    } else return Summary.UNREADABLE;
                    continue;
                }
                if (block != null) continue;
                String[] parts = s.toLowerCase(Locale.ROOT).split("\\s+");
                if (parts.length >= 2 && (parts[0].equals("verify-x509-name") || parts[0].equals("peer-fingerprint"))) found = true;
            }
            if (block != null) return Summary.UNREADABLE;
            return found ? Summary.HAS_SERVER_CHECK : Summary.NO_EXPLICIT_SERVER_CHECK;
        } catch (Exception e) { return Summary.UNREADABLE; }
    }

    // RFC 4648 alphabet, strict padding, and bounded allocation. Works on Android API 23.
    static byte[] decodeStrict(String s) {
        if (s == null || s.length() == 0 || s.length() > MAX_ENCODED || s.length() % 4 != 0) throw new IllegalArgumentException();
        int pad = s.endsWith("==") ? 2 : s.endsWith("=") ? 1 : 0;
        byte[] out = new byte[(s.length() / 4) * 3 - pad]; int pos = 0;
        for (int i = 0; i < s.length(); i += 4) {
            boolean last = i + 4 == s.length();
            int a = digit(s.charAt(i)), b = digit(s.charAt(i + 1));
            int c = s.charAt(i + 2) == '=' ? -1 : digit(s.charAt(i + 2));
            int d = s.charAt(i + 3) == '=' ? -1 : digit(s.charAt(i + 3));
            if (a < 0 || b < 0 || (c < 0 && !(last && pad == 2)) || (d < 0 && !(last && pad > 0)) ||
                (c < 0 && d >= 0) || (pad == 2 && last && (b & 15) != 0) || (pad == 1 && last && (c & 3) != 0)) throw new IllegalArgumentException();
            int v = (a << 18) | (b << 12) | ((c < 0 ? 0 : c) << 6) | (d < 0 ? 0 : d);
            out[pos++] = (byte)(v >> 16); if (pos < out.length) out[pos++] = (byte)(v >> 8); if (pos < out.length) out[pos++] = (byte)v;
        }
        return out;
    }
    private static int digit(char c) {
        if (c >= 'A' && c <= 'Z') return c-'A';
        if (c >= 'a' && c <= 'z') return c-'a'+26;
        if (c >= '0' && c <= '9') return c-'0'+52;
        if (c == '+') return 62; if (c == '/') return 63; throw new IllegalArgumentException();
    }
}
