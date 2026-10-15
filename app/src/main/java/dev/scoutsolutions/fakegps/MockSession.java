package dev.scoutsolutions.fakegps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Coordinates mock backends on the service worker; never changes the route or its position. */
final class MockSession {
    static final class Fix {
        final GeoPoint point;
        final float speed, bearing;
        final long time, elapsedNanos;
        Fix(GeoPoint point, float speed, float bearing, long time, long elapsedNanos) {
            this.point = point; this.speed = speed; this.bearing = bearing; this.time = time; this.elapsedNanos = elapsedNanos;
        }
    }
    interface Backend {
        void start() throws Exception;
        void send(Fix fix) throws Exception;
        void stop() throws Exception;
    }
    private final List<Backend> backends, attempted = new ArrayList<>();
    private int failures;
    private boolean running;
    private String warning = "";
    private long lastSentNanos;

    MockSession(Backend... backends) { this.backends = Arrays.asList(backends); }
    void start() throws Exception {
        if (running) throw new IllegalStateException("Simulation already active.");
        failures = 0; warning = ""; lastSentNanos = 0;
        try {
            for (Backend backend : backends) { attempted.add(backend); backend.start(); }
            running = true;
        } catch (Exception e) {
            String cleanup = stop();
            if (!cleanup.isEmpty()) e.addSuppressed(new IllegalStateException(cleanup));
            throw e;
        }
    }
    boolean send(Fix fix) throws Exception {
        if (!running) throw new IllegalStateException("Simulation not active.");
        Exception failure = null;
        for (Backend backend : backends) {
            try { backend.send(fix); }
            catch (SecurityException e) { throw e; }
            catch (Exception e) { if (failure == null) failure = e; }
        }
        if (failure != null) {
            warning = "Location delivery not confirmed · attempt " + (++failures) + "/3";
            if (failures >= 3) throw new IllegalStateException("GPS delivery failed three times: " + failure.getMessage(), failure);
            return false;
        }
        failures = 0; warning = ""; lastSentNanos = fix.elapsedNanos;
        return true;
    }
    String stop() {
        running = false;
        StringBuilder errors = new StringBuilder();
        for (int i = attempted.size() - 1; i >= 0; i--) {
            try { attempted.get(i).stop(); }
            catch (Exception e) { if (errors.length() > 0) errors.append("; "); errors.append(e.getMessage() == null ? "GPS restoration not confirmed" : e.getMessage()); }
        }
        attempted.clear();
        return errors.toString();
    }
    String warning() { return warning; }
    long lastSentNanos() { return lastSentNanos; }
}
