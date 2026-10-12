package dev.scoutsolutions.fakegps;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class RouteEngineTest {
    private RouteEngine straight() { return new RouteEngine(Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0, 0.01))); }
    @Test public void speedUsesKilometresPerHour() { RouteEngine r = straight(); r.advance(10, 36); assertEquals(100, r.travelled(), 0.001); assertEquals(100, new GeoPoint(0, 0).distanceTo(r.position()), 0.01); }
    @Test public void zeroSpeedKeepsPosition() { RouteEngine r = straight(); r.advance(4, 36); GeoPoint p = r.position(); r.advance(900, 0); assertEquals(p.lon, r.position().lon, 0); }
    @Test public void clampsToDestination() { RouteEngine r = straight(); r.advance(10000, 200); assertTrue(r.finished()); assertEquals(0.01, r.position().lon, 0); assertEquals(r.total(), r.travelled(), 0); }
    @Test public void restartReturnsToStart() { RouteEngine r = straight(); r.advance(12, 40); r.restart(); assertEquals(0, r.travelled(), 0); assertEquals(0, r.position().lon, 1e-10); }
    @Test public void crossesSegmentsAndDuplicateVertices() { RouteEngine r = new RouteEngine(Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0, 0), new GeoPoint(0, .001), new GeoPoint(.001, .001))); r.advance(15, 36); assertTrue(r.position().lat > 0); assertEquals(.001, r.position().lon, 1e-10); }
    @Test public void antimeridianUsesShortArc() { GeoPoint p = GeoPoint.interpolate(new GeoPoint(0, 179.9), new GeoPoint(0, -179.9), .5); assertEquals(180, Math.abs(p.lon), 1e-9); }
    @Test public void knownDistanceAndBearing() { assertEquals(111195, new GeoPoint(0, 0).distanceTo(new GeoPoint(0, 1)), 2); assertEquals(90, new GeoPoint(0, 0).bearingTo(new GeoPoint(0, 1)), .01); }
    @Test(expected=IllegalArgumentException.class) public void rejectsSinglePoint() { new RouteEngine(Arrays.asList(new GeoPoint(0, 0))); }
    @Test(expected=IllegalArgumentException.class) public void rejectsStationaryTrack() { new RouteEngine(Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0, 0))); }
    @Test(expected=IllegalArgumentException.class) public void rejectsNaNCoordinate() { new GeoPoint(Double.NaN, 0); }
    @Test(expected=IllegalArgumentException.class) public void rejectsNegativeSpeed() { straight().advance(1, -1); }
    @Test(expected=IllegalArgumentException.class) public void rejectsInfiniteTime() { straight().advance(Double.POSITIVE_INFINITY, 10); }
}
