package dev.scoutsolutions.fakegps;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

public final class TrackStore {
    private static final Object LOCK = new Object();
    private final File dir;
    public TrackStore(Context ctx) { dir = new File(ctx.getFilesDir(), "tracks"); if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Archivio non disponibile."); }
    private File file(String id) {
        if (!id.matches("[a-fA-F0-9-]{36}")) throw new IllegalArgumentException("Identificativo traccia non valido.");
        return new File(dir, id + ".json");
    }
    public void save(Track track) throws Exception { synchronized (LOCK) {
        AtomicFile f = new AtomicFile(file(track.id)); FileOutputStream out = null;
        try { out = f.startWrite(); out.write(track.json().toString().getBytes(StandardCharsets.UTF_8)); f.finishWrite(out); }
        catch (Exception e) { if (out != null) f.failWrite(out); throw e; }
    } }
    public Track load(String id) throws Exception { synchronized (LOCK) {
        byte[] data = new AtomicFile(file(id)).readFully();
        return Track.fromJson(new JSONObject(new String(data, StandardCharsets.UTF_8)));
    } }
    public List<Track> list() throws Exception { synchronized (LOCK) {
        List<Track> result = new ArrayList<>(); File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) for (File f : files) result.add(load(f.getName().replace(".json", "")));
        result.sort((a, b) -> Long.compare(b.created, a.created)); return result;
    } }
    // Reload under the same lock so metadata edits never replace a newer route or settings snapshot.
    public Track updatePlayback(String id, PlaybackSettings playback) throws Exception { synchronized (LOCK) {
        if (playback == null) throw new IllegalArgumentException("Impostazioni mancanti.");
        Track latest = load(id); latest.playback = playback; save(latest); return latest;
    } }
    public Track rename(String id, String name) throws Exception { synchronized (LOCK) {
        Track latest = load(id); latest.name = name; save(latest); return latest;
    } }
    public void delete(String id) { synchronized (LOCK) { new AtomicFile(file(id)).delete(); } }
}
