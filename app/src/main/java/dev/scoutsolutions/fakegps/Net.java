package dev.scoutsolutions.fakegps;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class Net {
    static final String AGENT = "AnomalyGPS/1.3 (Android; personal route simulator)";
    private static long lastRoutingRequestNanos;
    // Public routing service policy: at most one request per second, including across importers.
    static synchronized String readRoute(String url) throws IOException {
        long wait = lastRoutingRequestNanos == 0 ? 0 : 1_000_000_000L - (System.nanoTime() - lastRoutingRequestNanos);
        if (wait > 0) try { java.util.concurrent.TimeUnit.NANOSECONDS.sleep(wait); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Importazione interrotta."); }
        lastRoutingRequestNanos = System.nanoTime();
        return read(url);
    }
    static HttpsURLConnection open(URL url) throws IOException {
        if (!"https".equals(url.getProtocol())) throw new IOException("Sono consentiti solo link HTTPS.");
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false); c.setRequestProperty("User-Agent", AGENT);
        return c;
    }
    static String read(String url) throws IOException {
        HttpsURLConnection c = open(new URL(url));
        try {
            if (c.getResponseCode() != 200) throw new IOException("Servizio non disponibile (HTTP " + c.getResponseCode() + "). Riprova o importa un GPX.");
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) { if (out.size() + n > 12 * 1024 * 1024) throw new IOException("Risposta troppo grande."); out.write(b, 0, n); }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally { c.disconnect(); }
    }
}
