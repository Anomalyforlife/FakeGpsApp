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
        LinearLayout brand=column();add(brand,text("ANOMALY",21,TEXT,true));TextView sub=text("GPS, alle tue condizioni.",11,MUTED,false);add(brand,sub);heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button guide=button("Guida",false,this::help);heading.addView(guide,new LinearLayout.LayoutParams(dp(74),dp(48)));add(root,heading);gap(root,16);
        LinearLayout tabs=row();String[] names={"Posizione","Percorso","Registra","Archivio"};
        for(int i=0;i<names.length;i++){final int target=i;Button b=button(names[i],i==tab,()->{remember();tab=target;render();});b.setTextSize(12);b.setPadding(0,0,0,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(2),0,dp(2),0);tabs.addView(b,p);}add(root,tabs);gap(root,14);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=column();scroll.addView(content);
        sessionCard=card(content);LinearLayout statusRow=row();TextView dot=text("●  ",13,MINT,true);statusRow.addView(dot);status=text("Pronto a partire",15,TEXT,true);statusRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));add(sessionCard,statusRow);gap(sessionCard,6);details=text("Sessioni locali · nessun account richiesto",12,MUTED,false);add(sessionCard,details);
        pauseButton=button("Pausa",false,()->control(GpsService.PAUSE));stopButton=button("Termina sessione",false,()->control(GpsService.STOP));stopButton.setTextColor(RED);pair(sessionCard,pauseButton,stopButton); // hidden while idle
        gap(sessionCard,8);add(sessionCard,button("Diagnostica posizione",false,this::diagnostics));
        if(tab!=3)buildMap();
        if(tab==0)positionScreen();else if(tab==1)routeScreen();else if(tab==2)recordScreen();else libraryScreen();
        TextView foot=text("ANOMALY GPS  /  1.3   ·   I tuoi percorsi restano sul dispositivo",10,MUTED,false);foot.setGravity(Gravity.CENTER);add(content,foot);gap(content,20);updateSession();
    }
    private void buildMap() {
        LinearLayout container=column();container.setBackground(shape(CARD,20));container.setClipToOutline(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(260));lp.bottomMargin=dp(14);content.addView(container,lp);
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);container.addView(frame,new LinearLayout.LayoutParams(-1,-1));map=new MapView(this);frame.addView(map,new android.widget.FrameLayout.LayoutParams(-1,-1));
        if(selected!=null&&tab==1){map.route(selected.points);map.post(()->{if(map!=null)map.fit(selected.points);});map.marker(selected.points.get(0));}else{map.center(pin);map.marker(pin);}
        map.onPick=p->{if(tab==0&&!GpsService.state.active()){pin=p;if(latInput!=null){latInput.setText(number(p.lat));lonInput.setText(number(p.lon));}map.marker(p);if(pointLabel!=null)pointLabel.setText(coords(p));remember();}};
        LinearLayout zoom=column();Button plus=button("+",false,()->map.zoom(1)),minus=button("−",false,()->map.zoom(-1));plus.setContentDescription("Aumenta zoom");minus.setContentDescription("Riduci zoom");zoom.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48)));gap(zoom,5);zoom.addView(minus,new LinearLayout.LayoutParams(dp(48),dp(48)));android.widget.FrameLayout.LayoutParams z=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.TOP);z.setMargins(0,dp(10),dp(10),0);frame.addView(zoom,z);
    }
    private void positionScreen() {
        LinearLayout c=card(content);label(c,"01 / POSIZIONE FISSA");add(c,text("Scegli dove essere.",24,TEXT,true));gap(c,6);add(c,text("Tocca la mappa, cerca un luogo o inserisci le coordinate.",13,MUTED,false));gap(c,16);
        searchInput=input("Città, indirizzo o latitudine,longitudine","",false);searchInput.setSingleLine(true);add(c,searchInput);gap(c,9);add(c,button("Cerca sulla mappa",false,()->{String query=searchInput.getText().toString().trim();if(query.isEmpty()){toast("Inserisci un luogo o delle coordinate.");return;}new AlertDialog.Builder(this).setTitle("Cercare il luogo online?").setMessage("Il testo della ricerca viene inviato al servizio di geocodifica del dispositivo. Le coordinate vengono visualizzate sulla mappa OpenStreetMap.").setPositiveButton("Cerca",(d,w)->work("Ricerca del luogo…",()->new RouteImporter(this).locate(query),p->{pin=p;latInput.setText(number(p.lat));lonInput.setText(number(p.lon));map.center(p);map.marker(p);remember();})).setNegativeButton("Annulla",null).show();}));gap(c,16);
        label(c,"LATITUDINE");latInput=input("45.4642",number(pin.lat),true);add(c,latInput);gap(c,12);label(c,"LONGITUDINE");lonInput=input("9.1900",number(pin.lon),true);add(c,lonInput);gap(c,12);label(c,"ALTITUDINE · METRI");altInput=input("0",Double.toString(pin.altitude),true);add(c,altInput);gap(c,16);
        pointLabel=text(coords(pin),12,MINT,true);pointLabel.setTypeface(Typeface.MONOSPACE);add(c,pointLabel);gap(c,14);
        add(c,button("Attiva posizione fissa",true,()->{try{pin=readPin();map.marker(pin);map.center(pin);remember();Intent i=new Intent(this,GpsService.class).setAction(GpsService.STATIC).putExtra("lat",pin.lat).putExtra("lon",pin.lon).putExtra("alt",pin.altitude);begin(i);}catch(Exception e){error(e.getMessage());}}));
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
        speedLabel.setText(String.format(Locale.ITALY,"%s · %.1f km/h",playback.label(),playback.baseKmh));
        rangeLabel.setText(String.format(Locale.ITALY,"Oscillazione a regime: %.1f – %.1f km/h",playback.minKmh,playback.maxKmh));
        for(Map.Entry<String,Button> entry:profileButtons.entrySet()){
            boolean checked=entry.getKey().equals(playback.profile);entry.getValue().setBackground(shape(checked?MINT:EDGE,14));entry.getValue().setTextColor(checked?BG:TEXT);
        }
        if(endChoice!=null){updatingPlaybackUi=true;endChoice.setSelection("stop".equals(playback.end)?1:"loop".equals(playback.end)?2:0);updatingPlaybackUi=false;}
    }
    private void speedDialog(boolean custom) {
        LinearLayout body=column();body.setPadding(dp(20),dp(12),dp(20),dp(12));
        if(custom)label(body,"VELOCITÀ DI RIFERIMENTO · KM/H");else add(body,text(String.format(Locale.ITALY,"%s · riferimento %.1f km/h",playback.label(),playback.baseKmh),15,TEXT,true));
        EditText base=input("Es. 5,0",speedNumber(playback.baseKmh),true);if(custom)add(body,base);gap(body,12);
        label(body,"MINIMO · KM/H");EditText min=input("Es. 4,2",speedNumber(playback.minKmh),true);add(body,min);gap(body,12);
        label(body,"MASSIMO · KM/H");EditText max=input("Es. 5,8",speedNumber(playback.maxKmh),true);add(body,max);gap(body,12);
        if(custom){add(body,button("Calcola intervallo ±10%",false,()->{try{double v=parse(base);PlaybackSettings defaults=PlaybackSettings.custom(v,playback.end);min.setText(speedNumber(defaults.minKmh));max.setText(speedNumber(defaults.maxKmh));}catch(Exception e){base.setError("Inserisci una velocità tra 0 e 300.");}}));gap(body,10);}
        TextView validation=text("Il riferimento deve essere nel range. Per restare fermo usa tutti i valori a zero.",12,MUTED,false);add(body,validation);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(custom?"Velocità personalizzata":"Intervallo di oscillazione").setView(scroll).setNegativeButton("Annulla",null).setPositiveButton("Salva",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{PlaybackSettings next=new PlaybackSettings(custom?PlaybackSettings.CUSTOM:playback.profile,custom?parse(base):playback.baseKmh,parse(min),parse(max),playback.end);dialog.dismiss();savePlayback(next,true,null);}
            catch(Exception e){validation.setText(e instanceof NumberFormatException?"Inserisci numeri validi; puoi usare la virgola per i decimali.":e.getMessage());validation.setTextColor(RED);}
        }));dialog.show();
    }
    private void savePlayback(PlaybackSettings next,boolean applyToActive,Runnable after) {
        if(selected==null)return;String id=selected.id;
        if(GpsService.RECORD.equals(GpsService.state.mode)&&id.equals(GpsService.state.trackId)){toast("Termina la registrazione prima di cambiare le impostazioni.");refreshPlaybackControls();return;}
        refreshPlaybackControls();
        work("Salvataggio impostazioni del percorso…",()->new TrackStore(this).updatePlayback(id,next),saved->{
            if(selected!=null&&selected.id.equals(id)){selected=saved;playback=next;refreshPlaybackControls();remember();}
            if(applyToActive&&GpsService.ROUTE.equals(GpsService.state.mode)&&id.equals(GpsService.state.trackId))startService(new Intent(this,GpsService.class).setAction(GpsService.SPEED).putExtra("id",id));
            if(after!=null)after.run();
        });
    }
    private void startPlayback() {
        if(selected==null)return;
        if(GpsService.state.active()||requestingStart){toast("Termina prima la sessione attiva.");return;}
        String id=selected.id;remember();savePlayback(playback,false,()->begin(new Intent(this,GpsService.class).setAction(GpsService.ROUTE).putExtra("id",id)));
    }
    private void routeScreen() {
        LinearLayout c=card(content);label(c,"02 / SIMULA UN PERCORSO");add(c,text("Muoviti, a modo tuo.",24,TEXT,true));gap(c,6);add(c,text("Condividi un itinerario da Google Maps con Anomaly GPS, oppure incolla il link qui.",13,MUTED,false));gap(c,14);
        sharedInput=input("Link Google Maps oppure due tappe, una per riga",sharedText,false);sharedInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);sharedInput.setMinLines(3);sharedInput.setMaxLines(6);add(c,sharedInput);gap(c,10);
        pair(c,button("Importa da Maps",false,this::importLink),button("Apri GPX",false,this::pickGpx));gap(c,12);
        add(c,text("Maps → scegli Auto o A piedi prima di ricalcolare le tappe. Per seguire esattamente una traccia, usa GPX o una registrazione.",12,MUTED,false));
        LinearLayout playback=card(content);label(playback,"PERCORSO SELEZIONATO");
        if(selected==null){add(playback,text("Il prossimo viaggio inizia qui.",19,TEXT,true));gap(playback,6);add(playback,text("Importa un percorso o selezionalo dall’archivio.",13,MUTED,false));return;}
        add(playback,text(selected.name,20,TEXT,true));gap(playback,6);add(playback,text(String.format(Locale.ITALY,"%.2f km  ·  %,d punti\n%s",selected.distance()/1000,selected.points.size(),selected.source),13,MUTED,false));gap(playback,18);
        label(playback,"VELOCITÀ DI SIMULAZIONE");speedLabel=text("",28,MINT,true);add(playback,speedLabel);rangeLabel=text("",13,MUTED,false);add(playback,rangeLabel);gap(playback,6);
        liveSpeedLabel=text("",15,TEXT,true);add(playback,liveSpeedLabel);gap(playback,14);
        pair(playback,profileButton(PlaybackSettings.WALK,"A piedi · 5 km/h"),profileButton(PlaybackSettings.BIKE,"Bici · 18 km/h"));gap(playback,8);
        pair(playback,profileButton(PlaybackSettings.MOTO,"Moto · 50 km/h"),profileButton(PlaybackSettings.CAR,"Auto · 70 km/h"));gap(playback,8);
        add(playback,profileButton(PlaybackSettings.CUSTOM,"Personalizzata…"));gap(playback,10);
        add(playback,button("Modifica intervallo di oscillazione",false,()->speedDialog(false)));gap(playback,8);
        add(playback,text("La velocità cambia gradualmente entro il range. Partenza, ripresa e cambi di profilo sono progressivi: durante queste transizioni può essere fuori intervallo. Pausa e arrivo impostano zero.",12,MUTED,false));gap(playback,10);
        add(playback,text("I profili impostano solo la velocità. Life360 e le altre app possono usare i sensori reali del telefono: l’attività mostrata potrebbe essere diversa. Moto e auto possono essere classificate entrambe come veicolo.",12,MUTED,false));gap(playback,17);
        label(playback,"ALLA FINE DEL PERCORSO");endChoice=new Spinner(this);String[] options={"Resta fermo alla destinazione","Termina e ripristina il GPS reale","Ripeti da capo (salto alla partenza)"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);endChoice.setAdapter(adapter);endChoice.setMinimumHeight(dp(48));add(playback,endChoice);refreshPlaybackControls();
        endChoice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){String value=new String[]{"hold","stop","loop"}[index];if(!updatingPlaybackUi&&!value.equals(MainActivity.this.playback.end))savePlayback(MainActivity.this.playback.withEnd(value),false,null);}});
        gap(playback,8);add(playback,text("Profilo, velocità, range e scelta all’arrivo sono salvati per questo percorso. Velocità e range si possono cambiare durante la simulazione; la scelta all’arrivo si applica al prossimo avvio.",12,MUTED,false));gap(playback,16);
        add(playback,button("Avvia simulazione",true,this::startPlayback));gap(playback,10);pair(playback,button("Apri in Maps",false,()->openMaps(selected)),button("Esporta GPX",false,()->export(selected)));
    }
    private void recordScreen() {
        LinearLayout c=card(content);label(c,"03 / REGISTRA IL MONDO REALE");add(c,text("Un percorso tutto tuo.",24,TEXT,true));gap(c,8);add(c,text("Registra il tragitto che fai davvero e riproducilo quando vuoi, alla velocità che scegli.",14,MUTED,false));gap(c,20);
        add(c,text("●  GPS reale, aggiornamento ogni secondo\n●  Salvataggio locale durante il tragitto\n●  Continua anche a schermo spento\n●  Esportazione GPX dall’archivio",14,TEXT,false));gap(c,16);add(c,text("La registrazione ignora le posizioni simulate e i fix con precisione peggiore di 50 m. Disattiva le altre app GPS fake. Per registrare servono almeno due punti distinti.",12,MUTED,false));gap(c,18);
        add(c,button("Inizia registrazione",true,()->{if(GpsService.state.active()||requestingStart){toast("Termina prima la sessione attiva.");return;}askName("Nome della registrazione","Tragitto "+new SimpleDateFormat("dd MMM · HH:mm",Locale.ITALY).format(new Date()),name->begin(new Intent(this,GpsService.class).setAction(GpsService.RECORD).putExtra("name",name)));}));
        gap(c,12);add(c,text("Termina dalla scheda o dalla notifica: la traccia viene salvata nell’archivio. Le sessioni hanno una durata massima di 12 ore.",12,MUTED,false));
    }
    private void libraryScreen() {
        LinearLayout title=card(content);label(title,"04 / IL TUO ARCHIVIO");add(title,text("Strade da ritrovare.",24,TEXT,true));gap(title,8);add(title,text("Percorsi importati e registrati, conservati solo sul tuo telefono.",13,MUTED,false));gap(title,12);add(title,button("Importa una traccia GPX",false,this::pickGpx));
        try {
            List<Track> tracks=new TrackStore(this).list();
            if(tracks.isEmpty()){LinearLayout empty=card(content);add(empty,text("Ancora nessun percorso.",20,TEXT,true));gap(empty,8);add(empty,text("Registra una passeggiata, importa un GPX o condividi un percorso da Google Maps.",14,MUTED,false));return;}
            for(Track t:tracks){LinearLayout c=card(content);add(c,text(t.name,18,TEXT,true));gap(c,7);add(c,text(String.format(Locale.ITALY,"%.2f km  ·  %,d punti  ·  %s\n%s%s",t.distance()/1000,t.points.size(),new SimpleDateFormat("dd MMM yyyy",Locale.ITALY).format(new Date(t.created)),t.source,t.playback==null?"":String.format(Locale.ITALY,"\n%s · %.1f km/h · range %.1f–%.1f",t.playback.label(),t.playback.baseKmh,t.playback.minKmh,t.playback.maxKmh)),12,MUTED,false));gap(c,14);pair(c,button("Simula",true,()->select(t)),button("Opzioni",false,()->trackOptions(t)));}
        }catch(Exception e){error("Impossibile leggere l’archivio: "+e.getMessage());}
    }
    private void trackOptions(Track t) {
        new AlertDialog.Builder(this).setTitle(t.name).setItems(new String[]{"Esporta GPX","Rinomina","Apri estremi in Google Maps","Elimina"},(d,which)->{
            if(which==0)export(t);else if(which==1){if(GpsService.RECORD.equals(GpsService.state.mode)&&t.id.equals(GpsService.state.trackId)){toast("Termina la registrazione prima di rinominarla.");return;}askName("Rinomina percorso",t.name,name->work("Rinomina del percorso…",()->new TrackStore(this).rename(t.id,name),renamed->{if(selected!=null&&selected.id.equals(t.id)){selected=renamed;loadSelectedPlayback();}render();}));}
            else if(which==2)openMaps(t);else{
                if(GpsService.state.active()&&t.id.equals(GpsService.state.trackId)){toast("Termina la sessione prima di eliminare il percorso.");return;}
                new AlertDialog.Builder(this).setTitle("Eliminare il percorso?").setMessage(t.name).setNegativeButton("Annulla",null).setPositiveButton("Elimina",(dd,w)->{new TrackStore(this).delete(t.id);if(selected!=null&&selected.id.equals(t.id))selected=null;remember();render();}).show();
            }
        }).show();
    }
    private void select(Track t) { remember();selected=t;loadSelectedPlayback();tab=1;remember();render(); }
    private void importLink() {
        remember();if(sharedText.trim().isEmpty()){toast("Incolla un link Google Maps o almeno due tappe.");return;}
        String source=sharedText;
        LinearLayout body=column();body.setPadding(dp(20),dp(12),dp(20),dp(12));
        add(body,text("Come vuoi ricalcolare il tragitto?",16,TEXT,true));gap(body,8);
        RadioGroup choices=new RadioGroup(this);choices.setOrientation(RadioGroup.VERTICAL);
        RoutingProfile initial=RoutingProfile.fromPreference(prefs.getString("routing_mode","car"));
        int carId=View.generateViewId(),footId=View.generateViewId();
        RadioButton car=new RadioButton(this);car.setId(carId);car.setText(R.string.routing_car);car.setTextColor(TEXT);car.setMinHeight(dp(48));choices.addView(car);
        RadioButton foot=new RadioButton(this);foot.setId(footId);foot.setText(R.string.routing_foot);foot.setTextColor(TEXT);foot.setMinHeight(dp(48));choices.addView(foot);
        choices.check(initial==RoutingProfile.FOOT?footId:carId);add(body,choices);gap(body,12);
        add(body,text("La scelta cambia il tragitto. La velocità di simulazione si imposta separatamente, dopo l’importazione.",13,MUTED,false));gap(body,12);
        add(body,text("Le tappe saranno geocodificate e inviate al servizio pubblico OSRM. Il risultato può differire da Google Maps e non include traffico o alternative. Per conservare una traccia esatta usa GPX.",12,MUTED,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Importa da Maps").setView(scroll).setNegativeButton("Annulla",null).setPositiveButton("Importa",(d,w)->{
            RoutingProfile mode=choices.getCheckedRadioButtonId()==footId?RoutingProfile.FOOT:RoutingProfile.CAR;
            askName("Nome del percorso","Percorso importato",name->{
                prefs.edit().putString("routing_mode",mode.id).apply();
                work("Calcolo del percorso · "+mode.label+"…",()->{Track t=new RouteImporter(this).importText(source,name,mode);new TrackStore(this).save(t);return t;},this::select);
            });
        }).show();
    }
    private void pickGpx() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,51); }
    private void export(Track t) { exportTrack=t;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/gpx+xml").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,t.name.replaceAll("[^\\p{L}\\p{N} _-]","_")+".gpx");startActivityForResult(i,52); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==51)importGpx(uri);else if(request==52&&exportTrack!=null){Track t=exportTrack;work("Esportazione GPX…",()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Destinazione non disponibile.");Gpx.write(t,out);}return t;},ignored->toast("GPX esportato."));}
    }
    private void importGpx(Uri uri) { work("Lettura della traccia GPX…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("File non disponibile.");Track t=Gpx.read(in,"Traccia GPX");new TrackStore(this).save(t);return t;}},this::select); }
    @SuppressWarnings("deprecation")
    private void handleShare(Intent intent) {
        if(!Intent.ACTION_SEND.equals(intent.getAction()))return;
        if("text/plain".equals(intent.getType())){remember();sharedText=intent.getStringExtra(Intent.EXTRA_TEXT);if(sharedText==null)sharedText="";if(sharedText.length()>16000)sharedText=sharedText.substring(0,16000);tab=1;render();toast("Link ricevuto. Tocca Importa da Maps.");}
        else{Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null&&"content".equals(uri.getScheme()))importGpx(uri);}
    }
    private void openMaps(Track t) {
        if(t.points.size()<2)return;GeoPoint a=t.points.get(0),b=t.points.get(t.points.size()-1);
        new AlertDialog.Builder(this).setTitle("Apri Google Maps").setMessage("Google Maps riceverà partenza e arrivo e calcolerà il proprio itinerario. La traccia GPX non viene trasferita.").setNegativeButton("Annulla",null).setPositiveButton("Apri",(d,w)->{
            Uri url=Uri.parse("https://www.google.com/maps/dir/").buildUpon().appendQueryParameter("api","1").appendQueryParameter("origin",a.lat+","+a.lon).appendQueryParameter("destination",b.lat+","+b.lon).appendQueryParameter("travelmode",t.routingProfile==RoutingProfile.FOOT?"walking":"driving").build();
            try{startActivity(new Intent(Intent.ACTION_VIEW,url));}catch(ActivityNotFoundException e){error("Nessuna app disponibile per aprire il link.");}
        }).show();
    }
    private void begin(Intent intent) {
        if(GpsService.state.active()||requestingStart){toast("Termina prima la sessione attiva.");return;}
        if(!GpsService.RECORD.equals(intent.getAction())) {
            AppOpsManager ops=(AppOpsManager)getSystemService(APP_OPS_SERVICE);
            if(ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,android.os.Process.myUid(),getPackageName())!=AppOpsManager.MODE_ALLOWED) {
                new AlertDialog.Builder(this).setTitle("Seleziona l’app di posizione simulata").setMessage("In Opzioni sviluppatore → App di posizione simulata scegli Anomaly GPS, poi torna qui e avvia la simulazione.").setPositiveButton("Apri impostazioni",(d,w)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}).setNegativeButton("Annulla",null).show();return;
            }
        }
        if(!((LocationManager)getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)) {new AlertDialog.Builder(this).setTitle("Attiva la posizione GPS").setMessage("Android richiede la posizione attiva per la sessione GPS.").setPositiveButton("Impostazioni",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Annulla",null).show();return;}
        Runnable launch=()->{
            try{requestingStart=true;startForegroundService(intent);ui.postDelayed(()->{requestingStart=false;updateSession();},1500);}
            catch(Exception e){requestingStart=false;error("Impossibile avviare il servizio: "+e.getMessage());}
        };
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){afterPermission=launch;List<String> permissions=new ArrayList<>(Arrays.asList(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION));if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.POST_NOTIFICATIONS);requestPermissions(permissions.toArray(new String[0]),61);}
        else if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){afterPermission=launch;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},61);}
        else launch.run();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==61){Runnable action=afterPermission;afterPermission=null;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){if(action!=null)action.run();}else new AlertDialog.Builder(this).setTitle("Serve la posizione precisa").setMessage("Consenti la posizione precisa mentre usi l’app. Il servizio manterrà la sessione in background finché non premi Termina.").setPositiveButton("Impostazioni app",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Chiudi",null).show();}
    }
    private void control(String action) { if(GpsService.state.active())startService(new Intent(this,GpsService.class).setAction(action)); }
    private void updateSession() {
        if(status==null)return;GpsService.State s=GpsService.state;status.setText(requestingStart?"Avvio della sessione…":s.message);status.setTextColor(s.error.isEmpty()?TEXT:RED);
        if(s.active())details.setText(String.format(Locale.ITALY,"%s  ·  %.2f km  ·  %s%s%s\n%s%s",GpsService.RECORD.equals(s.mode)?"REC · GPS reale":GpsService.STATIC.equals(s.mode)?"GPS statico":"GPS simulato",s.distance/1000,duration(s.elapsed),GpsService.ROUTE.equals(s.mode)?String.format(Locale.ITALY," · %.1f km/h",s.speed):"",s.position==null?"":"\n"+coords(s.position),s.backend,s.warning.isEmpty()?"":"\n"+s.warning));
        else details.setText(s.error.isEmpty()?"Sessioni locali · nessun account richiesto":s.error);
        View controls=(View)pauseButton.getParent();controls.setVisibility(s.active()?View.VISIBLE:View.GONE);pauseButton.setVisibility(GpsService.ROUTE.equals(s.mode)&&!s.holding?View.VISIBLE:View.GONE);pauseButton.setText(s.paused?"Riprendi":"Pausa");stopButton.setEnabled(!s.saving);
        if(map!=null&&s.active()&&s.position!=null)map.marker(s.position);
        if(liveSpeedLabel!=null)liveSpeedLabel.setText(GpsService.ROUTE.equals(s.mode)&&selected!=null&&selected.id.equals(s.trackId)?String.format(Locale.ITALY,"Velocità attuale: %.1f km/h",s.speed):"La velocità attuale apparirà durante la simulazione.");
        if(!s.error.isEmpty()&&!s.error.equals(lastError)){lastError=s.error;error(s.error);}
        if(!s.active()&&!s.trackId.isEmpty()&&!s.trackId.equals(lastFinishedId)){lastFinishedId=s.trackId;if(tab==3)ui.post(()->{if(!isDestroyed())render();});}
    }
    private static String duration(long millis) { long seconds=millis/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60); }
    private static String number(double value) { return String.format(Locale.ROOT,"%.6f",value); }
    private static String speedNumber(double value) { return Double.toString(value).replace('.', ','); }
    private static String coords(GeoPoint p) { return number(p.lat)+"  /  "+number(p.lon); }
    private void diagnostics() {
        GpsService.State s=GpsService.state;
        String sent=s.lastSentNanos==0?"Nessun invio simulato confermato":String.format(Locale.ITALY,"Ultimo invio confermato: %.1f secondi fa",Math.max(0,SystemClock.elapsedRealtimeNanos()-s.lastSentNanos)/1e9);
        String message=Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n\n"+s.message+"\n"+s.backend+"\n"+sent+(s.position==null?"":"\n"+coords(s.position))+(s.warning.isEmpty()?"":"\n"+s.warning)+(s.error.isEmpty()?"":"\n"+s.error)+"\n\nL’invio confermato indica che i servizi di posizione hanno accettato il punto. Non conferma cosa mostrano le altre app.\n\nSe Maps è stabile ma Life360 oscilla, annota se torna a una posizione precedente o a quella reale, e se la notifica Anomaly GPS resta presente. Sensori, rete e aggiornamenti di Life360 richiedono una verifica separata.";
        new AlertDialog.Builder(this).setTitle("Diagnostica posizione · 1.3").setMessage(message).setPositiveButton("Copia dettagli",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Diagnostica Anomaly GPS",message));toast("Dettagli copiati.");}).setNegativeButton("Chiudi",null).show();
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("Primi passi con Anomaly GPS").setMessage("1. In Android, apri Informazioni sul telefono e tocca 7 volte Numero build per attivare le Opzioni sviluppatore.\n\n2. In Opzioni sviluppatore → App di posizione simulata seleziona Anomaly GPS. Non serve root.\n\n3. Attiva la posizione e consenti la posizione precisa quando l’app la richiede.\n\nPosizione: scegli un punto e attiva il GPS statico.\nPercorso: condividi da Maps e scegli Auto o A piedi per ricalcolare il tragitto, importa GPX o usa l’archivio. Scegli profilo o velocità personalizzata, regola il range e il comportamento all’arrivo. Le impostazioni restano salvate nel percorso.\nRegistra: acquisisci il tuo tragitto reale, poi termina per salvarlo.\n\nLa notifica permette di terminare anche in background. Le altre app possono riconoscere le posizioni simulate. I profili di velocità non impostano l’attività fisica nelle altre app: Life360 può usare i sensori reali.\n\nDiagnostica posizione mostra i servizi utilizzati e l’ultimo invio confermato. Su telefoni con Google Play Services viene attivata anche la loro modalità simulata.\n\nLe mappe inviano richieste a OpenStreetMap; ricerca e importazione Maps usano servizi online. GPX e registrazioni restano locali. A schermo spento alcuni produttori richiedono di consentire l’attività in background nelle impostazioni batteria.").setPositiveButton("Opzioni sviluppatore",(d,w)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}).setNeutralButton("Chiudi",null).show();
    }
    private interface Result<T> { void accept(T value); }
    private <T> void work(String message,Callable<T> job,Result<T> done) {
        if(busy){toast("Attendi il completamento dell’operazione in corso.");return;}busy=true;
        LinearLayout body=column();body.setPadding(dp(24),dp(20),dp(24),dp(20));ProgressBar progress=new ProgressBar(this);add(body,progress);gap(body,12);add(body,text(message,14,TEXT,false));AlertDialog dialog=new AlertDialog.Builder(this).setView(body).setCancelable(false).create();busyDialog=dialog;dialog.show();
        jobs.execute(()->{try{T result=job.call();ui.post(()->{busy=false;if(isDestroyed())return;dialog.dismiss();done.accept(result);});}catch(Exception e){ui.post(()->{busy=false;if(isDestroyed())return;dialog.dismiss();error(e.getMessage()==null?"Operazione non riuscita.":e.getMessage());});}});
    }
    private void askName(String title,String initial,Result<String> done) { EditText e=input("Nome",initial,false);LinearLayout c=column();c.setPadding(dp(20),dp(12),dp(20),0);add(c,e);new AlertDialog.Builder(this).setTitle(title).setView(c).setNegativeButton("Annulla",null).setPositiveButton("Salva",(d,w)->{String n=e.getText().toString().trim();if(n.isEmpty())n=initial;done.accept(n.substring(0,Math.min(100,n.length())));}).show(); }
    private void error(String message) { if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("Anomaly GPS").setMessage(message==null?"Controlla i dati inseriti.":message).setPositiveButton("OK",null).show(); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
}
