package dev.scoutsolutions.fakegps;

import org.json.JSONObject;

/** Immutable per-track settings. The speed range describes cruising, not standstill or acceleration. */
public final class PlaybackSettings {
    public static final String WALK = "walk", BIKE = "bike", MOTO = "moto", CAR = "car", CUSTOM = "custom";
    public final String profile, end;
    public final double baseKmh, minKmh, maxKmh;

    public PlaybackSettings(String profile, double baseKmh, double minKmh, double maxKmh, String end) {
        if (!WALK.equals(profile) && !BIKE.equals(profile) && !MOTO.equals(profile) && !CAR.equals(profile) && !CUSTOM.equals(profile)) throw new IllegalArgumentException("Profilo di velocità non valido.");
        if (!"hold".equals(end) && !"stop".equals(end) && !"loop".equals(end)) throw new IllegalArgumentException("Comportamento all’arrivo non valido.");
        if (!valid(baseKmh) || !valid(minKmh) || !valid(maxKmh)) throw new IllegalArgumentException("Le velocità devono essere comprese tra 0 e 300 km/h.");
        if (minKmh > baseKmh || baseKmh > maxKmh) throw new IllegalArgumentException("La velocità di riferimento deve essere compresa tra minimo e massimo.");
        if (baseKmh == 0 ? maxKmh != 0 : minKmh == maxKmh) throw new IllegalArgumentException("Per oscillare, il minimo deve essere inferiore al massimo. Per restare fermo imposta tutti i valori a zero.");
        this.profile = profile; this.baseKmh = baseKmh; this.minKmh = minKmh; this.maxKmh = maxKmh; this.end = end;
    }
    private static boolean valid(double value) { return Double.isFinite(value) && value >= 0 && value <= 300; }
    public static PlaybackSettings preset(String profile) {
        switch (profile) {
            case WALK: return new PlaybackSettings(profile, 5, 4.2, 5.8, "hold");
            case BIKE: return new PlaybackSettings(profile, 18, 15, 21, "hold");
            case MOTO: return new PlaybackSettings(profile, 50, 45, 55, "hold");
            case CAR: return new PlaybackSettings(profile, 70, 63, 77, "hold");
            case CUSTOM: return custom(30, "hold");
            default: throw new IllegalArgumentException("Profilo di velocità non valido.");
        }
    }
    public static PlaybackSettings custom(double base, String end) {
        return new PlaybackSettings(CUSTOM, base, base * .9, Math.min(300, base * 1.1), end);
    }
    public PlaybackSettings withEnd(String value) { return new PlaybackSettings(profile, baseKmh, minKmh, maxKmh, value); }
    public PlaybackSettings withRange(double min, double max) { return new PlaybackSettings(profile, baseKmh, min, max, end); }
    public String label() {
        switch (profile) { case WALK: return "A piedi"; case BIKE: return "Bici"; case MOTO: return "Moto"; case CAR: return "Auto"; default: return "Personalizzata"; }
    }
    public double accelerationKmhPerSecond() {
        switch (profile) { case WALK: return 1.5; case BIKE: return 2; case MOTO: case CAR: return 4; default: return Math.max(1.5, Math.min(4, baseKmh / 10)); }
    }
    public JSONObject json() throws Exception {
        return new JSONObject().put("schema", 1).put("profile", profile).put("baseKmh", baseKmh).put("minKmh", minKmh).put("maxKmh", maxKmh).put("end", end);
    }
    public static PlaybackSettings fromJson(JSONObject obj) throws Exception {
        if (obj.optInt("schema", 1) != 1) throw new IllegalArgumentException("Versione delle impostazioni non supportata.");
        return new PlaybackSettings(obj.getString("profile"), obj.getDouble("baseKmh"), obj.getDouble("minKmh"), obj.getDouble("maxKmh"), obj.getString("end"));
    }
}
