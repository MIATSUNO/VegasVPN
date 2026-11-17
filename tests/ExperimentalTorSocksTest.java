package br.com.vegas.independente;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class ExperimentalTorSocksTest {
    interface Handler { void handle(Socket s) throws Exception; }
    static void expect(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static int b(InputStream in) throws IOException { int n = in.read(); if (n < 0) throw new EOFException(); return n; }
    static void readFully(InputStream in, byte[] a) throws IOException { new DataInputStream(in).readFully(a); }
    static void serverTest(Handler handler, boolean succeeds) throws Exception {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> task = pool.submit(() -> { try (Socket s = server.accept()) { s.setSoTimeout(2000); handler.handle(s); } catch (Exception e) { throw new RuntimeException(e); } });
        boolean passed = false;
        try (Socket s = ExperimentalTorSocks.connectForResearch(server.getLocalPort(), 250)) { passed = true; }
        catch (IOException expected) { }
        finally { server.close(); }
        try { task.get(3, TimeUnit.SECONDS); } finally { pool.shutdownNow(); }
        expect(passed == succeeds, "Unexpected connect result");
    }
    static void greeting(Socket s) throws Exception {
        byte[] a = new byte[3]; readFully(s.getInputStream(), a);
        expect(a[0] == 5 && a[1] == 1 && a[2] == 0, "invalid greeting");
        s.getOutputStream().write(new byte[]{5,0}); s.getOutputStream().flush();
    }
    static void request(Socket s) throws Exception {
        InputStream in=s.getInputStream();
        expect(b(in)==5 && b(in)==1 && b(in)==0 && b(in)==3, "not SOCKS5 remote domain CONNECT");
        byte[] n=new byte[b(in)]; readFully(in,n);
        expect(new String(n,StandardCharsets.US_ASCII).equals("check.torproject.org"), "wrong host");
        expect((b(in)<<8 | b(in))==443, "wrong port");
    }
    public static void main(String[] args) throws Exception {
        serverTest(s -> { greeting(s); request(s); s.getOutputStream().write(new byte[]{5,0,0,1,127,0,0,1,0,1}); }, true);
        serverTest(s -> { byte[] x=new byte[3]; readFully(s.getInputStream(),x); s.getOutputStream().write(new byte[]{5,(byte)255}); }, false);
        serverTest(s -> { greeting(s); request(s); s.getOutputStream().write(new byte[]{5,5,0,1,127,0,0,1,0,1}); }, false);
        serverTest(s -> { greeting(s); request(s); s.getOutputStream().write(new byte[]{5,0,0,1,127}); }, false);
        serverTest(s -> { byte[] x=new byte[3]; readFully(s.getInputStream(),x); Thread.sleep(600); }, false);
        int unused; try (ServerSocket s = new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { unused=s.getLocalPort(); }
        try { ExperimentalTorSocks.connectForResearch(unused,250).close(); throw new AssertionError("unavailable proxy succeeded"); } catch (IOException expected) { }
        try { ExperimentalTorSocks.connectForResearch(0,250); throw new AssertionError("invalid port accepted"); } catch (IllegalArgumentException expected) { }
        System.out.println("OK: SOCKS greeting, remote-domain CONNECT, rejection, truncation, timeout, absent proxy, invalid port");
        System.out.println("Only fake loopback SOCKS tested; no Android, Arti, TLS, Tor or leak-proof guarantee.");
    }
}
