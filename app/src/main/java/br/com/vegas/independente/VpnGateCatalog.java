package br.com.vegas.independente;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Untrusted read-only directory. Debug may retain profiles in memory; never persist or directly execute them. */
public final class VpnGateCatalog {
    private static final String URL = "https://www.vpngate.net/api/iphone/";
    private static final int MAX_BYTES = 8 * 1024 * 1024, MAX_LINE = 262144, MAX_ROWS = 100;
    public static final class Server {
        public final String country, code, address, operator, logging, ping, speed;
        public final ProfileInspection.Summary inspection;
        // Untrusted and secret-bearing: process memory only, never expose or persist.
        final String encodedProfile;
        /** Screening reasons only; never grants connection permission. */
        public final Set<VpnGateProfileValidator.Reason> screeningReasons;
        Server(String country, String code, String address, String operator, String logging, String ping, String speed, ProfileInspection.Summary inspection, Set<VpnGateProfileValidator.Reason> screeningReasons) {
            this(country,code,address,operator,logging,ping,speed,inspection,screeningReasons,null);
        }
        Server(String country, String code, String address, String operator, String logging, String ping, String speed, ProfileInspection.Summary inspection, Set<VpnGateProfileValidator.Reason> screeningReasons, String encoded) {
            this.country=country; this.code=code; this.address=address; this.operator=operator; this.logging=logging; this.ping=ping; this.speed=speed; this.inspection=inspection; this.screeningReasons=screeningReasons; this.encodedProfile=encoded;
        }
    }
    public static List<Server> fetch() throws IOException { return fetch(false); }
    public static List<Server> fetch(boolean retainDebugProfiles) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(URL).openConnection();
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        c.setUseCaches(false); c.setRequestProperty("Accept", "text/plain");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            int length = c.getContentLength(); if (length > MAX_BYTES) throw new IOException("Resposta grande demais");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n, total=0;
                while ((n = in.read(buffer)) != -1) { total += n; if (total > MAX_BYTES) throw new IOException("Resposta grande demais"); out.write(buffer,0,n); }
                return parse(new String(out.toByteArray(), StandardCharsets.UTF_8), retainDebugProfiles);
            }
        } finally { c.disconnect(); }
    }
    public static List<Server> parse(String text) throws IOException { return parse(text, false); }
    public static List<Server> parse(String text, boolean retainDebugProfiles) throws IOException {
        List<Server> result = new ArrayList<>(); boolean start=false, header=false, end=false;
        Map<String,Integer> indexes = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > MAX_LINE) throw new IOException("Linha grande demais");
                if (!start) { if (line.equals("*vpn_servers")) start=true; continue; }
                if (!header) {
                    if (!line.startsWith("#")) throw new IOException("Cabeçalho ausente");
                    List<String> cols = csv(line.substring(1));
                    for (int i=0;i<cols.size();i++) if (indexes.put(cols.get(i),i)!=null) throw new IOException("Cabeçalho duplicado");
                    for (String key : new String[]{"IP","CountryLong","CountryShort","Operator","LogType","Ping","Speed"}) if (!indexes.containsKey(key)) throw new IOException("Coluna ausente");
                    header=true; continue;
                }
                if (line.equals("*")) { end=true; break; }
                if (line.isEmpty()) continue;
                List<String> row = csv(line);
                if (row.size()!=indexes.size()) throw new IOException("CSV inválido");
                String ip = row.get(indexes.get("IP"));
                String code = row.get(indexes.get("CountryShort"));
                if (!validIp(ip) || !code.matches("[A-Za-z]{2}")) continue;
                if (result.size() < MAX_ROWS) {
                    String encoded = indexes.containsKey("OpenVPN_ConfigData_Base64") ? row.get(indexes.get("OpenVPN_ConfigData_Base64")) : null;
                    ProfileInspection.Summary inspection = ProfileInspection.inspect(encoded);
                    VpnGateProfileValidator.Result screening = VpnGateProfileValidator.validate(encoded, ip);
                    result.add(new Server(clean(row.get(indexes.get("CountryLong"))),code.toUpperCase(Locale.ROOT),ip,clean(row.get(indexes.get("Operator"))),clean(row.get(indexes.get("LogType"))),number(row.get(indexes.get("Ping"))),number(row.get(indexes.get("Speed"))), inspection, screening.reasons, retainDebugProfiles ? encoded : null));
                }
            }
        }
        if (!start || !header || !end) throw new IOException("Resposta incompleta");
        return Collections.unmodifiableList(result);
    }
    private static String number(String s) { return s.matches("[0-9]{1,18}") ? s : "?"; }
    private static String clean(String s) { String t=s.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim(); if (t.length()>100) t=t.substring(0,100); return t.isEmpty()?"não informado":t; }
    private static boolean validIp(String s) {
        if (!s.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        for (String part:s.split("\\.")) if (Integer.parseInt(part)>255) return false;
        return true;
    }
    // One-line RFC-style CSV; embedded newlines are rejected rather than ambiguously parsed.
    private static List<String> csv(String line) throws IOException {
        List<String> fields=new ArrayList<>(); StringBuilder b=new StringBuilder(); boolean quoted=false, closed=false;
        for(int i=0;i<line.length();i++) { char c=line.charAt(i);
            if (quoted) { if(c=='"') { if(i+1<line.length() && line.charAt(i+1)=='"') {b.append('"');i++;} else {quoted=false;closed=true;} } else b.append(c); }
            else if(c==',') {fields.add(b.toString());b.setLength(0);closed=false;}
            else if(c=='"' && b.length()==0 && !closed) quoted=true;
            else {if(closed || c=='"') throw new IOException("CSV inválido"); b.append(c);}
        }
        if(quoted) throw new IOException("CSV incompleto"); fields.add(b.toString()); return fields;
    }
}
