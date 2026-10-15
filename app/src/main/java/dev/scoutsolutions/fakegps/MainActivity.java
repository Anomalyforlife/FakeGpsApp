package dev.scoutsolutions.fakegps;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BG=0xFF0C141B,CARD=0xFF15222C,EDGE=0xFF2C3D49,TEXT=0xFFF0F6F5,MUTED=0xFF99ACB7,MINT=0xFF80F3C2,RED=0xFFFF9A9A;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private LinearLayout root,content,sessionCard;
    private TextView status,details,speedLabel,rangeLabel,liveSpeedLabel,pointLabel;
    private Spinner endChoice;
    private final Map<String,Button> profileButtons=new LinkedHashMap<>();
    private PlaybackSettings playback=PlaybackSettings.preset(PlaybackSettings.WALK);
    private boolean updatingPlaybackUi;
    private Button pauseButton,stopButton;
    private EditText latInput,lonInput,altInput,sharedInput,searchInput;
    private MapView map;
    private SharedPreferences prefs;
    private Track selected,exportTrack;
    private GeoPoint pin=new GeoPoint(45.4642,9.19);
    private int tab=0;
    private String sharedText="", lastError="", lastFinishedId="";
    private boolean requestingStart,busy;
    private AlertDialog busyDialog;
    private Runnable afterPermission;
    private final Runnable pulse=new Runnable() { @Override public void run() { updateSession();ui.postDelayed(this,750); } };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); prefs=getSharedPreferences("ui",MODE_PRIVATE);
        sharedText=prefs.getString("link","");tab=saved==null?0:saved.getInt("tab",0);
        try { pin=new GeoPoint(Double.parseDouble(prefs.getString("lat","45.4642")),Double.parseDouble(prefs.getString("lon","9.1900")),Double.parseDouble(prefs.getString("alt","0")),0); }catch(Exception ignored) { }
        String id=prefs.getString("track","");if(!id.isEmpty())try{selected=new TrackStore(this).load(id);}catch(Exception ignored){ }
        loadSelectedPlayback();
        if(saved!=null){String exporting=saved.getString("exportTrack","");if(!exporting.isEmpty())try{exportTrack=new TrackStore(this).load(exporting);}catch(Exception ignored){ }}
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        render();
        if(saved==null) handleShare(getIntent());
        if(!prefs.getBoolean("onboarded",false)) { prefs.edit().putBoolean("onboarded",true).apply();ui.post(this::help); }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent);setIntent(intent);handleShare(intent); }
    @Override protected void onSaveInstanceState(Bundle out) { remember();out.putInt("tab",tab);out.putString("exportTrack",exportTrack==null?"":exportTrack.id);super.onSaveInstanceState(out); }
    @Override protected void onResume() { super.onResume();ui.removeCallbacks(pulse);ui.post(pulse); }
    @Override protected void onPause() { remember();ui.removeCallbacks(pulse);super.onPause(); }
    @Override protected void onDestroy() { if(busyDialog!=null&&busyDialog.isShowing())busyDialog.dismiss();jobs.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy(); }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    private TextView text(String value,int size,int color,boolean bold) { TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t; }
    private void gap(LinearLayout parent,int height) { View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    private void add(LinearLayout parent,View v) { parent.addView(v,new LinearLayout.LayoutParams(-1,-2)); }
    private LinearLayout card(LinearLayout parent) { LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(shape(CARD,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);parent.addView(c,lp);return c; }
    private Button button(String title,boolean primary,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?BG:TEXT);b.setBackground(shape(primary?MINT:EDGE,14));b.setMinHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setOnClickListener(v->action.run());return b;
    }
    private void pair(LinearLayout parent,Button a,Button b) { LinearLayout r=row();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(0,0,dp(5),0);r.addView(a,p);LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(0,dp(48),1);q.leftMargin=dp(5);r.addView(b,q);add(parent,r); }
    private EditText input(String hint,String value,boolean numeric) {
        EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(15);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(shape(BG,12));e.setMinHeight(dp(50));
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);else e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }
    private void label(LinearLayout parent,String value) { TextView t=text(value,12,MUTED,true);t.setLetterSpacing(.08f);add(parent,t);gap(parent,9); }
    private void remember() {
        if(prefs==null)return;
        if(sharedInput!=null)sharedText=sharedInput.getText().toString();
        if(latInput!=null&&lonInput!=null)try{pin=readPin();}catch(Exception ignored){ }
        prefs.edit().putString("lat",Double.toString(pin.lat)).putString("lon",Double.toString(pin.lon)).putString("alt",Double.toString(pin.altitude)).putString("link",sharedText).putString("track",selected==null?"":selected.id).apply();
    }
    private void render() {
        latInput=lonInput=altInput=sharedInput=searchInput=null;map=null;pointLabel=speedLabel=rangeLabel=liveSpeedLabel=null;endChoice=null;profileButtons.clear();pauseButton=stopButton=null;
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(10),dp(18),0);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{ root.setPadding(dp(18),dp(10)+insets.getSystemWindowInsetTop(),dp(18),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets(); });
        LinearLayout heading=row();ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_anomaly);heading.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout brand=column();add(brand,text("ANOMALY",21,TEXT,true));TextView sub=text("GPS on your terms.",11,MUTED,false);add(brand,sub);heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button guide=button("Help",false,this::help);heading.addView(guide,new LinearLayout.LayoutParams(dp(74),dp(48)));add(root,heading);gap(root,16);
        LinearLayout tabs=row();String[] names={"Location","Route","Record","Library"};
        for(int i=0;i<names.length;i++){final int target=i;Button b=button(names[i],i==tab,()->{remember();tab=target;render();});b.setTextSize(12);b.setPadding(0,0,0,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(2),0,dp(2),0);tabs.addView(b,p);}add(root,tabs);gap(root,14);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=column();scroll.addView(content);
        sessionCard=card(content);LinearLayout statusRow=row();TextView dot=text("●  ",13,MINT,true);statusRow.addView(dot);status=text("Ready to go",15,TEXT,true);statusRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));add(sessionCard,statusRow);gap(sessionCard,6);details=text("Local sessions · no account required",12,MUTED,false);add(sessionCard,details);
        pauseButton=button("Pause",false,()->control(GpsService.PAUSE));stopButton=button("End session",false,()->control(GpsService.STOP));stopButton.setTextColor(RED);pair(sessionCard,pauseButton,stopButton); // hidden while idle
        gap(sessionCard,8);add(sessionCard,button("Location diagnostics",false,this::diagnostics));
        if(tab!=3)buildMap();
        if(tab==0)positionScreen();else if(tab==1)routeScreen();else if(tab==2)recordScreen();else libraryScreen();
        TextView foot=text("ANOMALY GPS  /  1.3   ·   Your routes stay on your device",10,MUTED,false);foot.setGravity(Gravity.CENTER);add(content,foot);gap(content,20);updateSession();
    }
    private void buildMap() {
        LinearLayout container=column();container.setBackground(shape(CARD,20));container.setClipToOutline(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(260));lp.bottomMargin=dp(14);content.addView(container,lp);
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);container.addView(frame,new LinearLayout.LayoutParams(-1,-1));map=new MapView(this);frame.addView(map,new android.widget.FrameLayout.LayoutParams(-1,-1));
        if(selected!=null&&tab==1){map.route(selected.points);map.post(()->{if(map!=null)map.fit(selected.points);});map.marker(selected.points.get(0));}else{map.center(pin);map.marker(pin);}
        map.onPick=p->{if(tab==0&&!GpsService.state.active()){pin=p;if(latInput!=null){latInput.setText(number(p.lat));lonInput.setText(number(p.lon));}map.marker(p);if(pointLabel!=null)pointLabel.setText(coords(p));remember();}};
        LinearLayout zoom=column();Button plus=button("+",false,()->map.zoom(1)),minus=button("−",false,()->map.zoom(-1));plus.setContentDescription("Zoom in");minus.setContentDescription("Zoom out");zoom.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48)));gap(zoom,5);zoom.addView(minus,new LinearLayout.LayoutParams(dp(48),dp(48)));android.widget.FrameLayout.LayoutParams z=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.TOP);z.setMargins(0,dp(10),dp(10),0);frame.addView(zoom,z);
    }
    private void positionScreen() {
        LinearLayout c=card(content);label(c,"01 / FIXED LOCATION");add(c,text("Choose where to be.",24,TEXT,true));gap(c,6);add(c,text("Tap the map, search for a place or enter coordinates.",13,MUTED,false));gap(c,16);
        searchInput=input("City, address or latitude,longitude","",false);searchInput.setSingleLine(true);add(c,searchInput);gap(c,9);add(c,button("Search the map",false,()->{String query=searchInput.getText().toString().trim();if(query.isEmpty()){toast("Enter a place or coordinates.");return;}new AlertDialog.Builder(this).setTitle("Search for this place online?").setMessage("Your search is sent to the device's geocoding service. Coordinates are displayed on the OpenStreetMap map.").setPositiveButton("Search",(d,w)->work("Searching for a place…",()->new RouteImporter(this).locate(query),p->{pin=p;latInput.setText(number(p.lat));lonInput.setText(number(p.lon));map.center(p);map.marker(p);remember();})).setNegativeButton("Cancel",null).show();}));gap(c,16);
        label(c,"LATITUDE");latInput=input("45.4642",number(pin.lat),true);add(c,latInput);gap(c,12);label(c,"LONGITUDE");lonInput=input("9.1900",number(pin.lon),true);add(c,lonInput);gap(c,12);label(c,"ALTITUDE · METERS");altInput=input("0",Double.toString(pin.altitude),true);add(c,altInput);gap(c,16);
        pointLabel=text(coords(pin),12,MINT,true);pointLabel.setTypeface(Typeface.MONOSPACE);add(c,pointLabel);gap(c,14);
        add(c,button("Activate fixed location",true,()->{try{pin=readPin();map.marker(pin);map.center(pin);remember();Intent i=new Intent(this,GpsService.class).setAction(GpsService.STATIC).putExtra("lat",pin.lat).putExtra("lon",pin.lon).putExtra("alt",pin.altitude);begin(i);}catch(Exception e){error(e.getMessage());}}));
    }
    private GeoPoint readPin() { return new GeoPoint(parse(latInput),parse(lonInput),parse(altInput),0); }
    private double parse(EditText e) { if(e==null)return pin.altitude;return Double.parseDouble(e.getText().toString().trim().replace(',','.')); }
    private void loadSelectedPlayback() {
        if(selected!=null&&selected.playback!=null){playback=selected.playback;return;}
        try{
            int oldSpeed=prefs.getInt("speed",5),oldEnd=prefs.getInt("end",0);
            playback=PlaybackSettings.custom(oldSpeed,new String[]{"hold","stop","loop"}[Math.max(0,Math.min(2,oldEnd))]);
        }catch(Exception invalidLegacy){playback=PlaybackSettings.preset(PlaybackSettings.WALK);}
    }
    private Button profileButton(String profile,String title) {
        Button b=button(title,false,()->{
            if(PlaybackSettings.CUSTOM.equals(profile))speedDialog(true);
            else if(!profile.equals(playback.profile))savePlayback(PlaybackSettings.preset(profile).withEnd(playback.end),true,null);
        });
        profileButtons.put(profile,b);return b;
    }
    private void refreshPlaybackControls() {
        if(speedLabel==null)return;
        speedLabel.setText(String.format(Locale.ENGLISH,"%s · %.1f km/h",playback.label(),playback.baseKmh));
        rangeLabel.setText(String.format(Locale.ENGLISH,"Steady-state range: %.1f – %.1f km/h",playback.minKmh,playback.maxKmh));
        for(Map.Entry<String,Button> entry:profileButtons.entrySet()){
            boolean checked=entry.getKey().equals(playback.profile);entry.getValue().setBackground(shape(checked?MINT:EDGE,14));entry.getValue().setTextColor(checked?BG:TEXT);
        }
        if(endChoice!=null){updatingPlaybackUi=true;endChoice.setSelection("stop".equals(playback.end)?1:"loop".equals(playback.end)?2:0);updatingPlaybackUi=false;}
    }
    private void speedDialog(boolean custom) {
        LinearLayout body=column();body.setPadding(dp(20),dp(12),dp(20),dp(12));
        if(custom)label(body,"REFERENCE SPEED · KM/H");else add(body,text(String.format(Locale.ENGLISH,"%s · reference %.1f km/h",playback.label(),playback.baseKmh),15,TEXT,true));
        EditText base=input("E.g. 5.0",speedNumber(playback.baseKmh),true);if(custom)add(body,base);gap(body,12);
        label(body,"MINIMUM · KM/H");EditText min=input("E.g. 4.2",speedNumber(playback.minKmh),true);add(body,min);gap(body,12);
        label(body,"MAXIMUM · KM/H");EditText max=input("E.g. 5.8",speedNumber(playback.maxKmh),true);add(body,max);gap(body,12);
        if(custom){add(body,button("Calculate ±10% range",false,()->{try{double v=parse(base);PlaybackSettings defaults=PlaybackSettings.custom(v,playback.end);min.setText(speedNumber(defaults.minKmh));max.setText(speedNumber(defaults.maxKmh));}catch(Exception e){base.setError("Enter a speed between 0 and 300.");}}));gap(body,10);}
        TextView validation=text("The reference speed must be within the range. Set all values to zero to stay still.",12,MUTED,false);add(body,validation);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(custom?"Custom speed":"Speed variation range").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{PlaybackSettings next=new PlaybackSettings(custom?PlaybackSettings.CUSTOM:playback.profile,custom?parse(base):playback.baseKmh,parse(min),parse(max),playback.end);dialog.dismiss();savePlayback(next,true,null);}
            catch(Exception e){validation.setText(e instanceof NumberFormatException?"Enter valid numbers; either a decimal point or a comma is accepted.":e.getMessage());validation.setTextColor(RED);}
        }));dialog.show();
    }
    private void savePlayback(PlaybackSettings next,boolean applyToActive,Runnable after) {
        if(selected==null)return;String id=selected.id;
        if(GpsService.RECORD.equals(GpsService.state.mode)&&id.equals(GpsService.state.trackId)){toast("Stop recording before changing settings.");refreshPlaybackControls();return;}
        refreshPlaybackControls();
        work("Saving route settings…",()->new TrackStore(this).updatePlayback(id,next),saved->{
            if(selected!=null&&selected.id.equals(id)){selected=saved;playback=next;refreshPlaybackControls();remember();}
            if(applyToActive&&GpsService.ROUTE.equals(GpsService.state.mode)&&id.equals(GpsService.state.trackId))startService(new Intent(this,GpsService.class).setAction(GpsService.SPEED).putExtra("id",id));
            if(after!=null)after.run();
        });
    }
    private void startPlayback() {
        if(selected==null)return;
        if(GpsService.state.active()||requestingStart){toast("End the active session first.");return;}
        String id=selected.id;remember();savePlayback(playback,false,()->begin(new Intent(this,GpsService.class).setAction(GpsService.ROUTE).putExtra("id",id)));
    }
    private void routeScreen() {
        LinearLayout c=card(content);label(c,"02 / SIMULATE A ROUTE");add(c,text("Move your way.",24,TEXT,true));gap(c,6);add(c,text("Share a Google Maps route with Anomaly GPS or paste the link here.",13,MUTED,false));gap(c,14);
        sharedInput=input("Google Maps link or two stops, one per line",sharedText,false);sharedInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);sharedInput.setMinLines(3);sharedInput.setMaxLines(6);add(c,sharedInput);gap(c,10);
        pair(c,button("Import from Maps",false,this::importLink),button("Open GPX",false,this::pickGpx));gap(c,12);
        add(c,text("Maps → choose Driving or Walking before recalculating stops. To follow an exact track, use GPX or a recording.",12,MUTED,false));
        LinearLayout playback=card(content);label(playback,"SELECTED ROUTE");
        if(selected==null){add(playback,text("Your next journey starts here.",19,TEXT,true));gap(playback,6);add(playback,text("Import a route or select one from your library.",13,MUTED,false));return;}
        add(playback,text(selected.name,20,TEXT,true));gap(playback,6);add(playback,text(String.format(Locale.ENGLISH,"%.2f km  ·  %,d points\n%s",selected.distance()/1000,selected.points.size(),selected.source),13,MUTED,false));gap(playback,18);
        label(playback,"SIMULATION SPEED");speedLabel=text("",28,MINT,true);add(playback,speedLabel);rangeLabel=text("",13,MUTED,false);add(playback,rangeLabel);gap(playback,6);
        liveSpeedLabel=text("",15,TEXT,true);add(playback,liveSpeedLabel);gap(playback,14);
        pair(playback,profileButton(PlaybackSettings.WALK,"Walking · 5 km/h"),profileButton(PlaybackSettings.BIKE,"Cycling · 18 km/h"));gap(playback,8);
        pair(playback,profileButton(PlaybackSettings.MOTO,"Motorcycle · 50 km/h"),profileButton(PlaybackSettings.CAR,"Driving · 70 km/h"));gap(playback,8);
        add(playback,profileButton(PlaybackSettings.CUSTOM,"Custom…"));gap(playback,10);
        add(playback,button("Edit speed variation range",false,()->speedDialog(false)));gap(playback,8);
        add(playback,text("Speed changes gradually within the range. Starting, resuming and switching profiles are gradual: speed may fall outside the range during these transitions. Pausing and arriving set speed to zero.",12,MUTED,false));gap(playback,10);
        add(playback,text("Profiles only set speed. Life360 and other apps may use the phone's real sensors, so the displayed activity may differ. Motorcycles and cars may both be classified as vehicles.",12,MUTED,false));gap(playback,17);
        label(playback,"AT THE END OF THE ROUTE");endChoice=new Spinner(this);String[] options={"Hold at the destination","Stop and restore real GPS","Repeat from the start (jump to starting point)"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);endChoice.setAdapter(adapter);endChoice.setMinimumHeight(dp(48));add(playback,endChoice);refreshPlaybackControls();
        endChoice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){String value=new String[]{"hold","stop","loop"}[index];if(!updatingPlaybackUi&&!value.equals(MainActivity.this.playback.end))savePlayback(MainActivity.this.playback.withEnd(value),false,null);}});
        gap(playback,8);add(playback,text("Profile, speed, range and arrival behavior are saved for this route. Speed and range can change during simulation; arrival behavior applies the next time you start.",12,MUTED,false));gap(playback,16);
        add(playback,button("Start simulation",true,this::startPlayback));gap(playback,10);pair(playback,button("Open in Maps",false,()->openMaps(selected)),button("Export GPX",false,()->export(selected)));
    }
    private void recordScreen() {
        LinearLayout c=card(content);label(c,"03 / RECORD THE REAL WORLD");add(c,text("A route of your own.",24,TEXT,true));gap(c,8);add(c,text("Record your real journey and replay it whenever you want, at your chosen speed.",14,MUTED,false));gap(c,20);
        add(c,text("●  Real GPS, updated every second\n●  Local saving during your trip\n●  Keeps running with the screen off\n●  GPX export from your library",14,TEXT,false));gap(c,16);add(c,text("Recording ignores mock locations and fixes with accuracy worse than 50 m. Turn off other fake GPS apps. At least two distinct points are required to record a track.",12,MUTED,false));gap(c,18);
        add(c,button("Start recording",true,()->{if(GpsService.state.active()||requestingStart){toast("End the active session first.");return;}askName("Recording name","Trip "+new SimpleDateFormat("dd MMM · HH:mm",Locale.ENGLISH).format(new Date()),name->begin(new Intent(this,GpsService.class).setAction(GpsService.RECORD).putExtra("name",name)));}));
        gap(c,12);add(c,text("Stop from the tab or notification to save the track to your library. Sessions last up to 12 hours.",12,MUTED,false));
    }
    private void libraryScreen() {
        LinearLayout title=card(content);label(title,"04 / YOUR LIBRARY");add(title,text("Routes to revisit.",24,TEXT,true));gap(title,8);add(title,text("Imported and recorded routes, stored only on your phone.",13,MUTED,false));gap(title,12);add(title,button("Import a GPX track",false,this::pickGpx));
        try {
            List<Track> tracks=new TrackStore(this).list();
            if(tracks.isEmpty()){LinearLayout empty=card(content);add(empty,text("No routes yet.",20,TEXT,true));gap(empty,8);add(empty,text("Record a walk, import a GPX or share a route from Google Maps.",14,MUTED,false));return;}
            for(Track t:tracks){LinearLayout c=card(content);add(c,text(t.name,18,TEXT,true));gap(c,7);add(c,text(String.format(Locale.ENGLISH,"%.2f km  ·  %,d points  ·  %s\n%s%s",t.distance()/1000,t.points.size(),new SimpleDateFormat("dd MMM yyyy",Locale.ENGLISH).format(new Date(t.created)),t.source,t.playback==null?"":String.format(Locale.ENGLISH,"\n%s · %.1f km/h · range %.1f–%.1f",t.playback.label(),t.playback.baseKmh,t.playback.minKmh,t.playback.maxKmh)),12,MUTED,false));gap(c,14);pair(c,button("Simulate",true,()->select(t)),button("Options",false,()->trackOptions(t)));}
        }catch(Exception e){error("Unable to read the library: "+e.getMessage());}
    }
    private void trackOptions(Track t) {
        new AlertDialog.Builder(this).setTitle(t.name).setItems(new String[]{"Export GPX","Rename","Open endpoints in Google Maps","Delete"},(d,which)->{
            if(which==0)export(t);else if(which==1){if(GpsService.RECORD.equals(GpsService.state.mode)&&t.id.equals(GpsService.state.trackId)){toast("Stop recording before renaming it.");return;}askName("Rename route",t.name,name->work("Renaming route…",()->new TrackStore(this).rename(t.id,name),renamed->{if(selected!=null&&selected.id.equals(t.id)){selected=renamed;loadSelectedPlayback();}render();}));}
            else if(which==2)openMaps(t);else{
                if(GpsService.state.active()&&t.id.equals(GpsService.state.trackId)){toast("End the session before deleting the route.");return;}
                new AlertDialog.Builder(this).setTitle("Delete this route?").setMessage(t.name).setNegativeButton("Cancel",null).setPositiveButton("Delete",(dd,w)->{new TrackStore(this).delete(t.id);if(selected!=null&&selected.id.equals(t.id))selected=null;remember();render();}).show();
            }
        }).show();
    }
    private void select(Track t) { remember();selected=t;loadSelectedPlayback();tab=1;remember();render(); }
    private void importLink() {
        remember();if(sharedText.trim().isEmpty()){toast("Paste a Google Maps link or at least two stops.");return;}
        String source=sharedText;
        LinearLayout body=column();body.setPadding(dp(20),dp(12),dp(20),dp(12));
        add(body,text("How would you like to recalculate the route?",16,TEXT,true));gap(body,8);
        RadioGroup choices=new RadioGroup(this);choices.setOrientation(RadioGroup.VERTICAL);
        RoutingProfile initial=RoutingProfile.fromPreference(prefs.getString("routing_mode","car"));
        int carId=View.generateViewId(),footId=View.generateViewId();
        RadioButton car=new RadioButton(this);car.setId(carId);car.setText(R.string.routing_car);car.setTextColor(TEXT);car.setMinHeight(dp(48));choices.addView(car);
        RadioButton foot=new RadioButton(this);foot.setId(footId);foot.setText(R.string.routing_foot);foot.setTextColor(TEXT);foot.setMinHeight(dp(48));choices.addView(foot);
        choices.check(initial==RoutingProfile.FOOT?footId:carId);add(body,choices);gap(body,12);
        add(body,text("This choice changes the route. Simulation speed is set separately after importing.",13,MUTED,false));gap(body,12);
        add(body,text("Stops will be geocoded and sent to the public OSRM service. The result may differ from Google Maps and does not include traffic or alternatives. Use GPX to preserve an exact track.",12,MUTED,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Import from Maps").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Import",(d,w)->{
            RoutingProfile mode=choices.getCheckedRadioButtonId()==footId?RoutingProfile.FOOT:RoutingProfile.CAR;
            askName("Route name","Imported route",name->{
                prefs.edit().putString("routing_mode",mode.id).apply();
                work("Calculating route · "+mode.label+"…",()->{Track t=new RouteImporter(this).importText(source,name,mode);new TrackStore(this).save(t);return t;},this::select);
            });
        }).show();
    }
    private void pickGpx() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,51); }
    private void export(Track t) { exportTrack=t;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/gpx+xml").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,t.name.replaceAll("[^\\p{L}\\p{N} _-]","_")+".gpx");startActivityForResult(i,52); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==51)importGpx(uri);else if(request==52&&exportTrack!=null){Track t=exportTrack;work("Exporting GPX…",()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Destination unavailable.");Gpx.write(t,out);}return t;},ignored->toast("GPX exported."));}
    }
    private void importGpx(Uri uri) { work("Reading GPX track…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("File unavailable.");Track t=Gpx.read(in,"GPX track");new TrackStore(this).save(t);return t;}},this::select); }
    @SuppressWarnings("deprecation")
    private void handleShare(Intent intent) {
        if(!Intent.ACTION_SEND.equals(intent.getAction()))return;
        if("text/plain".equals(intent.getType())){remember();sharedText=intent.getStringExtra(Intent.EXTRA_TEXT);if(sharedText==null)sharedText="";if(sharedText.length()>16000)sharedText=sharedText.substring(0,16000);tab=1;render();toast("Link received. Tap Import from Maps.");}
        else{Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null&&"content".equals(uri.getScheme()))importGpx(uri);}
    }
    private void openMaps(Track t) {
        if(t.points.size()<2)return;GeoPoint a=t.points.get(0),b=t.points.get(t.points.size()-1);
        new AlertDialog.Builder(this).setTitle("Open Google Maps").setMessage("Google Maps will receive the start and destination and calculate its own route. The GPX track is not transferred.").setNegativeButton("Cancel",null).setPositiveButton("Open",(d,w)->{
            Uri url=Uri.parse("https://www.google.com/maps/dir/").buildUpon().appendQueryParameter("api","1").appendQueryParameter("origin",a.lat+","+a.lon).appendQueryParameter("destination",b.lat+","+b.lon).appendQueryParameter("travelmode",t.routingProfile==RoutingProfile.FOOT?"walking":"driving").build();
            try{startActivity(new Intent(Intent.ACTION_VIEW,url));}catch(ActivityNotFoundException e){error("No app available to open the link.");}
        }).show();
    }
    private void begin(Intent intent) {
        if(GpsService.state.active()||requestingStart){toast("End the active session first.");return;}
        if(!GpsService.RECORD.equals(intent.getAction())) {
            AppOpsManager ops=(AppOpsManager)getSystemService(APP_OPS_SERVICE);
            if(ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,android.os.Process.myUid(),getPackageName())!=AppOpsManager.MODE_ALLOWED) {
                new AlertDialog.Builder(this).setTitle("Select the mock location app").setMessage("In Developer options → Select mock location app, choose Anomaly GPS, then return here and start the simulation.").setPositiveButton("Open settings",(d,w)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}).setNegativeButton("Cancel",null).show();return;
            }
        }
        if(!((LocationManager)getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)) {new AlertDialog.Builder(this).setTitle("Enable GPS location").setMessage("Android requires location to be enabled for the GPS session.").setPositiveButton("Settings",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Cancel",null).show();return;}
        Runnable launch=()->{
            try{requestingStart=true;startForegroundService(intent);ui.postDelayed(()->{requestingStart=false;updateSession();},1500);}
            catch(Exception e){requestingStart=false;error("Unable to start the service: "+e.getMessage());}
        };
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){afterPermission=launch;List<String> permissions=new ArrayList<>(Arrays.asList(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION));if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.POST_NOTIFICATIONS);requestPermissions(permissions.toArray(new String[0]),61);}
        else if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){afterPermission=launch;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},61);}
        else launch.run();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==61){Runnable action=afterPermission;afterPermission=null;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){if(action!=null)action.run();}else new AlertDialog.Builder(this).setTitle("Precise location required").setMessage("Allow precise location while using the app. The service keeps the session running in the background until you tap Stop.").setPositiveButton("App settings",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Close",null).show();}
    }
    private void control(String action) { if(GpsService.state.active())startService(new Intent(this,GpsService.class).setAction(action)); }
    private void updateSession() {
        if(status==null)return;GpsService.State s=GpsService.state;status.setText(requestingStart?"Starting session…":s.message);status.setTextColor(s.error.isEmpty()?TEXT:RED);
        if(s.active())details.setText(String.format(Locale.ENGLISH,"%s  ·  %.2f km  ·  %s%s%s\n%s%s",GpsService.RECORD.equals(s.mode)?"REC · real GPS":GpsService.STATIC.equals(s.mode)?"Static GPS":"Simulated GPS",s.distance/1000,duration(s.elapsed),GpsService.ROUTE.equals(s.mode)?String.format(Locale.ENGLISH," · %.1f km/h",s.speed):"",s.position==null?"":"\n"+coords(s.position),s.backend,s.warning.isEmpty()?"":"\n"+s.warning));
        else details.setText(s.error.isEmpty()?"Local sessions · no account required":s.error);
        View controls=(View)pauseButton.getParent();controls.setVisibility(s.active()?View.VISIBLE:View.GONE);pauseButton.setVisibility(GpsService.ROUTE.equals(s.mode)&&!s.holding?View.VISIBLE:View.GONE);pauseButton.setText(s.paused?"Resume":"Pause");stopButton.setEnabled(!s.saving);
        if(map!=null&&s.active()&&s.position!=null)map.marker(s.position);
        if(liveSpeedLabel!=null)liveSpeedLabel.setText(GpsService.ROUTE.equals(s.mode)&&selected!=null&&selected.id.equals(s.trackId)?String.format(Locale.ENGLISH,"Current speed: %.1f km/h",s.speed):"Current speed will appear during simulation.");
        if(!s.error.isEmpty()&&!s.error.equals(lastError)){lastError=s.error;error(s.error);}
        if(!s.active()&&!s.trackId.isEmpty()&&!s.trackId.equals(lastFinishedId)){lastFinishedId=s.trackId;if(tab==3)ui.post(()->{if(!isDestroyed())render();});}
    }
    private static String duration(long millis) { long seconds=millis/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60); }
    private static String number(double value) { return String.format(Locale.ROOT,"%.6f",value); }
    private static String speedNumber(double value) { return Double.toString(value).replace('.', ','); }
    private static String coords(GeoPoint p) { return number(p.lat)+"  /  "+number(p.lon); }
    private void diagnostics() {
        GpsService.State s=GpsService.state;
        String sent=s.lastSentNanos==0?"No confirmed mock location delivery":String.format(Locale.ENGLISH,"Last confirmed delivery: %.1f seconds ago",Math.max(0,SystemClock.elapsedRealtimeNanos()-s.lastSentNanos)/1e9);
        String message=Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n\n"+s.message+"\n"+s.backend+"\n"+sent+(s.position==null?"":"\n"+coords(s.position))+(s.warning.isEmpty()?"":"\n"+s.warning)+(s.error.isEmpty()?"":"\n"+s.error)+"\n\nConfirmed delivery means the location services accepted the point. It does not confirm what other apps display.\n\nIf Maps is stable but Life360 jumps around, note whether it returns to a previous or real location, and whether the Anomaly GPS notification remains visible. Sensors, network and Life360 updates need separate checks.";
        new AlertDialog.Builder(this).setTitle("Location diagnostics · 1.3").setMessage(message).setPositiveButton("Copy details",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Anomaly GPS diagnostics",message));toast("Details copied.");}).setNegativeButton("Close",null).show();
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("Getting started with Anomaly GPS").setMessage("1. In Android, open About phone and tap Build number 7 times to enable Developer options.\n\n2. In Developer options → Select mock location app, select Anomaly GPS. Root is not required.\n\n3. Enable location and allow precise location when prompted.\n\nLocation: choose a point and activate static GPS.\nRoute: share from Maps and choose Driving or Walking to recalculate the route, import GPX or use the library. Choose a profile or custom speed, adjust the range and arrival behavior. Settings are saved with the route.\nRecord: capture your real journey, then stop to save it.\n\nThe notification lets you stop in the background. Other apps can detect mock locations. Speed profiles do not set physical activity in other apps: Life360 may use real sensors.\n\nLocation diagnostics shows the services used and the last confirmed delivery. On phones with Google Play Services, their mock mode is also enabled.\n\nMaps send requests to OpenStreetMap; search and Maps imports use online services. GPX files and recordings stay local. Some manufacturers require allowing background activity in battery settings when the screen is off.").setPositiveButton("Developer options",(d,w)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}).setNeutralButton("Close",null).show();
    }
    private interface Result<T> { void accept(T value); }
    private <T> void work(String message,Callable<T> job,Result<T> done) {
        if(busy){toast("Wait for the current operation to finish.");return;}busy=true;
        LinearLayout body=column();body.setPadding(dp(24),dp(20),dp(24),dp(20));ProgressBar progress=new ProgressBar(this);add(body,progress);gap(body,12);add(body,text(message,14,TEXT,false));AlertDialog dialog=new AlertDialog.Builder(this).setView(body).setCancelable(false).create();busyDialog=dialog;dialog.show();
        jobs.execute(()->{try{T result=job.call();ui.post(()->{busy=false;if(isDestroyed())return;dialog.dismiss();done.accept(result);});}catch(Exception e){ui.post(()->{busy=false;if(isDestroyed())return;dialog.dismiss();error(e.getMessage()==null?"Operation failed.":e.getMessage());});}});
    }
    private void askName(String title,String initial,Result<String> done) { EditText e=input("Name",initial,false);LinearLayout c=column();c.setPadding(dp(20),dp(12),dp(20),0);add(c,e);new AlertDialog.Builder(this).setTitle(title).setView(c).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String n=e.getText().toString().trim();if(n.isEmpty())n=initial;done.accept(n.substring(0,Math.min(100,n.length())));}).show(); }
    private void error(String message) { if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("Anomaly GPS").setMessage(message==null?"Check the values you entered.":message).setPositiveButton("OK",null).show(); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
}
