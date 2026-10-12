package dev.scoutsolutions.fakegps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Platform providers and Play Services FLP are separate APIs, both requiring mock-app authorization. */
final class AndroidMockBackend {
    static int googleAvailability(Context context) { return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context); }
    static boolean hasGoogle(int availability) { return availability == ConnectionResult.SUCCESS; }

    static Location location(String provider, MockSession.Fix fix) {
        Location location = new Location(provider);
        location.setLatitude(fix.point.lat); location.setLongitude(fix.point.lon); location.setAltitude(fix.point.altitude);
        location.setAccuracy(3); location.setVerticalAccuracyMeters(5);
        location.setSpeed(fix.speed); location.setSpeedAccuracyMetersPerSecond(.2f);
        location.setBearing(fix.bearing); location.setBearingAccuracyDegrees(5);
        location.setTime(fix.time); location.setElapsedRealtimeNanos(fix.elapsedNanos);
        return location;
    }

    @SuppressLint({"MissingPermission", "InlinedApi"})
    @SuppressWarnings("deprecation")
    static final class Platform implements MockSession.Backend {
        private final LocationManager manager;
        private final boolean frameworkFused;
        private final List<String> providers = new ArrayList<>();
        Platform(LocationManager manager, boolean frameworkFused) { this.manager = manager; this.frameworkFused = frameworkFused; }
        private void add(String name) {
            manager.addTestProvider(name, false, LocationManager.GPS_PROVIDER.equals(name), false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE);
            if (!providers.contains(name)) providers.add(name);
            manager.setTestProviderEnabled(name, true);
        }
        @Override public void start() {
            add(LocationManager.GPS_PROVIDER);
            add(LocationManager.NETWORK_PROVIDER);
            if (frameworkFused) try { add("fused"); } catch (IllegalArgumentException ignored) { /* Optional framework provider on non-GMS devices. */ }
        }
        @Override public void send(MockSession.Fix fix) {
            for (String provider : providers) {
                try { manager.setTestProviderLocation(provider, location(provider, fix)); }
                catch (IllegalArgumentException missingProvider) {
                    // Recover the same position if Android removed a test provider; never restart the route.
                    add(provider); manager.setTestProviderLocation(provider, location(provider, fix));
                }
            }
        }
        @Override public void stop() throws Exception {
            Exception failure = null;
            for (String provider : providers) {
                try { manager.removeTestProvider(provider); }
                catch (IllegalArgumentException alreadyRemoved) { /* Already restored by Android. */ }
                catch (Exception e) { if (failure == null) failure = e; }
            }
            providers.clear();
            if (failure != null) throw failure;
        }
    }

    @SuppressLint("MissingPermission")
    static final class Google implements MockSession.Backend {
        private final FusedLocationProviderClient client;
        Google(Context context) { client = LocationServices.getFusedLocationProviderClient(context); }
        @Override public void start() throws Exception { await(client.setMockMode(true)); }
        @Override public void send(MockSession.Fix fix) throws Exception { await(client.setMockLocation(location("fused", fix))); }
        @Override public void stop() throws Exception { await(client.setMockMode(false)); }
        private static void await(Task<Void> task) throws Exception {
            // Service worker only. A bounded wait confirms ordering and surfaces failures to the UI.
            try { Tasks.await(task, 3, TimeUnit.SECONDS); }
            catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                throw e;
            }
        }
    }
}
