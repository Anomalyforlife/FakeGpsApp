package dev.scoutsolutions.fakegps;

/** Routing geometry is independent of the playback speed profile. */
public enum RoutingProfile {
    CAR("Auto", "car", "auto"),
    FOOT("A piedi", "foot", "a piedi");

    public final String label, id;
    private final String description;
    RoutingProfile(String label, String id, String description) {
        this.label = label; this.id = id; this.description = description;
    }
    public String source() { return "OSRM · percorso " + description + " ricalcolato"; }
    public String endpoint() {
        // OSRM chooses the graph on the server, not from the /driving/ URL token.
        return "https://routing.openstreetmap.de/routed-" + id + "/route/v1/driving/";
    }
    public static RoutingProfile fromPreference(String value) {
        return FOOT.id.equals(value) ? FOOT : CAR;
    }
    public static RoutingProfile fromId(String value) {
        for (RoutingProfile profile : values()) if (profile.id.equals(value)) return profile;
        return null;
    }
}
