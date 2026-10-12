package dev.scoutsolutions.fakegps;

import org.junit.Test;
import java.net.URL;
import java.util.Arrays;
import static org.junit.Assert.*;

public class MapsLinkTest {
    @Test public void parsesCoordinateDirections() throws Exception { assertEquals(Arrays.asList("45.4642,9.19","45.478,9.227"),MapsLink.stops("https://www.google.com/maps/dir/?api=1&origin=45.4642%2C9.19&destination=45.478%2C9.227")); }
    @Test public void preservesOrderedWaypoints() throws Exception { assertEquals(Arrays.asList("Milano Duomo","Monza","Como","Lugano"),MapsLink.stops("https://www.google.it/maps/dir/?api=1&origin=Milano+Duomo&waypoints=Monza%7CComo&destination=Lugano")); }
    @Test public void parsesPathAndStopsBeforeViewport() throws Exception { assertEquals(Arrays.asList("Milano Centrale","45.46,9.19"),MapsLink.stops("https://www.google.com/maps/dir/Milano+Centrale/45.46,9.19/@45,9,13z/data=test")); }
    @Test public void supportsLegacyDirections() throws Exception { assertEquals(Arrays.asList("A","B"),MapsLink.stops("https://maps.google.com/?saddr=A&daddr=B")); }
    @Test public void recognisesFullLinkWithoutNetwork() throws Exception { assertTrue(MapsLink.isFullDirections(new URL("https://www.google.com/maps/dir/A/B")));assertFalse(MapsLink.isFullDirections(new URL("https://maps.app.goo.gl/test"))); }
    @Test(expected=IllegalArgumentException.class) public void rejectsPlaceWithoutOrigin() throws Exception { MapsLink.stops("https://www.google.com/maps/?api=1&destination=Milano"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsCurrentLocation() throws Exception { MapsLink.stops("https://www.google.com/maps/dir/?origin=Your+location&destination=Milano"); }
    @Test(expected=IllegalArgumentException.class) public void rejectsLookalikeHost() throws Exception { MapsLink.validate(new URL("https://maps.app.goo.gl.attacker.example/path")); }
    @Test(expected=IllegalArgumentException.class) public void rejectsPlainHttp() throws Exception { MapsLink.validate(new URL("http://www.google.com/maps/dir/A/B")); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUserInfo() throws Exception { MapsLink.validate(new URL("https://user@www.google.com/maps/dir/A/B")); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnexpectedPort() throws Exception { MapsLink.validate(new URL("https://www.google.com:444/maps/dir/A/B")); }
}
