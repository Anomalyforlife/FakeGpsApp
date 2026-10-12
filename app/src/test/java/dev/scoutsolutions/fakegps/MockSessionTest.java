package dev.scoutsolutions.fakegps;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MockSessionTest {
    private static final class Fake implements MockSession.Backend {
        final String name;
        final List<String> events;
        Exception startError, sendError, stopError;
        MockSession.Fix last;
        Fake(String name, List<String> events) { this.name = name; this.events = events; }
        public void start() throws Exception { events.add(name + ":start"); if (startError != null) throw startError; }
        public void send(MockSession.Fix fix) throws Exception { events.add(name + ":send"); last = fix; if (sendError != null) throw sendError; }
        public void stop() throws Exception { events.add(name + ":stop"); if (stopError != null) throw stopError; }
    }
    private MockSession.Fix fix(long elapsed) { return new MockSession.Fix(new GeoPoint(45, 9), 5, 90, 123456, elapsed); }

    @Test public void enablesBothBeforeSendingTheIdenticalFix() throws Exception {
        List<String> events = new ArrayList<>(); Fake platform = new Fake("android", events), google = new Fake("google", events);
        MockSession session = new MockSession(platform, google); session.start(); MockSession.Fix point = fix(100);
        assertTrue(session.send(point)); assertSame(point, platform.last); assertSame(point, google.last);
        assertEquals(Arrays.asList("android:start", "google:start", "android:send", "google:send"), events);
        assertEquals(100, session.lastSentNanos());
    }
    @Test public void failedEnableRestoresBothIncludingPartlyStartedBackend() throws Exception {
        List<String> events = new ArrayList<>(); Fake platform = new Fake("android", events), google = new Fake("google", events);
        google.startError = new Exception("timeout"); MockSession session = new MockSession(platform, google);
        try { session.start(); fail(); } catch (Exception expected) { assertEquals("timeout", expected.getMessage()); }
        assertEquals(Arrays.asList("android:start", "google:start", "google:stop", "android:stop"), events);
        assertEquals("", session.stop()); assertEquals(4, events.size());
    }
    @Test public void rollbackFailureIsReportedWithoutSkippingPlatformRestore() {
        List<String> events = new ArrayList<>(); Fake platform = new Fake("android", events), google = new Fake("google", events);
        google.startError = new Exception("enable failed"); google.stopError = new Exception("disable failed");
        try { new MockSession(platform, google).start(); fail(); }
        catch (Exception expected) { assertEquals(1, expected.getSuppressed().length); assertTrue(expected.getSuppressed()[0].getMessage().contains("disable failed")); }
        assertTrue(events.contains("android:stop"));
    }
    @Test public void transientFailureRetainsLastConfirmationAndRecoversWithoutReenabling() throws Exception {
        List<String> events = new ArrayList<>(); Fake platform = new Fake("android", events), google = new Fake("google", events);
        MockSession session = new MockSession(platform, google); session.start(); session.send(fix(100));
        google.sendError = new Exception("temporary"); MockSession.Fix point = fix(200);
        assertFalse(session.send(point)); assertEquals(100, session.lastSentNanos()); assertFalse(session.warning().isEmpty());
        google.sendError = null; assertTrue(session.send(point)); assertSame(point, google.last); assertEquals(200, session.lastSentNanos()); assertEquals("", session.warning());
        assertEquals(1, events.stream().filter(e -> e.equals("google:start")).count());
    }
    @Test public void persistentFailureStopsAfterThreeAttempts() throws Exception {
        Fake backend = new Fake("google", new ArrayList<>()); MockSession session = new MockSession(backend); session.start(); backend.sendError = new Exception("offline");
        assertFalse(session.send(fix(1))); assertFalse(session.send(fix(2)));
        try { session.send(fix(3)); fail(); } catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("tre volte")); }
        assertEquals(0, session.lastSentNanos());
    }
    @Test public void successfulDeliveryResetsFailureBudget() throws Exception {
        Fake backend = new Fake("google", new ArrayList<>()); MockSession session = new MockSession(backend); session.start(); backend.sendError = new Exception("offline");
        assertFalse(session.send(fix(1))); assertFalse(session.send(fix(2))); backend.sendError = null; assertTrue(session.send(fix(3)));
        backend.sendError = new Exception("offline again"); assertFalse(session.send(fix(4))); assertFalse(session.send(fix(5)));
    }
    @Test public void nativeFailureDoesNotSkipOtherBackend() throws Exception {
        List<String> events = new ArrayList<>(); Fake platform = new Fake("android", events), google = new Fake("google", events);
        MockSession session = new MockSession(platform, google); session.start(); platform.sendError = new Exception("provider removed");
        MockSession.Fix point = fix(10); assertFalse(session.send(point)); assertSame(point, google.last);
    }
    @Test public void revokedAuthorizationFailsImmediately() throws Exception {
        Fake backend = new Fake("android", new ArrayList<>()); MockSession session = new MockSession(backend); session.start(); backend.sendError = new SecurityException("revoked");
        try { session.send(fix(1)); fail(); } catch (SecurityException expected) { assertEquals("revoked", expected.getMessage()); }
    }
    @Test public void disableFailureDoesNotSkipOtherCleanupAndCannotBeSilenced() throws Exception {
        List<String> events = new ArrayList<>(); Fake platform = new Fake("android", events), google = new Fake("google", events);
        MockSession session = new MockSession(platform, google); session.start(); google.stopError = new Exception("disable failed");
        assertTrue(session.stop().contains("disable failed")); assertEquals(Arrays.asList("android:start", "google:start", "google:stop", "android:stop"), events);
        assertEquals("", session.stop()); assertEquals(4, events.size());
    }
    @Test public void deviceWithoutGoogleUsesPlatformAlone() throws Exception {
        Fake platform = new Fake("android", new ArrayList<>()); MockSession session = new MockSession(platform); session.start();
        assertTrue(session.send(fix(50))); assertEquals(50, session.lastSentNanos()); assertEquals("", session.stop());
    }
    @Test(expected=IllegalStateException.class) public void cannotSendAfterStop() throws Exception {
        MockSession session = new MockSession(new Fake("android", new ArrayList<>())); session.start(); session.stop(); session.send(fix(1));
    }
}
