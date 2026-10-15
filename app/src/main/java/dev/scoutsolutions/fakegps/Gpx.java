package dev.scoutsolutions.fakegps;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import java.io.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

public final class Gpx {
    public static Track read(InputStream stream, String fallback) throws Exception {
        // Limit input before parsing. Reject DTD/entities; no external entity resolution.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = stream.read(buffer)) != -1) { if (bytes.size() + n > 12 * 1024 * 1024) throw new IOException("GPX file too large (12 MB maximum)."); bytes.write(buffer, 0, n); }
        String xml = bytes.toString(StandardCharsets.UTF_8.name());
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) throw new IOException("GPX files with XML entities are not supported.");
        XmlPullParser p = Xml.newPullParser(); p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); p.setInput(new StringReader(xml));
        Track track = new Track(fallback, "GPX · original track");
        double lat = 0, lon = 0, alt = 0; long time = 0; boolean inPoint = false, root = false; int segments = 0;
        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next()) {
            String tag = p.getName();
            if (event == XmlPullParser.START_TAG) {
                if ("gpx".equals(tag)) root = true;
                if ("trkseg".equals(tag) || "rte".equals(tag)) { if (++segments > 1) throw new IOException("The GPX contains multiple segments. Import a continuous track to avoid jumps between segments."); }
                if ("trkpt".equals(tag) || "rtept".equals(tag)) { lat = Double.parseDouble(p.getAttributeValue(null, "lat")); lon = Double.parseDouble(p.getAttributeValue(null, "lon")); alt = 0; time = 0; inPoint = true; }
                else if (inPoint && "ele".equals(tag)) alt = Double.parseDouble(p.nextText());
                else if (inPoint && "time".equals(tag)) { try { time = Instant.parse(p.nextText()).toEpochMilli(); } catch (Exception ignored) { time = 0; } }
                else if (!inPoint && "name".equals(tag) && track.points.isEmpty()) { String name = p.nextText().trim(); if (!name.isEmpty()) track.name = name.substring(0, Math.min(name.length(), 100)); }
            } else if (event == XmlPullParser.END_TAG && ("trkpt".equals(tag) || "rtept".equals(tag))) {
                track.points.add(new GeoPoint(lat, lon, alt, time)); inPoint = false;
                if (track.points.size() > 100000) throw new IOException("Maximum 100,000 points per track.");
            }
        }
        if (!root || track.points.size() < 2) throw new IOException("The file must be a GPX with at least two trkpt or rtept points.");
        new RouteEngine(track.points); return track;
    }
    public static void write(Track track, OutputStream stream) throws Exception {
        XmlSerializer x = Xml.newSerializer(); x.setOutput(stream, "UTF-8"); x.startDocument("UTF-8", true);
        x.startTag(null, "gpx").attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1").attribute(null, "version", "1.1").attribute(null, "creator", "Anomaly GPS");
        x.startTag(null, "trk").startTag(null, "name").text(track.name).endTag(null, "name").startTag(null, "trkseg");
        for (GeoPoint p : track.points) {
            x.startTag(null, "trkpt").attribute(null, "lat", Double.toString(p.lat)).attribute(null, "lon", Double.toString(p.lon));
            x.startTag(null, "ele").text(Double.toString(p.altitude)).endTag(null, "ele");
            if (p.time > 0) x.startTag(null, "time").text(Instant.ofEpochMilli(p.time).toString()).endTag(null, "time");
            x.endTag(null, "trkpt");
        }
        x.endTag(null, "trkseg").endTag(null, "trk").endTag(null, "gpx"); x.endDocument(); x.flush();
    }
}
