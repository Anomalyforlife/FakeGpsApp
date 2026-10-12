package dev.scoutsolutions.fakegps;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackSettingsTest {
    @Test public void customDecimalSettingsSurviveJsonRoundTrip() throws Exception {
        PlaybackSettings s = new PlaybackSettings(PlaybackSettings.CUSTOM, 5.3, 4.7, 6.1, "loop");
        PlaybackSettings restored = PlaybackSettings.fromJson(new JSONObject(s.json().toString()));
        assertEquals(s.profile, restored.profile); assertEquals(s.end, restored.end);
        assertEquals(s.baseKmh, restored.baseKmh, 0); assertEquals(s.minKmh, restored.minKmh, 0); assertEquals(s.maxKmh, restored.maxKmh, 0);
    }
    @Test public void presetRangeAndEndCanBeChangedIndependently() {
        PlaybackSettings s = PlaybackSettings.preset(PlaybackSettings.WALK).withRange(3.5, 6).withEnd("stop");
        assertEquals(PlaybackSettings.WALK, s.profile); assertEquals(5, s.baseKmh, 0); assertEquals(3.5, s.minKmh, 0); assertEquals(6, s.maxKmh, 0); assertEquals("stop", s.end);
        assertEquals("hold", PlaybackSettings.preset(PlaybackSettings.WALK).end);
    }
    @Test public void customDefaultsStayBelowMaximumSpeed() { PlaybackSettings s = PlaybackSettings.custom(300, "hold"); assertEquals(300, s.maxKmh, 0); assertTrue(s.minKmh < s.maxKmh); }
    @Test public void zeroMeansStandstill() { PlaybackSettings s = PlaybackSettings.custom(0, "hold"); assertEquals(0, s.minKmh, 0); assertEquals(0, s.maxKmh, 0); }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvertedRange() { new PlaybackSettings("custom", 5, 6, 4, "hold"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsReferenceOutsideRange() { new PlaybackSettings("custom", 5, 6, 8, "hold"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsConstantNonzeroRange() { new PlaybackSettings("custom", 5, 5, 5, "hold"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsMovingRangeWithZeroReference() { new PlaybackSettings("custom", 0, 0, 5, "hold"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsNaN() { new PlaybackSettings("custom", Double.NaN, 0, 5, "hold"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsInfinity() { new PlaybackSettings("custom", 5, 0, Double.POSITIVE_INFINITY, "hold"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsSpeedOverMaximum() { new PlaybackSettings("custom", 301, 290, 310, "hold"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnknownProfile() { new PlaybackSettings("spaceship", 5, 4, 6, "hold"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnknownEnd() { new PlaybackSettings("walk", 5, 4, 6, "restartUnknown"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnknownSchema() throws Exception { PlaybackSettings.fromJson(PlaybackSettings.preset("walk").json().put("schema", 99)); }
}
