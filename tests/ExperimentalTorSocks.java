package br.com.vegas.independente;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Offline-tested building block, deliberately unused by the app. This only connects
 * to a SOCKS5 listener on IPv4 loopback and requests one fixed remote hostname.
 * It does NOT authenticate that listener or prove it is Arti/Tor; do not enable.
 */
final class ExperimentalTorSocks {
    private static final String DESTINATION = "check.torproject.org";
    private static final int DESTINATION_PORT = 443;

    private ExperimentalTorSocks() {}

    static Socket connectForResearch(int localProxyPort, int timeoutMs) throws IOException {
        if (localProxyPort < 1 || localProxyPort > 65535 || timeoutMs < 1) {
            throw new IllegalArgumentException("Invalid port or timeout");
        }
        // NO_PROXY prevents platform proxy selection for this single local socket.
        // It NEVER connects directly to the destination.
        Socket socket = new Socket(Proxy.NO_PROXY);
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", localProxyPort), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            out.write(new byte[] {5, 1, 0});
            out.flush();
            if (readByte(in) != 5 || readByte(in) != 0) {
                throw new IOException("SOCKS authentication rejected");
            }
            byte[] name = DESTINATION.getBytes(StandardCharsets.US_ASCII);
            out.write(new byte[] {5, 1, 0, 3, (byte) name.length});
            out.write(name);
            out.write(DESTINATION_PORT >>> 8);
            out.write(DESTINATION_PORT & 255);
            out.flush();
            int version = readByte(in);
            int status = readByte(in);
            int reserved = readByte(in);
            int type = readByte(in);
            if (version != 5 || status != 0 || reserved != 0) {
                throw new IOException("SOCKS connect rejected or invalid reply");
            }
            int addressLength;
            switch (type) {
                case 1: addressLength = 4; break;
                case 4: addressLength = 16; break;
                case 3: addressLength = readByte(in); if (addressLength == 0) throw new IOException("Empty address"); break;
                default: throw new IOException("Invalid SOCKS address type");
            }
            for (int i = 0; i < addressLength + 2; i++) readByte(in);
            return socket;
        } catch (IOException | RuntimeException failure) {
            try { socket.close(); } catch (IOException ignored) { }
            throw failure;
        }
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("Truncated SOCKS response");
        return b;
    }
}
