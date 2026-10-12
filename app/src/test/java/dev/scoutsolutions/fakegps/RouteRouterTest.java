package dev.scoutsolutions.fakegps;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteRouterTest {
    private final List<GeoPoint> stops = Arrays.asList(new GeoPoint(45.4642, 9.1900), new GeoPoint(45.4780, 9.2270));
    private static final String CAR = "{\"code\":\"Ok\",\"routes\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[9.19,45.4642],[9.23,45.4642],[9.227,45.478]]}}]}";
    private static final String FOOT = "{\"code\":\"Ok\",\"routes\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[9.19,45.4642],[9.20,45.47],[9.227,45.478]]}}]}";

    @Test public void footUsesItsOwnGraphAndReturnedPedestrianGeometry() throws Exception {
        String[] requested = {""};
        Track track = new RouteRouter(url -> { requested[0] = url; return FOOT; }).route(stops, "Passeggiata", RoutingProfile.FOOT);
        assertTrue(requested[0].startsWith("https://routing.openstreetmap.de/routed-foot/route/v1/"));
        assertFalse(requested[0].contains("routed-car"));
        assertTrue(requested[0].contains("9.19,45.4642;9.227,45.478"));
        assertEquals(45.47, track.points.get(1).lat, 1e-10);
        assertEquals(9.20, track.points.get(1).lon, 1e-10);
        assertEquals(RoutingProfile.FOOT, track.routingProfile);
        assertTrue(track.source.contains("a piedi"));
    }
    @Test public void selectingCarAndFootCanProduceDifferentRoutes() throws Exception {
        RouteRouter router = new RouteRouter(url -> url.contains("routed-foot") ? FOOT : CAR);
        Track car = router.route(stops, "Auto", RoutingProfile.CAR), foot = router.route(stops, "Piedi", RoutingProfile.FOOT);
        assertNotEquals(car.points.get(1).lon, foot.points.get(1).lon, 1e-6);
        assertTrue(car.distance() > foot.distance());
        assertTrue(car.source.contains("auto"));
        assertEquals(RoutingProfile.CAR, car.routingProfile);
    }
    @Test public void routingModeSurvivesSavingWithoutChangingPlaybackSettings() throws Exception {
        Track track = new RouteRouter(url -> FOOT).route(stops, "Passeggiata", RoutingProfile.FOOT);
        track.playback = new PlaybackSettings(PlaybackSettings.CUSTOM, 6, 5, 7, "stop");
        Track loaded = Track.fromJson(new JSONObject(track.json().toString()));
        assertEquals(RoutingProfile.FOOT, loaded.routingProfile);
        assertEquals(track.source, loaded.source);
        assertEquals(6, loaded.playback.baseKmh, 0);
        assertEquals("stop", loaded.playback.end);
        assertEquals(track.distance(), loaded.distance(), 1e-8);
    }
    @Test public void legacyAndUnknownRoutingModesKeepTracksReadable() throws Exception {
        Track track = new Track("Legacy", "Traccia GPX");track.points.addAll(stops);
        JSONObject json = track.json();
        assertNull(Track.fromJson(json).routingProfile);
        json.put("routingProfile", "future-mode");
        Track loaded = Track.fromJson(json);
        assertNull(loaded.routingProfile);
        assertEquals(2, loaded.points.size());
    }
    @Test public void missingPedestrianRouteDoesNotFallBackToCar() throws Exception {
        int[] calls = {0};
        RouteRouter router = new RouteRouter(url -> { calls[0]++; return "{\"code\":\"NoRoute\"}"; });
        try { router.route(stops, "Piedi", RoutingProfile.FOOT); fail("Atteso errore di percorso"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("a piedi")); }
        assertEquals(1, calls[0]);
    }
    @Test public void networkFailureDoesNotCreateADifferentRoute() throws Exception {
        int[] calls = {0};
        RouteRouter router = new RouteRouter(url -> { calls[0]++; throw new IOException("offline"); });
        try { router.route(stops, "Piedi", RoutingProfile.FOOT); fail("Atteso errore di rete"); }
        catch (IOException expected) { assertEquals("offline", expected.getMessage()); }
        assertEquals(1, calls[0]);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsEmptyRouteResult() throws Exception {
        new RouteRouter(url -> "{\"code\":\"Ok\",\"routes\":[]}").route(stops, "Vuoto", RoutingProfile.FOOT);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsDegenerateGeometry() throws Exception {
        new RouteRouter(url -> "{\"code\":\"Ok\",\"routes\":[{\"geometry\":{\"coordinates\":[[9,45],[9,45]]}}]}").route(stops, "Vuoto", RoutingProfile.FOOT);
    }
    @Test public void rejectsInvalidStopsBeforeAnyNetworkCall() throws Exception {
        RouteRouter router = new RouteRouter(url -> { fail("Non deve fare richieste"); return CAR; });
        for (List<GeoPoint> invalid : Arrays.asList(Collections.<GeoPoint>emptyList(), Collections.singletonList(stops.get(0)), Collections.nCopies(21, stops.get(0)))) {
            try { router.route(invalid, "Invalid", RoutingProfile.CAR); fail("Atteso errore di tappe"); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("20")); }
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsMissingProfileBeforeNetworkCall() throws Exception {
        new RouteRouter(url -> { fail("Non deve fare richieste"); return CAR; }).route(stops, "Invalid", null);
    }
}
