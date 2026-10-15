package dev.scoutsolutions.fakegps;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import javax.net.ssl.HttpsURLConnection;

public final class RouteImporter {
    private static final Pattern COORD = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");
    private final Context context;
    public RouteImporter(Context ctx) { context = ctx.getApplicationContext(); }
    @SuppressWarnings("deprecation")
    public GeoPoint locate(String text) throws Exception {
        Matcher match = COORD.matcher(text);
        if (match.matches()) return new GeoPoint(Double.parseDouble(match.group(1)), Double.parseDouble(match.group(2)));
        if (!Geocoder.isPresent()) throw new IllegalArgumentException("Geocoding unavailable. Enter latitude,longitude.");
        List<Address> results = new Geocoder(context, Locale.ENGLISH).getFromLocationName(text, 1);
        if (results == null || results.isEmpty()) throw new IllegalArgumentException("Place not found: " + text + ". Use precise coordinates.");
        Address a = results.get(0); return new GeoPoint(a.getLatitude(), a.getLongitude());
    }
    public Track importText(String text, String name, RoutingProfile profile) throws Exception {
        if (profile == null) throw new IllegalArgumentException("Choose Driving or Walking.");
        List<String> stops = new ArrayList<>();
        Matcher link = Pattern.compile("https://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(text);
        if (link.find()) {
            stops.addAll(MapsLink.stops(expand(link.group().replaceAll("[.,)]+$", ""))));
        } else {
            for (String s : text.split("[\\n;]+")) if (!s.trim().isEmpty()) stops.add(s.trim());
        }
        if (stops.size() < 2 || stops.size() > 20) throw new IllegalArgumentException("Enter 2 to 20 stops, one per line: address or latitude,longitude.");
        List<GeoPoint> points = new ArrayList<>();
        for (String stop : stops) { GeoPoint p = locate(stop); if (points.isEmpty() || points.get(points.size()-1).distanceTo(p) > 0.5) points.add(p); }
        return new RouteRouter(Net::readRoute).route(points, name, profile);
    }
    private String expand(String initial) throws Exception {
        URL url = new URL(initial);
        for (int i = 0; i < 6; i++) {
            MapsLink.validate(url);
            if (MapsLink.isFullDirections(url)) return url.toString();
            HttpsURLConnection c = Net.open(url);
            try {
                int status = c.getResponseCode();
                if (status >= 300 && status < 400) { String next = c.getHeaderField("Location"); if (next == null) throw new IllegalArgumentException("Unable to resolve the shortened link."); url = new URL(url, next); }
                else if (status == 200) return url.toString();
                else throw new IllegalArgumentException("Google Maps could not resolve the link (HTTP " + status + "). Use the full link or enter the stops.");
            } finally { c.disconnect(); }
        }
        throw new IllegalArgumentException("Too many redirects in the link.");
    }
}
