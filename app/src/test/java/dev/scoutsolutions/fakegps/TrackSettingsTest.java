package dev.scoutsolutions.fakegps;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrackSettingsTest {
    private Track track() {
        Track t = new Track("Test route", "GPX"); t.points.add(new GeoPoint(45, 9, 30, 1234)); t.points.add(new GeoPoint(45.001, 9.002, 40, 5678)); return t;
    }
    private Track reload(Track t) throws Exception { return Track.fromJson(new JSONObject(t.json().toString())); }
    @Test public void newTracksHaveWalkingDefaults() { assertEquals(PlaybackSettings.WALK, track().playback.profile); }
    @Test public void trackAndSettingsSurviveActualJsonSerialization() throws Exception {
        Track original = track(); original.playback = new PlaybackSettings("custom", 5.3, 4.7, 6.1, "loop"); Track restored = reload(original);
        assertEquals(original.id, restored.id); assertEquals(original.name, restored.name); assertEquals(original.created, restored.created);
        assertEquals(2, restored.points.size()); assertEquals(40, restored.points.get(1).altitude, 0); assertEquals(5678, restored.points.get(1).time);
        assertEquals("custom", restored.playback.profile); assertEquals(5.3, restored.playback.baseKmh, 0); assertEquals(4.7, restored.playback.minKmh, 0); assertEquals(6.1, restored.playback.maxKmh, 0); assertEquals("loop", restored.playback.end);
    }
    @Test public void oldTracksWithoutSettingsKeepTheirGeometry() throws Exception {
        JSONObject legacy = track().json(); legacy.remove("playback"); Track restored = Track.fromJson(legacy);
        assertNull(restored.playback); assertEquals(2, restored.points.size()); assertEquals(45.001, restored.points.get(1).lat, 0);
        restored.playback = PlaybackSettings.custom(30, "stop"); assertEquals("stop", reload(restored).playback.end);
    }
    @Test public void invalidSettingsDoNotMakeATrackUnreadable() throws Exception {
        JSONObject obj = track().json(); obj.getJSONObject("playback").put("minKmh", 100); Track restored = Track.fromJson(obj);
        assertNull(restored.playback); assertEquals(2, restored.points.size()); assertEquals(5678, restored.points.get(1).time);
    }
    @Test public void differentRoutesKeepIndependentSettings() throws Exception {
        Track a = track(), b = track(); a.playback = PlaybackSettings.preset("walk").withEnd("loop"); b.playback = PlaybackSettings.preset("car").withRange(65, 80).withEnd("stop");
        Track restoredA = reload(a), restoredB = reload(b); restoredA.playback = PlaybackSettings.custom(7.5, "hold");
        assertEquals("car", restoredB.playback.profile); assertEquals(65, restoredB.playback.minKmh, 0); assertEquals("stop", restoredB.playback.end);
        assertEquals(7.5, reload(restoredA).playback.baseKmh, 0);
    }
    @Test public void renamingDoesNotRemoveSpeedSettings() throws Exception {
        Track a = track(); a.playback = PlaybackSettings.preset("bike").withRange(12, 25).withEnd("stop"); a = reload(a); a.name = "New name"; a = reload(a);
        assertEquals("New name", a.name); assertEquals("bike", a.playback.profile); assertEquals(12, a.playback.minKmh, 0); assertEquals(25, a.playback.maxKmh, 0); assertEquals("stop", a.playback.end);
    }
}
