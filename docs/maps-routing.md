# Importazione Maps: Auto e A piedi

La versione 1.3 chiede la modalità prima di geocodificare e ricalcolare le tappe. La scelta vale anche per un link incollato o per tappe manuali; il profilo di velocità della simulazione rimane separato. Le tracce già salvate non vengono ricalcolate.

## Servizi scelti

Si usano i grafi OSRM distinti indicati nel [frontend ufficiale FOSSGIS](https://github.com/fossgis-routing-server/osrm-frontend/blob/master/src/leaflet_options.js):

| Modalità | Base della richiesta |
| --- | --- |
| Auto | `https://routing.openstreetmap.de/routed-car/route/v1/driving/` |
| A piedi | `https://routing.openstreetmap.de/routed-foot/route/v1/driving/` |

Il grafo è preparato con il profilo del server: cambiare soltanto il token `driving` nell’URL non sceglie un grafo pedonale. La modalità dipende da `routed-car` oppure `routed-foot`, come descritto dal concetto di profilo statico nell’[API OSRM](https://project-osrm.org/docs/v5.24.0/api/#general-options).

Il [gestore documenta](https://routing.openstreetmap.de/about.html) copertura mondiale per auto e piedi, limite di una richiesta al secondo e assenza di garanzie di disponibilità. L’app spazia le richieste di ricalcolo e usa un User-Agent identificativo. Il JSON viene validato prima del salvataggio: un errore o l’assenza di un itinerario pedonale non attiva un fallback auto.

## Prova HTTPS del 12 ottobre 2026

Richieste sequenziali distanziate di oltre un secondo, stesse tappe pubbliche di Milano: `45.4642,9.1900` → `45.4780,9.2270`. Opzioni `overview=full&geometries=geojson&steps=false`.

| Modalità | HTTP / codice | Distanza | Durata stimata dal servizio | Punti |
| --- | --- | ---: | ---: | ---: |
| Auto | 200 / Ok | 4.716,1 m | 637,4 s | 269 |
| A piedi | 200 / Ok | 3.697,2 m | 2.957,2 s | 315 |

È una verifica del servizio esterno, non una prova dell’interfaccia sul telefono. La durata del servizio non regola la simulazione: valgono le impostazioni di velocità dell’app.

## Salvataggio e compatibilità

La geometria e `routingProfile` (`car`/`foot`) vengono salvati nel JSON privato della traccia. La modalità appare nella descrizione della sorgente e viene mantenuta con rinomina e modifica delle impostazioni. “Apri in Maps” usa `travelmode=walking` per le nuove tracce pedonali; trasferisce solo gli estremi e lascia a Google il proprio ricalcolo.

Le tracce precedenti o i GPX senza modalità restano leggibili. Il GPX esporta la geometria, senza parametri di ricalcolo o simulazione. Il ricalcolo OSRM non garantisce la geometria originariamente proposta da Google Maps.
