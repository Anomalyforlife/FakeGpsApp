package dev.scoutsolutions.fakegps;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fetches a route from the selected OSRM graph; failures never fall back to another mode. */
public final class RouteRouter {
    public interface Transport { String read(String url) throws Exception; }
    private final Transport transport;
    public RouteRouter(Transport transport) { this.transport = transport; }

    public Track route(List<GeoPoint> stops, String name, RoutingProfile profile) throws Exception {
        if (profile == null) throw new IllegalArgumentException("Choose Driving or Walking.");
        if (stops == null || stops.size() < 2 || stops.size() > 20) throw new IllegalArgumentException("Enter 2 to 20 distinct stops.");
        StringBuilder coords = new StringBuilder();
        for (GeoPoint p : stops) {
            if (coords.length() > 0) coords.append(';');
            coords.append(p.lon).append(',').append(p.lat);
        }
        JSONObject obj = new JSONObject(transport.read(profile.endpoint() + coords + "?overview=full&geometries=geojson&steps=false"));
        if (!"Ok".equals(obj.optString("code"))) throw new IllegalArgumentException("No route was found for " + (profile == RoutingProfile.FOOT ? "walking" : "driving") + " between these stops.");
        JSONArray routes = obj.optJSONArray("routes");
        if (routes == null || routes.length() == 0) throw new IllegalArgumentException("The service did not return a route.");
        JSONArray coordinates = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
        if (coordinates.length() > 100000) throw new IllegalArgumentException("Route too large. Split it into shorter sections.");
        Track track = new Track(name, profile.source());
        track.routingProfile = profile;
        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray p = coordinates.getJSONArray(i);
            track.points.add(new GeoPoint(p.getDouble(1), p.getDouble(0)));
        }
        new RouteEngine(track.points);
        return track;
    }
}
