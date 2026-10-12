package dev.scoutsolutions.fakegps;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Track {
    public String id = UUID.randomUUID().toString();
    public String name, source;
    public RoutingProfile routingProfile;
    public long created = System.currentTimeMillis();
    public PlaybackSettings playback = PlaybackSettings.preset(PlaybackSettings.WALK);
    public final List<GeoPoint> points = new ArrayList<>();
    public Track(String name, String source) { this.name = name; this.source = source; }
    public double distance() { double d = 0; for (int i = 1; i < points.size(); i++) d += points.get(i-1).distanceTo(points.get(i)); return d; }
    public JSONObject json() throws Exception {
        JSONArray p = new JSONArray();
        for (GeoPoint v : points) p.put(new JSONArray().put(v.lat).put(v.lon).put(v.altitude).put(v.time));
        JSONObject result = new JSONObject().put("id", id).put("name", name).put("source", source).put("created", created).put("points", p);
        if (playback != null) result.put("playback", playback.json());
        if (routingProfile != null) result.put("routingProfile", routingProfile.id);
        return result;
    }
    public static Track fromJson(JSONObject obj) throws Exception {
        Track t = new Track(obj.getString("name"), obj.optString("source", "Traccia"));
        t.id = obj.getString("id"); t.created = obj.getLong("created");
        t.routingProfile = RoutingProfile.fromId(obj.optString("routingProfile", ""));
        // Legacy tracks keep their geometry and can inherit the last pre-1.2 UI speed once.
        t.playback = null;
        if (obj.optJSONObject("playback") != null) {
            try { t.playback = PlaybackSettings.fromJson(obj.getJSONObject("playback")); }
            catch (Exception invalidSettings) { /* Do not make an otherwise valid saved track unreadable. */ }
        }
        JSONArray list = obj.getJSONArray("points");
        if (list.length() > 100000) throw new IllegalArgumentException("Massimo 100.000 punti per traccia.");
        for (int i = 0; i < list.length(); i++) { JSONArray p = list.getJSONArray(i); t.points.add(new GeoPoint(p.getDouble(0), p.getDouble(1), p.optDouble(2, 0), p.optLong(3, 0))); }
        return t;
    }
}
