package dev.scoutsolutions.fakegps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Distance-based playback: speed changes and pause never alter the route geometry. */
public final class RouteEngine {
    private final List<GeoPoint> points;
    private final double[] cumulative;
    private double travelled;
    public RouteEngine(List<GeoPoint> input) {
        if (input == null || input.size() < 2) throw new IllegalArgumentException("Servono almeno due punti per simulare un percorso.");
        points = Collections.unmodifiableList(new ArrayList<>(input));
        cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) cumulative[i] = cumulative[i - 1] + points.get(i - 1).distanceTo(points.get(i));
        if (total() < 0.5) throw new IllegalArgumentException("Il percorso è troppo corto.");
    }
    public double total() { return cumulative[cumulative.length - 1]; }
    public double travelled() { return travelled; }
    public boolean finished() { return travelled >= total(); }
    public void advance(double seconds, double kmh) {
        if (!Double.isFinite(seconds) || !Double.isFinite(kmh) || seconds < 0 || kmh < 0) throw new IllegalArgumentException("Tempo o velocità non validi.");
        advanceMeters(seconds * kmh / 3.6);
    }
    public void advanceMeters(double meters) {
        if (!Double.isFinite(meters) || meters < 0) throw new IllegalArgumentException("Distanza non valida.");
        travelled = Math.min(total(), travelled + meters);
    }
    public void restart() { travelled = 0; }
    private int segment() {
        int low = 1, high = cumulative.length - 1;
        while (low < high) { int mid = (low + high) / 2; if (cumulative[mid] <= travelled) low = mid + 1; else high = mid; }
        return low;
    }
    public GeoPoint position() {
        if (finished()) return points.get(points.size() - 1);
        int i = segment(); double length = cumulative[i] - cumulative[i - 1];
        return GeoPoint.interpolate(points.get(i - 1), points.get(i), length > 0 ? (travelled - cumulative[i - 1]) / length : 1);
    }
    public float bearing() { int i = segment(); return points.get(i - 1).bearingTo(points.get(i)); }
}
