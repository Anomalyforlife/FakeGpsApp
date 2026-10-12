# Verifica su dispositivo Android

Da eseguire dopo una build riuscita, installando l’APK debug. Nessuna di queste prove è dichiarata eseguita automaticamente.

## Permessi e avvio

- Prima installazione: guida visibile, schede navigabili, UI senza sovrapposizioni alle barre di sistema.
- GPS spento: avvio rifiutato con collegamento alle impostazioni.
- App mock non selezionata: avviso e apertura delle Opzioni sviluppatore.
- Posizione negata o solo approssimativa: richiesta di posizione precisa; nessun crash.
- Notifiche negate su Android 13+: la sessione resta controllabile dall’app; concederle rende visibile la notifica.
- Avvio ripetuto rapido: una sola sessione.

## Posizione statica

- Scegli un punto sulla mappa e attiva la simulazione. Verifica latitudine/longitudine in un’app GPS di prova.
- Modifica altitudine; verifica valore del provider.
- Passa a un’altra app e spegni lo schermo per alcuni minuti: verifica la posizione al ritorno.
- Termina dalla notifica; controlla che il provider reale torni disponibile.
- Revoca il permesso durante una sessione: arresto e messaggio, nessun provider mock mantenuto intenzionalmente.
- Su un dispositivo con Google Play Services: diagnostica indica entrambi i backend e l'ultimo invio confermato resta recente; confronta Maps e Life360 per almeno dieci minuti. Annotare eventuali salti di Life360, senza assumere che dipendano dall'attività fisica.
- Su un dispositivo senza Google Play Services: diagnostica indica il solo backend Android e la posizione rimane disponibile tramite LocationManager.
- Termina e avvia una registrazione reale: anche il client Google FLP deve tornare a coordinate reali, senza cache mock mantenuta.
- Disconnessione temporanea del servizio di posizione: avviso, tentativi limitati allo stesso punto; dopo tre fallimenti, arresto con motivo. Revoca dell'autorizzazione mock: arresto immediato.

## Percorso

- Importa un GPX continuo con partenza e arrivo distinti; confronta la traccia visualizzata.
- Imposta Personalizzata 36 km/h con intervallo 35–37. Dopo la rampa iniziale, in 10 secondi devono essere percorsi circa 97–103 m, compatibilmente con aggiornamento e scheduling Android; durante la partenza la distanza è minore.
- Metti in pausa: posizione mantenuta, velocità mock zero. Riprendi senza ritorno alla partenza.
- Cambia velocità a sessione attiva; controlla che il percorso prosegua dallo stesso punto.
- Profili A piedi/Bici/Moto/Auto: modificano la velocità, non impongono categorie alle altre app né cambiano la geometria importata.
- Versione 1.2: cursore sostituito dalle opzioni A piedi/Bici/Moto/Auto/Personalizzata. Verifica valori decimali con punto e virgola, range modificabile, rifiuto di minimo > riferimento, massimo >300, valori non numerici o range costante nonzero.
- A regime, osserva variazioni graduali nel range e nella velocità GPS ricevuta. Durante partenza/ripresa/cambi di profilo sono previste transizioni fuori range; pausa e arrivo fermo devono inviare zero.
- Percorso A: salva Personalizzata 5,3 km/h, range 4,7–6,1, arrivo Ripeti. Percorso B: Bici, range 15–21, arrivo Resta fermo. Alterna A/B, rinomina, chiudi e riapri l'app: ciascuno conserva i propri valori.
- Un vecchio percorso senza impostazioni resta leggibile: al primo avvio eredita l'ultima velocità globale della versione precedente, poi la salva sul percorso.
- Mentre simuli A, seleziona B e modifica il suo range: A non deve cambiare velocità o posizione.
- Installa 1.2 sopra 1.1 senza disinstallare: archivio e preferenze devono restare presenti.
- Installa 1.3 sopra 1.2 senza disinstallare: percorsi e impostazioni restano presenti; il launcher mostra la nuova icona, leggibile con maschere circolari e quadrate arrotondate.
- Su un launcher che supporta le icone a tema, attivale: deve comparire la variante monocromatica con freccia e percorso. Verifica anche il caricamento dell’icona adattiva su Android 8–12.
- Velocità zero: sessione attiva ma posizione ferma.
- Fine “Resta fermo”: posizione finale mantenuta e velocità zero fino a Termina.
- Fine “Termina”: ultimo fix alla destinazione e rimozione provider simulati.
- Fine “Ripeti”: ritorno alla partenza esplicitamente previsto.

## Importazione e archivio

- Condividi da Google Maps un itinerario con estremi e un link abbreviato. Verifica conferma ricalcolo OSRM e nome percorso.
- All’importazione, verifica la scelta Auto/A piedi anche con link incollato e tappe manuali. Annullare la scelta o il nome non deve creare un percorso.
- Importa le stesse tappe in Auto e A piedi in un’area con passaggi pedonali: controlla le diverse geometrie e la modalità nei dettagli salvati. Chiudi e riapri: la modalità e l’ultima scelta di importazione restano disponibili.
- “Apri in Maps” da una nuova traccia pedonale deve proporre la modalità a piedi; i percorsi precedenti e GPX restano utilizzabili.
- Cambiare il profilo di velocità dopo l’importazione non deve ricalcolare la geometria. Un errore del servizio pedonale deve essere mostrato senza passare automaticamente al percorso auto.
- Un link a un solo luogo deve fallire con istruzioni utili.
- Rete assente durante il ricalcolo: errore visibile e nessun percorso inventato.
- GPX malformato, con DTD, più segmenti, coordinate non finite, meno di due punti o oltre i limiti: importazione rifiutata.
- Rinomina, seleziona, esporta, reimporta GPX: coordinate e altitudini conservate; timestamp delle registrazioni conservati.
- Ruota il telefono mentre il selettore di esportazione è aperto e completa l’esportazione.
- Elimina una traccia: scompare dall’archivio; il percorso attivo non si può eliminare.

## Registrazione reale

- Termina tutte le simulazioni e avvia una registrazione all’aperto.
- Muoviti per almeno 50 m: conteggio e distanza devono aumentare, con coordinate reali.
- Una posizione marcata mock deve essere ignorata con avviso.
- GPS senza segnale: stato di attesa/perdita segnale e nessun punto inventato.
- Termina dopo almeno due fix distinti: traccia nell’archivio, esportabile e riproducibile.
- Termina senza fix: messaggio “nessuna traccia salvata”.
- Dopo più di dieci secondi e due fix, termina brutalmente il processo: l’ultimo salvataggio completato deve essere recuperabile nell’archivio, senza riavvio automatico della sessione.

## Dispositivi da coprire

- Android 8–12: API minima, provider GPS e notifica foreground.
- Android 13: permesso notifiche.
- Android 14–15 o superiore: servizio foreground location e restrizioni permessi.
- Almeno un dispositivo con gestione batteria aggressiva (Samsung/Xiaomi/altro).
