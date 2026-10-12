# Anomaly GPS

<img src="output/imagegen/anomaly-gps-icon-v1.3.png" alt="Icona Anomaly GPS" width="112">

App Android per simulare il GPS, riprodurre percorsi da Google Maps o GPX con velocità variabile e registrare tragitti reali.

Interfaccia in italiano, nessun account, nessuna chiave API Google richiesta. Funziona su **Android 8.0 o successivo**, senza root.

**Versione 1.3.0** · [APK](AnomalyGPS.apk) · [Checksum SHA-256](AnomalyGPS.apk.sha256) · [Verifiche](docs/validation.md)

## Funzionalità

- **Posizione fissa:** scegli un punto sulla mappa OpenStreetMap, cerca un indirizzo oppure inserisci coordinate e altitudine.
- **Percorsi da Google Maps:** condividi o incolla un link e scegli se ricalcolare il tragitto **in auto o a piedi**. Puoi anche inserire manualmente le tappe.
- **Riproduzione GPX:** importa una traccia e percorrila alla velocità desiderata.
- **Velocità variabile:** profili A piedi, Bici, Moto, Auto e Personalizzata, con riferimento e intervallo modificabile. Accelerazione e variazioni sono graduali.
- **Controllo della simulazione:** pausa, ripresa e scelta tra restare alla destinazione, ripristinare il GPS reale o ripetere il percorso.
- **Impostazioni per traccia:** velocità, intervallo, profilo, comportamento all’arrivo e modalità di ricalcolo vengono conservati nel percorso.
- **Registrazione reale:** registra i tuoi spostamenti con salvataggio periodico; le posizioni simulate vengono escluse.
- **Archivio locale:** seleziona, rinomina, elimina ed esporta le tracce in GPX.
- **Uso in background:** servizio con notifica e pulsante **Termina**, aggiornamenti della posizione ogni 500 ms e diagnostica copiabile.

## Installazione

1. Scarica [AnomalyGPS.apk](AnomalyGPS.apk) sul telefono e aprilo. Consenti l’installazione dalla sorgente scelta quando Android lo richiede.
2. Attiva le **Opzioni sviluppatore**: solitamente occorre toccare sette volte **Numero build** nelle informazioni del dispositivo.
3. Apri **Opzioni sviluppatore → App di posizione simulata** e seleziona **Anomaly GPS**.
4. Attiva la localizzazione e concedi all’app la **posizione precisa mentre la usi**. Consenti le notifiche per accedere rapidamente al pulsante di arresto.
5. Apri l’app e avvia una posizione fissa, un percorso o una registrazione.

La versione 1.3 può essere installata sopra la 1.1 o la 1.2 mantenendo i dati: gli APK condividono la stessa firma. L’APK fornito è una build debug per installazione diretta.

<details>
<summary>Aggiornamento dalla prima versione 1.0</summary>

La prima versione usa una firma diversa e richiede una nuova installazione. Esporta prima i percorsi dall’Archivio in GPX, termina la simulazione e disinstalla la vecchia app. Installa poi la nuova, selezionala nuovamente come app di posizione simulata e reimporta i GPX. La disinstallazione cancella i dati locali; i GPX non conservano le impostazioni di simulazione.

</details>

## Utilizzo

### Simulare una posizione fissa

Nella scheda **Posizione**, scegli il punto sulla mappa o inserisci le coordinate, imposta l’altitudine e avvia la simulazione. La posizione rimane attiva anche in background fino all’arresto della sessione.

### Importare un percorso da Google Maps

1. Crea le indicazioni in Google Maps, premi **Condividi** e scegli **Anomaly GPS**. In alternativa, incolla il link nella scheda **Percorso**.
2. Tocca **Importa da Maps** e scegli **Auto** oppure **A piedi**.
3. Conferma il ricalcolo e assegna un nome alla traccia.
4. Seleziona il profilo di velocità, modifica l’intervallo se necessario e avvia la riproduzione.

Per inserire manualmente un percorso puoi indicare da 2 a 20 tappe, una per riga, per esempio:

```text
45.4642,9.1900
45.4780,9.2270
```

**Il tragitto viene ricalcolato con OSRM:** dal link Google Maps vengono recuperati partenza, arrivo e, quando disponibili, tappe intermedie. La geometria può differire da quella visualizzata su Google Maps; traffico, alternative e indicazioni di navigazione non vengono importati. Un link a un singolo luogo non basta per ricostruire un percorso.

Auto e A piedi usano grafi di instradamento distinti. La modalità scelta viene ricordata e salvata nella traccia; in caso di errore pedonale l’app non passa automaticamente al percorso in auto. Cambiare successivamente il profilo di velocità non ricalcola la geometria. Per seguire una traccia specifica, importa un GPX o usa una registrazione.

### Regolare la velocità e l’arrivo

| Profilo | Velocità di riferimento | Intervallo iniziale |
| --- | --- | --- |
| A piedi | 5 km/h | 4,2–5,8 km/h |
| Bici | 18 km/h | 15–21 km/h |
| Moto | 50 km/h | 45–55 km/h |
| Auto | 70 km/h | 63–77 km/h |
| Personalizzata | A scelta | A scelta |

Riferimento, minimo e massimo sono modificabili tra **0 e 300 km/h**. Per una simulazione in movimento, il minimo deve essere inferiore al massimo e l’intervallo deve includere il riferimento. Impostando tutti i valori a zero, la posizione resta ferma.

La velocità oscilla gradualmente nell’intervallo scelto. Partenza, ripresa e cambio profilo prevedono transizioni che possono temporaneamente uscire dall’intervallo; pausa e permanenza all’arrivo inviano velocità zero. Le modifiche alla velocità si applicano anche alla riproduzione attiva dello stesso percorso.

All’arrivo puoi scegliere di:

- **Restare alla destinazione**, mantenendo il punto simulato con velocità zero.
- **Terminare**, interrompendo la simulazione e ripristinando il GPS reale.
- **Ripetere**, tornando al punto iniziale e ricominciando il percorso. Il ritorno comporta un salto di posizione.

Le impostazioni vengono salvate per ogni traccia. Una modifica al comportamento all’arrivo vale dal prossimo avvio.

### Registrare e gestire le tracce

Termina le simulazioni, anche di altre app, e avvia la scheda **Registra**. L’app acquisisce il GPS reale ogni secondo, scarta i punti simulati e quelli con precisione peggiore di 50 metri. Per creare una traccia servono almeno due punti distinti.

La registrazione viene salvata ogni 10 secondi e al termine. Se Android chiude il processo, rimane l’ultimo salvataggio completato; una registrazione molto breve potrebbe non essere recuperabile. Le sessioni non ripartono automaticamente.

Nell’**Archivio** puoi selezionare, rinominare, eliminare ed esportare i percorsi. L’importazione GPX accetta una traccia continua con punti `trkpt` o `rtept`, fino a 12 MB e 100.000 punti; i file con più segmenti vengono rifiutati. Coordinate, altitudine e timestamp sono conservati, ma la riproduzione usa la velocità impostata nell’app, senza seguire i tempi originali.

## Dati, rete e compatibilità

Le tracce e le loro impostazioni restano nell’area privata dell’app. Non è prevista sincronizzazione cloud e il backup Android è disabilitato. **Disinstallare l’app elimina i dati locali:** esporta prima i percorsi da conservare. Il GPX include geometria, altitudine e timestamp, ma non profili di velocità, comportamento all’arrivo o modalità di ricalcolo.

La mappa usa OpenStreetMap: le richieste delle tile comunicano l’area visualizzata al servizio. La ricerca usa il geocoder Android; il ricalcolo invia le coordinate delle tappe ai servizi pubblici OSRM di FOSSGIS, previa conferma. La disponibilità dei servizi può variare. Simulazione e GPX funzionano senza rete; la mappa offline può essere incompleta.

L’app usa le API Android di posizione simulata e, quando disponibili, Google Play Services. Le altre applicazioni possono riconoscere il flag mock e gestire diversamente le posizioni ricevute. I profili regolano il movimento GPS: **non simulano accelerometro, giroscopio o bussola e non garantiscono categorie di attività nelle altre app**, comprese le icone di movimento di Life360. Approfondimento in [Life360 e riconoscimento dell’attività](docs/life360-research.md).

Le sessioni hanno durata massima di 12 ore e non si avviano automaticamente al riavvio del telefono. Le restrizioni della batteria del dispositivo possono influire sul funzionamento in background.

## Build con ScoutSolutions

Il progetto è nativo Android, scritto in **Java 17**, con Gradle Wrapper **8.11.1**, Android Gradle Plugin **8.9.2** e compile/target SDK **35**. Non richiede un server web.

Compilazioni, test Gradle e lint devono essere eseguiti tramite l’Executor ScoutSolutions, usando `scoutsolutions-run.start_build` e seguendo lo stato con `scoutsolutions-run.status` e `scoutsolutions-log`. Non eseguire Gradle nel container AI.

| Campo | Valore |
| --- | --- |
| Build image | `thyrlian/android-sdk:10.0` — Linux amd64 |
| Sandbox AI | Immagine predefinita, se configurabile separatamente |
| App port | Vuota |
| Dev command / dev port | Vuoti |
| Start command | Vuoto, se consentito dalla piattaforma |

Comando di build da configurare nell’Executor, **su una sola riga**:

```sh
export ANDROID_HOME="$PWD/.android-sdk" && export GRADLE_USER_HOME="$PWD/.gradle-cache" && yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null && sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-35" "build-tools;35.0.0" && sh ./gradlew --no-daemon :app:assembleDebug
```

Il comando accetta automaticamente le licenze SDK e richiede accesso ai repository Google, Maven Central e Gradle. SDK e cache sono conservati nel progetto. Il task `assembleDebug` esegue anche `testDebugUnitTest` e `lintDebug`; l’APK risultante si trova in `app/build/outputs/apk/debug/app-debug.apk`.

La chiave debug persistente è in `.signing/debug.keystore`, esclusa da Git. Conservala privatamente per produrre aggiornamenti con la stessa firma; un nuovo checkout senza questa chiave può generare un APK con firma diversa. Per una distribuzione release occorrono configurazione e firma dedicate.

## Verifiche e documentazione

L’ultima verifica della versione 1.3.0, build ScoutSolutions **#11**, ha completato **77 test unitari**, con Android lint senza errori e un avviso sulla versione della dipendenza Google mantenuta per compatibilità. I test coprono percorsi, link Maps, backend mock, dinamica della velocità, impostazioni, migrazioni e modalità Auto/A piedi. Non è stata ancora completata una prova della 1.3 su dispositivo: i risultati di compilazione non garantiscono il comportamento di ogni telefono o app esterna.

- [Rapporto di validazione, checksum e firma APK](docs/validation.md)
- [Checklist delle prove su dispositivo](docs/device-checklist.md)
- [Importazione Maps e ricalcolo Auto/A piedi](docs/maps-routing.md)
- [Ricerca su Life360 e riconoscimento dell’attività](docs/life360-research.md)
- [Icona generata e relativo prompt](output/imagegen/README.md)
