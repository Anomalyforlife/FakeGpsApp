package dev.scoutsolutions.fakegps;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** URL parsing is independent of Android and does not fetch Google's HTML. */
public final class MapsLink {
    public static boolean googleHost(String host) {
        if(host==null)return false;
        return Arrays.asList("maps.app.goo.gl","goo.gl","google.com","www.google.com","maps.google.com","google.it","www.google.it","maps.google.it").contains(host.toLowerCase(Locale.ROOT));
    }
    public static void validate(URL url) {
        if(!"https".equals(url.getProtocol())||!googleHost(url.getHost())||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))
            throw new IllegalArgumentException("Paste a Google Maps HTTPS link (google.com, google.it or maps.app.goo.gl).");
    }
    public static boolean isFullDirections(URL url) {
        return !url.getHost().equalsIgnoreCase("maps.app.goo.gl")&&!url.getHost().equalsIgnoreCase("goo.gl")
            && (url.getPath().startsWith("/maps/dir/") || url.getPath().equals("/maps/dir") || query(url).containsKey("daddr") || query(url).containsKey("destination"));
    }
    private static String decode(String s) {
        try { return URLDecoder.decode(s,StandardCharsets.UTF_8.name()); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    private static Map<String,String> query(URL url) {
        Map<String,String> result=new HashMap<>();if(url.getQuery()!=null)for(String p:url.getQuery().split("&")){String[] v=p.split("=",2);result.put(decode(v[0]),v.length==2?decode(v[1]):"");}return result;
    }
    public static List<String> stops(String raw) throws MalformedURLException {
        URL url=new URL(raw);validate(url);List<String> stops=new ArrayList<>();Map<String,String> q=query(url);
        String origin=q.containsKey("origin")?q.get("origin"):q.get("saddr"),dest=q.containsKey("destination")?q.get("destination"):q.get("daddr");
        if(origin!=null&&dest!=null){stops.add(origin);if(q.containsKey("waypoints"))stops.addAll(Arrays.asList(q.get("waypoints").split("\\|")));stops.add(dest);}
        else {
            String[] segments=url.getPath().split("/",-1);boolean dir=false;
            for(String encoded:segments){String s=decode(encoded);if(!dir){if(s.equals("dir"))dir=true;continue;}if(s.startsWith("@")||s.startsWith("data="))break;if(!s.trim().isEmpty())stops.add(s);}
        }
        if(stops.size()<2)throw new IllegalArgumentException("This link does not expose the start and destination. Share route directions or enter the stops below. Import a GPX for an exact track.");
        for(String stop:stops){String s=stop.trim().toLowerCase(Locale.ROOT);if(s.isEmpty()||Arrays.asList("my location","your location","current location","posizione attuale","la tua posizione").contains(s))throw new IllegalArgumentException("The link uses your current location as a stop. Set an explicit starting point in Maps or enter coordinates here.");}
        return stops;
    }
}
