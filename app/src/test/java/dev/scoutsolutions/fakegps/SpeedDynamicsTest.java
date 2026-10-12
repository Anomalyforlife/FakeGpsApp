package dev.scoutsolutions.fakegps;

import java.util.Arrays;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpeedDynamicsTest {
    private SpeedDynamics dynamics(String profile) { return new SpeedDynamics(PlaybackSettings.preset(profile), new Random(12345)); }
    @Test public void startsFromStandstillAndAcceleratesProgressively() {
        SpeedDynamics d = dynamics(PlaybackSettings.WALK); assertEquals(0, d.speedKmh(), 0);
        SpeedDynamics.Step step = d.advance(.5); assertTrue(step.speedKmh > 0); assertTrue(step.speedKmh < 5); assertTrue(step.meters > 0);
        assertTrue(step.meters < step.speedKmh / 3.6 * .5);
    }
    @Test public void cruisesInsideConfiguredRangeAndKeepsVarying() {
        SpeedDynamics d = dynamics(PlaybackSettings.WALK); d.advance(120); double lowest = 1000, highest = 0;
        for (int i = 0; i < 5000; i++) { double speed = d.advance(.5).speedKmh; assertTrue(speed >= 4.2 - 1e-9); assertTrue(speed <= 5.8 + 1e-9); lowest = Math.min(lowest, speed); highest = Math.max(highest, speed); }
        assertTrue(highest - lowest > .5);
    }
    @Test public void allProfilesRespectAccelerationLimit() {
        for (String profile : new String[]{PlaybackSettings.WALK, PlaybackSettings.BIKE, PlaybackSettings.MOTO, PlaybackSettings.CAR, PlaybackSettings.CUSTOM}) {
            PlaybackSettings settings = PlaybackSettings.preset(profile); SpeedDynamics d = new SpeedDynamics(settings, new Random(7));
            for (int i = 0; i < 12000; i++) { double before = d.speedKmh(), after = d.advance(.1).speedKmh; assertTrue(profile, Math.abs(after - before) <= settings.accelerationKmhPerSecond() * .1 + 1e-8); }
        }
    }
    @Test public void distanceDoesNotDependOnTickSize() {
        SpeedDynamics one = dynamics(PlaybackSettings.CAR), many = dynamics(PlaybackSettings.CAR);
        SpeedDynamics.Step whole = one.advance(1000); double meters = 0;
        for (int i = 0; i < 2000; i++) meters += many.advance(.5).meters;
        assertEquals(whole.meters, meters, 1e-6); assertEquals(one.speedKmh(), many.speedKmh(), 1e-8);
    }
    @Test public void integratedDistanceIsAppliedToRouteWithoutReset() {
        RouteEngine route = new RouteEngine(Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0, 1)));
        SpeedDynamics d = dynamics(PlaybackSettings.BIKE); double expected = 0;
        for (int i = 0; i < 100; i++) { SpeedDynamics.Step step = d.advance(.5); expected += step.meters; route.advanceMeters(step.meters); }
        double before = route.travelled(); d.configure(PlaybackSettings.preset(PlaybackSettings.CAR));
        SpeedDynamics.Step changed = d.advance(.5); route.advanceMeters(changed.meters); expected += changed.meters;
        assertTrue(route.travelled() > before); assertEquals(expected, route.travelled(), 1e-8);
    }
    @Test public void changingRangePreservesCurrentSpeedThenTransitionsIntoNewRange() {
        SpeedDynamics d = dynamics(PlaybackSettings.CAR); d.advance(120); double before = d.speedKmh();
        d.configure(PlaybackSettings.preset(PlaybackSettings.WALK)); assertEquals(before, d.speedKmh(), 0);
        double first = d.advance(.1).speedKmh; assertTrue(Math.abs(first - before) <= .15 + 1e-8);
        d.advance(300); assertTrue(d.speedKmh() >= 4.2 && d.speedKmh() <= 5.8);
    }
    @Test public void zeroConfigurationNeverCreatesMovement() {
        SpeedDynamics d = new SpeedDynamics(PlaybackSettings.custom(0, "hold"), new Random(5));
        assertEquals(0, d.advance(3600).meters, 0); assertEquals(0, d.speedKmh(), 0);
    }
    @Test public void changingToZeroDeceleratesBeforeStopping() {
        SpeedDynamics d = dynamics(PlaybackSettings.CAR); d.advance(120); double before = d.speedKmh();
        d.configure(PlaybackSettings.custom(0, "hold")); assertEquals(before, d.speedKmh(), 0);
        double first = d.advance(.5).speedKmh; assertTrue(first > 0 && first < before);
        d.advance(600); assertEquals(0, d.speedKmh(), 0); assertEquals(0, d.advance(10).meters, 0);
    }
    @Test public void resumeAfterStandstillStartsANewRamp() {
        SpeedDynamics d = dynamics(PlaybackSettings.BIKE); d.advance(120); d.resetStandstill();
        assertEquals(0, d.speedKmh(), 0); assertEquals(0, d.advance(0).meters, 0);
        double resumed = d.advance(.5).speedKmh; assertTrue(resumed > 0 && resumed < 15);
    }
    @Test public void differentSessionsCanHaveDifferentVariations() {
        PlaybackSettings s = PlaybackSettings.preset(PlaybackSettings.WALK);
        SpeedDynamics a = new SpeedDynamics(s, new Random(1)), b = new SpeedDynamics(s, new Random(2));
        assertNotEquals(a.advance(90).speedKmh, b.advance(90).speedKmh, 1e-6);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsNonFiniteTime() { dynamics(PlaybackSettings.WALK).advance(Double.NaN); }
    @Test(expected=IllegalArgumentException.class) public void rejectsNegativeTime() { dynamics(PlaybackSettings.WALK).advance(-1); }
    @Test(expected=IllegalArgumentException.class) public void boundsWorkToMaximumSessionLength() { dynamics(PlaybackSettings.WALK).advance(43201); }
}
