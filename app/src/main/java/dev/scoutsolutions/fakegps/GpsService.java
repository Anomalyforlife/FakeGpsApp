package dev.scoutsolutions.fakegps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.*;

/** All GPS and disk work is serialized on one worker. Only immutable state reaches the UI. */
public final class GpsService extends Service implements LocationListener {
    public static final String STATIC = "static", ROUTE = "route", RECORD = "record", STOP = "stop", PAUSE = "pause", SPEED = "speed";
    private static final int NOTIFICATION_ID = 21;
    public static final class State {
        public final String mode, message, error, trackId, backend, warning;
        public final GeoPoint position;
        public final double distance, total, speed;
        public final long elapsed, lastSentNanos;
        public final boolean paused, holding, saving;
        public final int count;
        State(String mode, String message, String error, String trackId, GeoPoint position, double distance, double total, double speed, long elapsed, boolean paused, boolean holding, boolean saving, int count, String backend, String warning, long lastSentNanos) {
            this.mode=mode; this.message=message; this.error=error; this.trackId=trackId; this.position=position; this.distance=distance; this.total=total; this.speed=speed; this.elapsed=elapsed; this.paused=paused; this.holding=holding; this.saving=saving; this.count=count;
            this.backend=backend; this.warning=warning; this.lastSentNanos=lastSentNanos;
        }
        public boolean active() { return !mode.isEmpty(); }
    }
    public static volatile State state = new State("", "Ready to go", "", "", null, 0, 0, 0, 0, false, false, false, 0, "", "", 0);
    private HandlerThread thread;
    private Handler worker;
    private LocationManager locations;
    private PowerManager.WakeLock wakeLock;
    private MockSession mockSession;
    private String backend = "";
    private boolean deliveryPending;
    private Track track;
    private RouteEngine engine;
    private SpeedDynamics speedDynamics;
    private GeoPoint position;
    private String mode = "", end = "hold";
    private boolean paused, holding, finishing;
    private double speed, recordedDistance;
    private long started, lastTick, activeMillis, lastSave, lastRealFix;
    private String statusMessage = "";

    @Override public void onCreate() {
        super.onCreate(); locations = (LocationManager)getSystemService(LOCATION_SERVICE);
        thread = new HandlerThread("anomaly-gps"); thread.start(); worker = new Handler(thread.getLooper());
        NotificationChannel channel = new NotificationChannel("gps", "GPS session", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Controls for the active simulation and recording");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (STATIC.equals(action) || ROUTE.equals(action) || RECORD.equals(action)) {
            if (state.active()) return START_NOT_STICKY;
            try { startForeground(NOTIFICATION_ID, notification("Preparing session…")); }
            catch (Exception e) { state = new State("", "Unable to start", "Enable GPS and precise location permission, then try again with the app open.", "", null, 0, 0, 0, 0, false, false, false, 0, "", "", 0); stopSelf(); return START_NOT_STICKY; }
        }
        worker.post(() -> {
            try {
                if (STOP.equals(action)) finish("Session ended", "");
                else if (PAUSE.equals(action) && ROUTE.equals(mode) && !finishing && !holding) {
                    paused = !paused; lastTick = SystemClock.elapsedRealtime();
                    speed = 0;
                    if (paused) speedDynamics.resetStandstill();
                    if (RECORD.equals(mode)) lastRealFix = 0;
                    publish(paused ? "Paused" : RECORD.equals(mode) ? "Recording real GPS" : "Simulation running", "");
                } else if (SPEED.equals(action) && ROUTE.equals(mode) && !finishing && track.id.equals(intent.getStringExtra("id"))) {
                    PlaybackSettings settings = new TrackStore(this).load(track.id).playback;
                    if (settings == null) throw new IllegalArgumentException("Save the route settings first.");
                    track.playback = settings; speedDynamics.configure(settings);
                    lastTick = SystemClock.elapsedRealtime(); publish(statusMessage, "");
                } else if (STATIC.equals(action) || ROUTE.equals(action) || RECORD.equals(action)) startSession(action, intent);
            } catch (Exception e) { finish("Session interrupted", friendly(e)); }
        });
        return START_NOT_STICKY;
    }
    @SuppressLint({"MissingPermission", "WakelockTimeout"})
    private void startSession(String action, Intent intent) throws Exception {
        if (!mode.isEmpty() || finishing) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("Precise location permission is required.");
        if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) throw new IllegalStateException("Enable GPS location in Android settings.");
        mode = action; paused = false; holding = false; deliveryPending = false; started = lastTick = SystemClock.elapsedRealtime(); activeMillis = 0; recordedDistance = 0; lastRealFix = 0; lastSave = started;
        publish("Preparing session…", "");
        speed = 0; end = "hold";
        if (RECORD.equals(mode)) {
            backend = "Real GPS";
            track = new Track(intent.getStringExtra("name") == null ? "Recorded trip" : intent.getStringExtra("name"), "Real GPS · recording");
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, worker.getLooper());
            publish("Waiting for a real GPS signal…", "");
        } else {
            if (ROUTE.equals(mode)) {
                track = new TrackStore(this).load(intent.getStringExtra("id")); engine = new RouteEngine(track.points); position = engine.position();
                PlaybackSettings settings = track.playback == null ? PlaybackSettings.preset(PlaybackSettings.WALK) : track.playback;
                end = settings.end; speedDynamics = new SpeedDynamics(settings, new Random());
            }
            else position = new GeoPoint(intent.getDoubleExtra("lat", Double.NaN), intent.getDoubleExtra("lon", Double.NaN), intent.getDoubleExtra("alt", 0), 0);
            int availability = AndroidMockBackend.googleAvailability(this);
            boolean google = AndroidMockBackend.hasGoogle(availability);
            backend = google ? "Android GPS + Google Play Services" : "Android GPS · Google Play Services unavailable (" + availability + ")";
            AndroidMockBackend.Platform platform = new AndroidMockBackend.Platform(locations, !google);
            mockSession = google ? new MockSession(platform, new AndroidMockBackend.Google(this)) : new MockSession(platform);
            mockSession.start();
            sendMock(0, 0); publish(STATIC.equals(mode) ? "Fixed location active" : "Simulation running", "");
        }
        // Connection setup time must not advance a route before its first fix.
        lastTick = SystemClock.elapsedRealtime();
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AnomalyGPS:session");
        wakeLock.acquire(12 * 60 * 60 * 1000L); // Sessions stop after 12 hours, releasing this lock.
        worker.postDelayed(tick, 500);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (mode.isEmpty() || finishing) return;
            try {
                long now = SystemClock.elapsedRealtime(); double seconds = (now - lastTick) / 1000.0; lastTick = now;
                if (now - started >= 12 * 60 * 60 * 1000L) { finish("Session ended after 12 hours", ""); return; }
                if (!paused && !holding && !deliveryPending) activeMillis += Math.round(seconds * 1000);
                if (RECORD.equals(mode)) {
                    if (now - lastSave >= 10000 && track.points.size() >= 2) { new TrackStore(GpsService.this).save(track); lastSave = now; }
                    if (!paused && lastRealFix > 0 && now - lastRealFix > 10000) statusMessage = "GPS signal lost · waiting…";
                    publish(statusMessage, "");
                } else {
                    if (engine != null && !paused && !holding && !deliveryPending) {
                        SpeedDynamics.Step step = speedDynamics.advance(seconds);
                        speed = step.speedKmh; engine.advanceMeters(step.meters); position = engine.position();
                    }
                    if (engine != null && engine.finished() && !holding) {
                        if ("stop".equals(end)) {
                            sendMock(0, engine.bearing());
                            if (!deliveryPending) { finish("Route completed · real GPS restored", ""); return; }
                            publish("Arrived · waiting for final delivery confirmation", "");
                            worker.postDelayed(this, 500); return;
                        }
                        if ("loop".equals(end)) { engine.restart(); position = engine.position(); }
                        else holding = true;
                    }
                    if (paused || holding) speed = 0;
                    sendMock(engine != null && !paused && !holding ? (float)(speed / 3.6) : 0, engine != null ? engine.bearing() : 0);
                    publish(holding ? "Arrived · holding at destination" : paused ? "Paused · holding position" : STATIC.equals(mode) ? "Fixed location active" : "Simulation running", "");
                }
                worker.postDelayed(this, 500);
            } catch (Exception e) { finish("Session interrupted", friendly(e)); }
        }
    };
    private void sendMock(float metersPerSecond, float bearing) throws Exception {
        boolean wasPending = deliveryPending;
        deliveryPending = !mockSession.send(new MockSession.Fix(position, metersPerSecond, bearing, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()));
        // A failed delivery is retried at the same position. Waiting for IPC must not create a catch-up jump.
        if (deliveryPending || wasPending) lastTick = SystemClock.elapsedRealtime();
    }
    @Override public void onLocationChanged(Location location) {
        if (!RECORD.equals(mode) || paused || finishing) return;
        if (location.isFromMockProvider()) { publish("Mock location detected: turn off other fake GPS apps.", ""); return; }
        if (!location.hasAccuracy() || location.getAccuracy() > 50 || SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos() > 10000000000L) return;
        GeoPoint p = new GeoPoint(location.getLatitude(), location.getLongitude(), location.hasAltitude() ? location.getAltitude() : 0, location.getTime());
        if (track.points.size() >= 100000) { finish("100,000-point limit reached · track saved", ""); return; }
        if (!track.points.isEmpty()) {
            GeoPoint last = track.points.get(track.points.size()-1); double d = last.distanceTo(p);
            if (d < 2) { position = p; lastRealFix = SystemClock.elapsedRealtime(); publish("Recording real GPS", ""); return; }
            recordedDistance += d;
        }
        track.points.add(p); position = p; lastRealFix = SystemClock.elapsedRealtime();
        publish("Recording real GPS · accuracy " + Math.round(location.getAccuracy()) + " m", "");
    }
    @Override public void onProviderDisabled(String provider) { if (RECORD.equals(mode)) publish("GPS disabled · enable location again", ""); }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    private void publish(String message, String error) {
        statusMessage = message;
        state = new State(mode, message, error, track == null ? "" : track.id, position, engine == null ? recordedDistance : engine.travelled(), engine == null ? 0 : engine.total(), speed, activeMillis, paused, holding, finishing, track == null ? 0 : track.points.size(), backend, mockSession == null ? "" : mockSession.warning(), mockSession == null ? 0 : mockSession.lastSentNanos());
        // Avoid re-alerts: the channel is silent and the notification only updates on text changes.
        if (!message.equals(lastNotification)) {
            if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(message));
            lastNotification = message;
        }
    }
    private String lastNotification = "";
    private void finish(String message, String error) {
        if (finishing) return; finishing = true; worker.removeCallbacks(tick);
        String cleanupError = cleanupGps();
        if (!cleanupError.isEmpty()) error += (error.isEmpty() ? "" : "\n") + "GPS restoration not confirmed: " + cleanupError + ". Check your location before recording.";
        if (RECORD.equals(mode) && track != null && track.points.size() >= 2) {
            try { new TrackStore(this).save(track); message += " · track saved"; }
            catch (Exception e) { error = "Unable to save: " + e.getMessage(); }
        } else if (RECORD.equals(mode)) message += " · no track saved (fewer than two points)";
        String id = track == null ? "" : track.id;
        state = new State("", message, error, id, position, engine == null ? recordedDistance : engine.travelled(), engine == null ? 0 : engine.total(), 0, activeMillis, false, holding, false, track == null ? 0 : track.points.size(), backend, "", mockSession == null ? 0 : mockSession.lastSentNanos());
        mode = ""; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @SuppressLint("MissingPermission") // Cleanup also runs after a user revokes location access.
    private String cleanupGps() {
        try { locations.removeUpdates(this); } catch (Exception ignored) { }
        String error = mockSession == null ? "" : mockSession.stop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); wakeLock = null;
        return error;
    }
    private static String friendly(Exception e) {
        String message = e instanceof SecurityException ? "Allow precise location and select Anomaly GPS in Developer options → Select mock location app." : e.getMessage() == null ? "GPS error. Check Android settings." : e.getMessage();
        for (Throwable suppressed : e.getSuppressed()) message += "\nGPS restoration not confirmed: " + suppressed.getMessage();
        return message;
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, GpsService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "gps").setSmallIcon(R.drawable.ic_notification).setContentTitle("Anomaly GPS").setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
    }
    @Override public void onDestroy() {
        worker.post(() -> { if (!mode.isEmpty() && !finishing) finish("Session ended by the system", ""); else cleanupGps(); });
        thread.quitSafely(); super.onDestroy();
    }
}
