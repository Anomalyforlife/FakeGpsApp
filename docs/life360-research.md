# Posizione e attività: indagine del 12 ottobre 2026

## Cosa sappiamo

L'utente vede oscillazioni in Life360, mentre Google Maps mantiene il punto simulato. Questo non prova che Android ripristini il GPS reale né che Life360 rifiuti la posizione perché il telefono non cammina. Non abbiamo log o accesso al dispositivo, e Life360 non pubblica il suo algoritmo completo.

[Life360 Drive Detection & Analysis](https://support.life360.com/hc/en-us/articles/23053499870487-Drive-Detection-Analysis) descrive un rilevamento che combina posizione e attività. Le [impostazioni dei permessi Life360](https://support.life360.com/hc/en-us/articles/23053398402839-Life360-Permission-Settings) collegano i dati di movimento all'analisi della guida. La documentazione non conferma un ritorno alla posizione reale in assenza di passi.

La pagina specifica [Movement Icons](https://support.life360.com/hc/en-us/articles/23053397194903-Movement-Icons) collega le icone anche alla velocità. Descrive camminata/corsa a 0–4+ mph, bici a 0–7+ mph e guida a 0–50+ mph. Le fasce sono sovrapposte e il simbolo “+” non definisce limiti esclusivi. Il profilo da 5 km/h è coerente con la fascia indicata per camminata/corsa: potrebbe influire sull'icona, ma questo va verificato con il GPS simulato sul telefono. Life360 non documenta in questa pagina un obbligo di passi reali e avverte che le icone possono essere inesatte. Non bisogna trasferire automaticamente le condizioni del rilevamento guida al riconoscimento delle icone di camminata.

[Movement Accuracy](https://support.life360.com/hc/en-us/articles/23053466420887-Movement-Accuracy) descrive anche dipendenze da connessione e attività in background. Sono possibili fattori da verificare, non una diagnosi delle oscillazioni segnalate.

## Lacuna corretta nella versione 1.1

La versione 1.0 usava `LocationManager.addTestProvider` anche con un provider chiamato `fused`, senza attivare la modalità mock di Google Play Services. Le due API sono distinte.

Secondo [FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient), `setMockMode(true)` svuota la cache e limita i fix di FLP a quelli forniti con `setMockLocation`. La modalità riguarda anche client di altri processi e deve essere disattivata al termine. L'app adesso abilita FLP quando Google Play Services è disponibile, attende la conferma sul worker e invia lo stesso fix anche ai provider Android. In sua assenza resta disponibile il backend Android, dichiarato nella diagnostica. Non si può garantire che Life360 usi esclusivamente FLP o che questa modifica risolva le oscillazioni osservate.

Ogni fix contiene coordinate, altitudine, velocità in m/s, direzione, precisioni e timestamp coerenti tra i backend. Gli errori temporanei di consegna vengono mostrati e ritentati mantenendo il punto; tre errori consecutivi interrompono la sessione con un messaggio. La revoca dei permessi interrompe subito. Un provider Android rimosso viene ricreato usando lo stesso punto. Arresto, errore e distruzione ordinata disattivano la modalità mock FLP e rimuovono i provider; un errore di ripristino viene segnalato.

## Limite dei profili di movimento

[ActivityRecognitionClient](https://developers.google.com/android/reference/com/google/android/gms/location/ActivityRecognitionClient) ricava l'attività da campioni dei sensori. Le API pubbliche consentono di ricevere questi risultati; non espongono un comando per impostare l'attività globale che ricevono le altre app. [DetectedActivity](https://developers.google.com/android/reference/com/google/android/gms/location/DetectedActivity) comprende camminata, corsa, bicicletta e veicolo, senza categorie distinte per moto e automobile.

I profili A piedi/Bici/Moto/Auto di Anomaly GPS regolano la velocità GPS: dalla versione 1.2 includono intervalli modificabili con variazioni graduali e un’opzione Personalizzata. Non simulano accelerometro, passi o attività fisica e non garantiscono le etichette di Life360. Non cambiano la geometria già importata; dalla 1.3 il ricalcolo Auto/A piedi si sceglie separatamente durante l’importazione. Le posizioni mantengono il normale flag mock Android.

## Prova necessaria sul telefono

Confrontare una posizione statica e un GPX continuo, prima con schermo acceso poi spento. Se Life360 salta, acquisire dalla diagnostica modello/versione Android, backend, ultimo invio confermato e eventuale errore; annotare se il salto riguarda un punto precedente o la posizione reale e se la notifica resta presente. Una conferma di invio attesta l'accettazione da parte dei servizi Android/Google, non la ricezione o visualizzazione in Life360. Verificare anche rete e autorizzazioni di Life360 secondo la sua documentazione. Non serve cambiare permessi dei sensori per provare la correzione GPS.

## Feedback e richiesta sensori

L'utente ha confermato che con la versione 1.1 Life360 ha iniziato a indicare la camminata sul suo telefono. È un risultato riferito dall'utente, non una garanzia su altri dispositivi.

Per accelerometro, giroscopio e bussola, le API pubbliche permettono la lettura dei sensori. Il [codice ufficiale Android di SensorManager](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/hardware/SensorManager.java) contrassegna `injectSensorData` come `@SystemApi`, nascosto e riservato ai test, non alle applicazioni di terze parti. Nell'APK ordinario non è quindi disponibile una modalità per sostituire le letture globali ricevute da Life360. Non è stata aggiunta un'opzione che finga di poterlo fare. La documentazione Life360 non prova quali di questi sensori usi per ogni singola icona.

La versione 1.2 varia gradualmente la velocità GPS entro intervalli configurabili, con una distanza coerente con tale velocità; non altera i sensori né nasconde il flag mock.
