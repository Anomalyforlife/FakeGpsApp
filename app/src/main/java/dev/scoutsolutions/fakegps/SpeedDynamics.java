package dev.scoutsolutions.fakegps;

import java.util.Random;

/** Smooth random targets and an exact speed integral keep route distance and reported speed coherent. */
public final class SpeedDynamics {
    public static final class Step {
        public final double speedKmh, meters;
        Step(double speedKmh, double meters) { this.speedKmh = speedKmh; this.meters = meters; }
    }
    private final Random random;
    private PlaybackSettings settings;
    private double current, from, target, duration, phase;
    public SpeedDynamics(PlaybackSettings settings, Random random) {
        if (settings == null || random == null) throw new IllegalArgumentException("Missing settings.");
        this.random = random; this.settings = settings; resetStandstill();
    }
    public double speedKmh() { return current; }
    public void configure(PlaybackSettings next) {
        if (next == null) throw new IllegalArgumentException("Missing settings.");
        settings = next; transitionTo(next.baseKmh);
    }
    public void resetStandstill() { current = 0; transitionTo(settings.baseKmh); }
    private void transitionTo(double value) {
        from = current; target = value; phase = 0;
        // Smoothstep has a maximum slope of 1.5. Bound acceleration even for very wide ranges.
        double cadence = from == 0 && target > 0 ? 3 + random.nextDouble() * 2 : 6 + random.nextDouble() * 8;
        duration = Math.max(cadence, 1.5 * Math.abs(target - from) / settings.accelerationKmhPerSecond());
    }
    private void nextTarget() {
        double next;
        if (random.nextBoolean()) next = settings.baseKmh - (settings.baseKmh - settings.minKmh) * random.nextDouble();
        else next = settings.baseKmh + (settings.maxKmh - settings.baseKmh) * random.nextDouble();
        double width = settings.maxKmh - settings.minKmh;
        if (Math.abs(next - current) < width * .05) {
            next = current <= (settings.minKmh + settings.maxKmh) / 2
                ? settings.maxKmh - width * random.nextDouble() * .35
                : settings.minKmh + width * random.nextDouble() * .35;
        }
        transitionTo(next);
    }
    private static double easing(double u) { return u * u * (3 - 2 * u); }
    private static double integral(double u) { return u * u * u - .5 * u * u * u * u; }
    public Step advance(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 43200) throw new IllegalArgumentException("Invalid time interval.");
        if (settings.maxKmh == 0 && current == 0) return new Step(0, 0);
        double remaining = seconds, kmhSeconds = 0;
        while (remaining > 0) {
            double dt = Math.min(remaining, duration - phase);
            double before = phase / duration, after = (phase + dt) / duration;
            kmhSeconds += from * dt + (target - from) * duration * (integral(after) - integral(before));
            phase += dt; remaining -= dt;
            current = from + (target - from) * easing(Math.min(1, phase / duration));
            if (duration - phase < 1e-9) { current = target; nextTarget(); }
        }
        return new Step(current, Math.max(0, kmhSeconds / 3.6));
    }
}
