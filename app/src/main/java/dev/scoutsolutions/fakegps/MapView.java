package dev.scoutsolutions.fakegps;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.util.LruCache;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** Native slippy map; visible tiles only, persistent 7-day cache and OSM attribution. */
public final class MapView extends View {
    public interface OnPick { void pick(GeoPoint point); }
    public OnPick onPick;
    private double centerX, centerY;
    private int zoom = 13;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tileRect = new RectF();
    private final Path routePath = new Path();
    private final LruCache<String, Bitmap> cache = new LruCache<>(48);
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> failures = new ConcurrentHashMap<>();
    private final ExecutorService loader = Executors.newFixedThreadPool(2);
    private final File tileDir;
    private boolean alive = true, moved;
    private float lastX, lastY, startX, startY;
    private List<GeoPoint> route = Collections.emptyList();
    private GeoPoint marker = new GeoPoint(45.4642, 9.19);
    private final ScaleGestureDetector pinch;
    public MapView(Context context) {
        super(context); setLayerType(LAYER_TYPE_SOFTWARE, null);
        tileDir = new File(context.getCacheDir(), "map-tiles"); if (!tileDir.exists()) tileDir.mkdirs();
        center(marker);
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private float accumulated = 1;
            @Override public boolean onScale(ScaleGestureDetector d) { accumulated *= d.getScaleFactor(); if (accumulated > 1.5) { zoom(1); accumulated = 1; } else if (accumulated < .67) { zoom(-1); accumulated = 1; } return true; }
        });
        setContentDescription("Mappa OpenStreetMap. Trascina per spostarti, pizzica per lo zoom, tocca per scegliere una posizione.");
    }
    public void center(GeoPoint p) { centerX = x(p.lon); centerY = y(p.lat); invalidate(); }
    public void marker(GeoPoint p) { marker = p; invalidate(); }
    public void route(List<GeoPoint> points) { route = new ArrayList<>(points); invalidate(); }
    public void fit(List<GeoPoint> points) {
        if (points.isEmpty()) return;
        double minX=Double.POSITIVE_INFINITY, maxX=Double.NEGATIVE_INFINITY, minY=1, maxY=0, anchor=x(points.get(0).lon);
        for (GeoPoint p : points) { double xx=x(p.lon);if(xx-anchor>.5)xx--;if(xx-anchor<-.5)xx++;minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minY=Math.min(minY,y(p.lat));maxY=Math.max(maxY,y(p.lat)); }
        centerX=(((minX+maxX)/2)%1+1)%1; centerY=(minY+maxY)/2;
        double width = Math.max(180, getWidth()-80), height = Math.max(120, getHeight()-80);
        zoom=16; while (zoom>2 && ((maxX-minX)*256*(1<<zoom)>width || (maxY-minY)*256*(1<<zoom)>height)) zoom--;
        invalidate();
    }
    public void zoom(int delta) { zoom = Math.max(2, Math.min(18, zoom + delta)); invalidate(); }
    private static double x(double lon) { return (lon+180)/360; }
    private static double y(double lat) { double v=Math.toRadians(Math.max(-85.0511,Math.min(85.0511,lat))); return (1-Math.log(Math.tan(v)+1/Math.cos(v))/Math.PI)/2; }
    private float screenX(GeoPoint p) { double dx = x(p.lon)-centerX; if (dx>.5) dx--; if (dx<-.5) dx++; return (float)(getWidth()/2.0+dx*256*(1<<zoom)); }
    private float screenY(GeoPoint p) { return (float)(getHeight()/2.0+(y(p.lat)-centerY)*256*(1<<zoom)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.rgb(22,36,43));
        int tiles = 1 << zoom; double world = 256.0*tiles, left=centerX*world-getWidth()/2.0, top=centerY*world-getHeight()/2.0;
        for (int tx=(int)Math.floor(left/256); tx<=(int)Math.floor((left+getWidth())/256); tx++) for (int ty=(int)Math.floor(top/256); ty<=(int)Math.floor((top+getHeight())/256); ty++) {
            if (ty<0 || ty>=tiles) continue; int wrapX=((tx%tiles)+tiles)%tiles; String key=zoom+"_"+wrapX+"_"+ty;
            float px=(float)(tx*256-left), py=(float)(ty*256-top); Bitmap b=cache.get(key);
            if (b != null) { paint.setAlpha(190); tileRect.set(px,py,px+256,py+256); canvas.drawBitmap(b,null,tileRect,paint); paint.setAlpha(255); }
            else { paint.setColor(Color.rgb(37,55,61)); paint.setStrokeWidth(1); canvas.drawLine(px,py,px+256,py,paint); canvas.drawLine(px,py,px,py+256,paint); load(key,zoom,wrapX,ty); }
        }
        if (!route.isEmpty()) {
            Path path=routePath; path.reset(); boolean first=true; float prevX=0;
            for (GeoPoint p:route) { float px=screenX(p), py=screenY(p); if (first || Math.abs(px-prevX)>world/2) path.moveTo(px,py); else path.lineTo(px,py); first=false; prevX=px; }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Color.rgb(12,35,32)); paint.setStrokeWidth(10); canvas.drawPath(path,paint);
            paint.setColor(Color.rgb(40,190,133)); paint.setStrokeWidth(5); canvas.drawPath(path,paint); paint.setStyle(Paint.Style.FILL);
            drawDot(canvas,route.get(0),Color.WHITE,7); drawDot(canvas,route.get(route.size()-1),Color.rgb(128,243,194),8);
        }
        if (marker!=null) {
            float px=screenX(marker), py=screenY(marker); paint.setColor(0x4080F3C2); canvas.drawCircle(px,py,24,paint);
            paint.setColor(Color.rgb(12,20,27)); canvas.drawCircle(px,py,11,paint); paint.setColor(Color.rgb(128,243,194)); canvas.drawCircle(px,py,7,paint);
        }
        paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity); paint.setColor(0xD00C141B); canvas.drawRect(0,getHeight()-27*getResources().getDisplayMetrics().density,getWidth(),getHeight(),paint);
        paint.setColor(Color.WHITE); canvas.drawText("© OpenStreetMap contributors  ·  mappa online",10,getHeight()-10,paint);
    }
    private void drawDot(Canvas c, GeoPoint p,int color,int radius) { paint.setColor(0xFF0C141B); c.drawCircle(screenX(p),screenY(p),radius+3,paint); paint.setColor(color); c.drawCircle(screenX(p),screenY(p),radius,paint); }
    private void load(String key,int z,int tx,int ty) {
        if (!alive || pending.contains(key) || (failures.containsKey(key) && System.currentTimeMillis()-failures.get(key)<60000)) return;
        pending.add(key);
        loader.execute(() -> {
            Bitmap bitmap = null; HttpsURLConnection connection=null;
            try {
                File file = new File(tileDir,key+".png");
                if (file.exists() && System.currentTimeMillis()-file.lastModified()<7L*24*60*60*1000) bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
                else {
                    connection=Net.open(new URL("https://tile.openstreetmap.org/"+z+"/"+tx+"/"+ty+".png"));
                    if (file.exists()) connection.setIfModifiedSince(file.lastModified());
                    int status=connection.getResponseCode();
                    if (status==304) { file.setLastModified(System.currentTimeMillis()); bitmap=BitmapFactory.decodeFile(file.getAbsolutePath()); }
                    else if (status==200) {
                        byte[] data;
                        try (InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                            byte[] buffer=new byte[4096]; int n; while ((n=in.read(buffer))!=-1) { if (out.size()+n>1024*1024) throw new IOException("Tile troppo grande"); out.write(buffer,0,n); } data=out.toByteArray();
                        }
                        bitmap=BitmapFactory.decodeByteArray(data,0,data.length);
                        if (bitmap!=null) { trimCache(); try(FileOutputStream out=new FileOutputStream(file)) { out.write(data); } }
                    }
                }
            } catch(Exception ignored) { /* Offline route drawing remains usable without tiles. */ }
            finally { if(connection!=null) connection.disconnect(); }
            Bitmap result=bitmap;
            post(() -> { if (alive && result!=null) { cache.put(key,result); invalidate(); } else failures.put(key,System.currentTimeMillis()); pending.remove(key); });
        });
    }
    private void trimCache() {
        File[] files=tileDir.listFiles(); if(files==null || files.length<500) return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        for(int i=0;i<Math.min(50,files.length);i++) files[i].delete();
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        pinch.onTouchEvent(e);
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: getParent().requestDisallowInterceptTouchEvent(true); lastX=startX=e.getX();lastY=startY=e.getY();moved=false;return true;
            case MotionEvent.ACTION_POINTER_DOWN: moved=true;return true;
            case MotionEvent.ACTION_MOVE:
                if(e.getPointerCount()==1 && !pinch.isInProgress()) {
                    double world=256.0*(1<<zoom);centerX-=(e.getX()-lastX)/world;centerY-=(e.getY()-lastY)/world;
                    centerX=((centerX%1)+1)%1;centerY=Math.max(0,Math.min(1,centerY));
                    if(Math.hypot(e.getX()-startX,e.getY()-startY)>12) moved=true;
                    lastX=e.getX();lastY=e.getY();invalidate();
                } return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if(!moved && onPick!=null) {
                    if(e.getY()>getHeight()-30*getResources().getDisplayMetrics().density) {
                        try { getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright"))); }
                        catch(android.content.ActivityNotFoundException ignored) { }
                    } else {
                        double world=256.0*(1<<zoom), xx=centerX+(e.getX()-getWidth()/2.0)/world, yy=centerY+(e.getY()-getHeight()/2.0)/world;
                        double lon=(((xx*360-180)+540)%360)-180, lat=Math.toDegrees(Math.atan(Math.sinh(Math.PI*(1-2*yy))));
                        onPick.pick(new GeoPoint(lat,lon));
                    }
                }
                performClick();return true;
            case MotionEvent.ACTION_CANCEL: getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDetachedFromWindow() { alive=false;loader.shutdownNow();cache.evictAll();super.onDetachedFromWindow(); }
}
