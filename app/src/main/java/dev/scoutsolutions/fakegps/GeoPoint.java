package dev.scoutsolutions.fakegps;

public final class GeoPoint {
    public final double lat, lon, altitude;
    public final long time;
    public GeoPoint(double lat, double lon) { this(lat, lon, 0, 0); }
    public GeoPoint(double lat, double lon, double altitude, long time) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180 || !Double.isFinite(altitude))
            throw new IllegalArgumentException("Coordinate non valide: latitudine −90…90, longitudine −180…180.");
        this.lat = lat; this.lon = lon; this.altitude = altitude; this.time = time;
    }
    public double distanceTo(GeoPoint other) {
        double a = Math.toRadians(lat), b = Math.toRadians(other.lat);
        double dlat = b - a, dlon = Math.toRadians(other.lon - lon);
        double h = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(a) * Math.cos(b) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371008.8 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
    }
    public float bearingTo(GeoPoint other) {
        double a = Math.toRadians(lat), b = Math.toRadians(other.lat), d = Math.toRadians(other.lon - lon);
        return (float)((Math.toDegrees(Math.atan2(Math.sin(d) * Math.cos(b), Math.cos(a) * Math.sin(b) - Math.sin(a) * Math.cos(b) * Math.cos(d))) + 360) % 360);
    }
    public static GeoPoint interpolate(GeoPoint a, GeoPoint b, double fraction) {
        double t = Math.max(0, Math.min(1, fraction));
        double delta = ((b.lon - a.lon + 540) % 360) - 180;
        double lon = ((a.lon + delta * t + 540) % 360) - 180;
        return new GeoPoint(a.lat + (b.lat - a.lat) * t, lon, a.altitude + (b.altitude - a.altitude) * t, 0);
    }
}
