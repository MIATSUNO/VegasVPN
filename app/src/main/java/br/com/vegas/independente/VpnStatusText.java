package br.com.vegas.independente;

/** Present both status sources, including their final messages after becoming idle. */
final class VpnStatusText {
    static String format(String gate, String fixture) {
        return "VPN Gate: " + gate + "\nFixture: " + fixture;
    }
    private VpnStatusText() { }
}
