# Verifica Anomaly GPS 1.3.0 — 12 ottobre 2026

## Esito

La build finale **#11** è stata avviata tramite `scoutsolutions-run.start_build` e risulta **success** in `scoutsolutions-log.list_builds`. Timestamp della piattaforma: `2026-10-12T19:20:26.784Z`. Log Gradle prodotto dall'Executor nella workspace: `BUILD SUCCESSFUL in 1m 30s`. Nessuna compilazione o server è stato eseguito nel container AI.

I report generati dall’Executor nella workspace confermano:

| Suite | Test | Errori / fallimenti / saltati |
| --- | ---: | --- |
| RouteEngineTest | 12 | 0 / 0 / 0 |
| MapsLinkTest | 11 | 0 / 0 / 0 |
| MockSessionTest | 11 | 0 / 0 / 0 |
| SpeedDynamicsTest | 13 | 0 / 0 / 0 |
| PlaybackSettingsTest | 14 | 0 / 0 / 0 |
| TrackSettingsTest | 6 | 0 / 0 / 0 |
| RouteRouterTest | 10 | 0 / 0 / 0 |

Totale: **77 test passati**. Report JUnit timestamp `2026-10-12T19:22:08`–`19:22:09`, hostname `dba71a86c3f7`. Android lint: **0 errori, 1 avviso `GradleDependency`** per la versione Google mantenuta intenzionalmente.

Le verifiche del ricalcolo coprono scelta del grafo Auto/A piedi e uso della geometria restituita, persistenza della modalità, compatibilità con tracce precedenti, errori di rete e assenza di fallback pedonale→auto. Le verifiche della velocità coprono partenza progressiva, limiti di accelerazione, variazioni entro l’intervallo a regime, integrazione della distanza indipendente dalla frequenza degli aggiornamenti, cambio profilo senza azzerare la posizione, arresto/ripresa, validazione dei valori e roundtrip JSON delle impostazioni per percorso. La compatibilità con le tracce precedenti e la conservazione della geometria sono verificate anche con impostazioni mancanti o non valide.

Si usa `play-services-location:21.3.0`: la 21.4.0 contiene metadati Kotlin 2.3 non compatibili con l'analizzatore di AGP 8.9. Il log finale non contiene quegli errori di metadati. `play-services-location.properties` nell'APK conferma la versione 21.3.0. Wrapper 8.11.1, AGP 8.9.2, Java 17, compile/target SDK 35 invariati.

Report: `app/build/test-results/testDebugUnitTest/` e `app/build/reports/lint-results-debug.txt`.

## APK consegnato

- File originale: `app/build/outputs/apk/debug/app-debug.apk`.
- Copia per il download: `AnomalyGPS.apk` nella root del progetto.
- Metadata: `dev.scoutsolutions.fakegps`, versione **1.3.0**, versionCode **4**, variante debug, minSdk 26.
- Dimensione: **3.799.916 byte**.
- SHA-256: `54107ee4cf05c80de097588ee2e50a519909e82d71184eee44bae574e4749273`.
- Verifica di integrità ZIP: nessun errore nei dati compressi.

Il checksum è anche in `AnomalyGPS.apk.sha256`. È un APK debug firmato automaticamente dal processo Android Gradle; non è una release destinata a Google Play.

Certificato v2 SHA-256: `05:3E:EF:82:29:55:FB:29:07:8E:0E:5F:C3:64:EC:DE:74:2E:4D:E6:1A:B2:F0:8B:76:BF:37:71:69:E3:61:12`. Confrontato con l’APK 1.2 conservato prima della nuova build: **identico**, come già verificato tra 1.1 e 1.2. L’aggiornamento dalla 1.1 o dalla 1.2 può essere installato senza disinstallare, mantenendo archivio e preferenze. La chiave debug è mantenuta in `.signing/debug.keystore`, esclusa da Git: non eliminare questa cartella.

La firma differisce ancora da quella dell’APK 1.0 della build 4. Soltanto per quella vecchia installazione occorre esportare i percorsi GPX prima di disinstallare, installare la nuova versione e reimportarli.

## Altri controlli

- Parsing sintattico di 35 file Java/XML con `java-parser` e `fast-xml-parser`, senza compilare nel container AI. La build finale ha compilato anche le modifiche successive.
- SHA-256 del Gradle Wrapper JAR già confrontato con il checksum ufficiale di Gradle 8.11.1: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
- Distribuzione Gradle fissata a 8.11.1 con SHA-256 ufficiale `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.
- Richieste HTTPS del 12 ottobre 2026 alle due modalità, stesse tappe pubbliche di Milano: Auto `Ok`, 4.716,1 m, 269 punti; A piedi `Ok`, 3.697,2 m, 315 punti. Richieste distanziate di oltre un secondo. Fonti e dettagli in [maps-routing.md](maps-routing.md).

## Limiti della verifica

`scoutsolutions-log.get_build_log(11)` risponde ancora “Build non trovata”. L’esito è verificato con `list_builds`, report JUnit/lint e log Gradle prodotti dall’Executor nella workspace, riferiti alla build corrente. Non sono stati usati log precedenti come prova dell’esito finale e non è stata eseguita una build locale per aggirare il problema MCP.

Dev non configurato e produzione ferma: l’app nativa non richiede un server.

Le verifiche della versione 1.3 su telefono fisico o emulatore **non sono state eseguite**. La checklist comprende UI, salvataggio/ripristino dei valori per percorso, aggiornamento dalla 1.2, nuova icona, scelta del ricalcolo, GPS e gestione batteria: [device-checklist.md](device-checklist.md).

L’utente ha confermato che la versione 1.1 segnala la camminata in Life360 sul suo telefono. È un riscontro dell’utente, non una garanzia universale. La simulazione varia la velocità GPS e la distanza coerentemente, ma non simula accelerometro, giroscopio o bussola. Android non espone alle normali app un’API pubblica per sostituire le letture consegnate a Life360; l’iniezione nel codice AOSP è un’interfaccia di sistema destinata ai test. Fonti in [life360-research.md](life360-research.md).

Le impostazioni per percorso sono conservate nel JSON privato dell’app; l’esportazione GPX conserva geometria, altitudine e timestamp e non include i parametri della simulazione o la modalità di ricalcolo.

## Icona della nuova versione

Generata con lo strumento integrato image_gen, skill imagegen. Originale `output/imagegen/anomaly-gps-icon-v1.3.png`, 1254 × 1254 pixel, 1.218.410 byte, SHA-256 `ebcf8d920b17247c350f8ab144a71ab355ddcedd49ff0610d3ff665a5ea79bce`. Il bitmap è copiato senza modifiche nelle risorse Android e incluso nell’APK. Icona adattiva con margine XML di 10 dp, sfondo scuro e variante vettoriale monocromatica per le icone a tema. Prompt completo in [output/imagegen/README.md](../output/imagegen/README.md).

Lint ignora soltanto `IconLauncherShape` per questo specifico bitmap opaco: è un livello interno dell’icona adattiva, la cui forma esterna è applicata dal launcher. La motivazione è in `app/lint.xml`. La build finale ha zero errori e il solo avviso preesistente sulla dipendenza Google.
