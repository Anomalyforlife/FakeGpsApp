---
name: life360-movement-icons-source
description: Precisazione ufficiale Life360: icone di movimento legate anche alla velocità, passi reali non documentati come obbligatori
type: reference
---

Fonte primaria verificata12ott2026 https://support.life360.com/hc/en-us/articles/23053397194903-Movement-Icons : Life360 descrive icone quando membri si muovono a certe velocità, walking/running 0–4+mph, biking0–7+mph, driving0–50+mph; range sovrapposti e '+' non soglie esclusive. Avvisa icone talvolta inesatte in base alla velocità. Non pubblica algoritmo completo né dichiara passi fisici obbligatori per icona camminata. Quindi profilo5kmh è coerente con fascia camminata, può influire sull'icona ma non garantisce classificazione con GPSmock; provarefisicamente. Distinguere icone da DriveDetection basato posizione/attività, e da Android ActivityRecognition sensor-based: NON dedurre che Life360 richieda sensori/passi per camminata soltanto da docsDriveDetection o Android. Utente informato della precisazione, correggendo possibile implicazione precedente. Ulteriore fonte https://support.life360.com/hc/en-us/articles/23053466420887-Movement-Accuracy descrive dipendenza rete/background e segnali ogni2s durantevisualizzazione, non conferma causaoscillazioni.
