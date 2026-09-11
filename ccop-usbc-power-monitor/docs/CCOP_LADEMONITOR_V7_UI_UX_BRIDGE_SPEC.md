# CCOP LadeMonitor v7 · UI/UX, One Choice & Battery Bridge

Status: Spezifikation / Review-Grundlage. Noch keine Umsetzung.

## 1. Ziel
Die nächste Version des CCOP LadeMonitors wird als klar tab-basierte, mobile-first Anwendung aufgebaut. Jede Hauptaufgabe erhält genau eine eigene Ansicht. Messung, Historie, aktives Profil, Ladezyklus und Bridge-Zustand bleiben beim Wechsel der Tabs unverändert erhalten. Es darf keinen Medien-, Daten- oder Kontextbruch geben.

Die App soll nicht nur den Smartphone-Akku betrachten, sondern ein lokales Energie-System aus Smartphone, Powerbanks, externen Akkus und Ladequellen abbilden. Zwei Akkus können als Bridge/Pool gekoppelt und gemeinsam bewertet werden. Alle Prozentwerte müssen nachvollziehbar aus realer oder ausdrücklich als geschätzt markierter Energie abgeleitet werden.

## 2. Verbindliche UI-Grundsätze

### 2.1 Ein Tab = eine Aufgabe = eine Ansicht
- Keine lange Startseite mit allen Funktionen untereinander.
- Keine mehrfach verschachtelten Unterseiten innerhalb eines Tabs.
- Kein Wechsel in eine technisch anders aufgebaute Oberfläche für Profil, Bridge, Verlauf oder Bericht.
- Jeder Tab besitzt eine klare Hauptaufgabe und eine primäre Aktion.
- Sekundäre Details werden inline, als aufklappbare Zeile oder kompakte Bottom-Sheet-Aktion gezeigt, ohne den Tab-Kontext zu verlassen.

### 2.2 Kein Medien-/Kontextbruch
Beim Wechsel zwischen Tabs bleiben zwingend erhalten:
- aktive Messsession,
- aktiver Ladezyklus,
- aktive Ladequelle,
- gewähltes Geräteprofil,
- Bridge-Konfiguration,
- Chart-Zeitraum,
- letzter Live-Messpunkt,
- Historie und Unterbrechungsmarker,
- offene Validierung,
- noch nicht exportierter Bug-/Review-Kontext.

Die Messung darf durch Navigation, Display-Rotation, App-Hintergrund oder Wechsel zwischen Tabs nicht neu gestartet werden.

### 2.3 Jobs-to-be-Done / One Choice
Die Navigation folgt dem Prinzip „Was will ich jetzt tun?“ und nicht „Welche technische Funktion existiert?“.

Pro Ansicht gilt:
- genau eine hervorgehobene Primäraktion,
- maximal zwei sichtbare Sekundäraktionen,
- keine gleichzeitig konkurrierenden Hauptbuttons,
- kontextabhängige Beschriftung der Primäraktion,
- technische Einstellungen nur dort zeigen, wo sie für die aktuelle Aufgabe nötig sind.

Beispiele:
- unbekannte Ladequelle erkannt → Primäraktion: `PROFIL AUSWÄHLEN`,
- aktives Profil vorhanden → Primäraktion: `LIVE ÜBERWACHEN`,
- zwei Akkus gewählt → Primäraktion: `BRIDGE AKTIVIEREN`,
- Messkanal fehlt → Primäraktion: `ENTWICKLUNGSAUFTRAG ERSTELLEN`,
- Powerbank wurde extern vollgeladen → Primäraktion: `AUF 100 % VALIDIEREN`.

## 3. Globaler App-Rahmen
Jede Ansicht verwendet denselben App-Rahmen.

### Kopfbereich
Immer sichtbar:
- `CCOP LadeMonitor`,
- Live-/Offline-Status,
- aktives Profil bzw. `Profil fehlt`,
- aktive Quelle,
- Bridge-Status,
- Gesamtenergie-System-SOC in Prozent,
- letzter Messzeitpunkt.

Der Kopfbereich darf nicht größer als nötig werden. Die aktuelle Hauptkennzahl des jeweiligen Tabs erhält den visuellen Fokus.

### Tab-Leiste
Verbindliche Tabs:
1. `ÜBERSICHT`
2. `LIVE`
3. `PROFILE`
4. `BRIDGE`
5. `VERLAUF`
6. `BERICHT`

Auf kleinen Displays wird die Tab-Leiste horizontal verschiebbar oder als kompakte Icon+Text-Leiste umgesetzt. Es darf kein Hamburger-Menü für diese sechs Kernaufgaben geben.

## 4. Tab ÜBERSICHT

### Zweck
In maximal wenigen Sekunden verstehen:
- wie viel Energie insgesamt verfügbar ist,
- welches Gerät gerade geladen wird,
- welche Quelle aktiv ist,
- welche Akkus Teil des Systems sind,
- ob die Werte gemessen, berechnet oder unbekannt sind.

### Hauptdarstellung
Große Gesamtsystem-Akkuleiste:
- Gesamt-SOC in %, gewichtet nach verfügbarer Energie in Wh,
- Restenergie in Wh,
- Gesamtkapazität in Wh,
- Zahl aktiver Akkus,
- Zahl aktuell verbundener Ladequellen.

Formel:
`Gesamt-SOC = Summe Restenergie aller einbezogenen Akkus / Summe nutzbare Vollenergie aller einbezogenen Akkus × 100`

Ein einfacher Mittelwert der Prozentwerte ist ausdrücklich unzulässig.

### Geräteübersicht
Kompakte Reihen für:
- Smartphone,
- Powerbank 1,
- Powerbank 2,
- weitere Profile,
- aktive Bridge.

Je Gerät nur:
- Name,
- aktueller SOC,
- Rest-Wh,
- Status `lädt / liefert / getrennt / unbekannt`,
- Datenqualität `direkt / berechnet / manuell validiert`.

### Primäraktion
`QUELLE / PROFIL AUSWÄHLEN`

## 5. Tab LIVE

### Zweck
Aktuelle elektrische Situation ohne Ablenkung beobachten.

### Hauptkennzahl
`NETTOLEISTUNG ZUM AKKU`
- Watt groß,
- darunter Spannung und Strom,
- Lade-/Entladerichtung eindeutig,
- Datenquelle sichtbar.

### Live-Chart
Vollbreites Liniendiagramm über Zeit.

Mindestens darstellbar:
- Nettoleistung W,
- Strom A/mA,
- Spannung V,
- Smartphone-SOC %, 
- optional Quell-SOC %, wenn verfügbar.

Eigenschaften:
- kontinuierliche Zeitachse,
- Unterbrechungen bleiben als Unterbrechung/Gap sichtbar,
- Wiederanschluss beginnt keinen neuen visuellen Datensatz,
- Ladezyklusgrenzen werden markiert,
- Profilwechsel werden markiert,
- Bridge-Aktivierung/-Deaktivierung wird markiert,
- Zoom/Zeitraumwahl ohne Verlust der Messsession.

### Profilwahl direkt im Live-Kontext
Unter der Hauptkennzahl steht ein kompakter Profil-Chip:
`Quelle: [Profilname ▼]`

Tippen öffnet die Profilauswahl, ohne den Live-Tab zu verlassen. Auswahlmöglichkeiten:
- vorhandenes Profil,
- neues Profil,
- aus Foto erkennen,
- `unbekannte Quelle`,
- Bridge als Quelle.

### Primäraktion
Bei Messung: `LIVE-MESSUNG HALTEN`
Bei fehlendem Profil: `PROFIL AUSWÄHLEN`
Bei fehlender Telemetrie: `MESSKANAL PRÜFEN`

## 6. Tab PROFILE

### Zweck
Geräte- und Ladequellen sauber identifizieren, kalibrieren und wiederverwenden.

### Profiltypen
- Smartphone-Akku,
- Powerbank,
- externer Akku,
- Netzteil,
- USB-C-/PD-Ladegerät,
- Solar-/sonstige Quelle,
- Bridge-Profil.

### Profilkarte
Jede Karte zeigt:
- Foto,
- Name/Modell,
- Typ,
- Nennkapazität mAh und/oder Wh,
- Nennspannung,
- bekannte Ladeprofile V/A/W,
- Wirkungsgrad,
- letzter validierter SOC,
- letzter Einsatz,
- Datenqualität.

### Foto/OCR
`FOTO IMPORTIEREN` analysiert lokal mindestens:
- Hersteller/Modelltext,
- mAh,
- Wh,
- V,
- A,
- W,
- USB-PD-/QC-Angaben, soweit lesbar.

OCR-Ergebnisse sind Vorschläge und müssen bestätigt werden. Ein Foto darf nie automatisch ein Profil endgültig überschreiben.

### Auswahl
Ein Profil kann von hier als `AKTIVE QUELLE`, `AKKU A` oder `AKKU B` gesetzt werden.

### Primäraktion
`PROFIL AUSWÄHLEN / ERSTELLEN`

## 7. Tab BRIDGE

### Zweck
Zwei Akkus oder Powerbanks als gemeinsames Energiesystem betrachten und – soweit technisch messbar – Energiefluss zwischen ihnen und dem Verbraucher darstellen.

### Bridge-Auswahl
Zwei große Auswahlfelder:
- `AKKU A [Profil auswählen]`
- `AKKU B [Profil auswählen]`

Optionaler Verbraucher:
- `ZIELGERÄT: Smartphone / anderes Profil`

Dasselbe Profil darf nicht gleichzeitig A und B sein.

### Bridge-Modi
Die UI unterscheidet klar:
- `POOL` – beide Akkus bilden einen gemeinsamen rechnerischen Energiepool,
- `SEQUENZ` – Akku A wird bevorzugt, Akku B als Reserve,
- `PASS-THROUGH` – mindestens ein Akku wird gleichzeitig geladen und entladen,
- `DIREKTE BRIDGE` – nur wenn echte Telemetrie für beide Seiten vorhanden ist.

Diese Modi beschreiben zunächst die logische Darstellung. Die App darf daraus keine nicht gemessene physische Stromrichtung erfinden.

### Bridge-Kennzahlen
- Gesamt-SOC A+B,
- gesamte nutzbare Restenergie Wh,
- Restenergie A,
- Restenergie B,
- Quelle → A/B → Zielgerät als Flussdiagramm,
- gemessene oder geschätzte Leistung je Richtung,
- kumulierte Energie pro Seite,
- Wirkungsgrad/Verlust, sofern bestimmbar,
- Status `direkt gemessen / modelliert / unvollständig`.

### Gewichtete Berechnung
`Bridge-SOC = (Rest-Wh A + Rest-Wh B) / (Voll-Wh A + Voll-Wh B) × 100`

### Validierung
Für jeden Akku einzeln:
- beobachteten SOC eingeben,
- `100 % / neu geladen`,
- Messpunkt als Referenz speichern,
- Differenz Modell ↔ beobachtet anzeigen.

### Primäraktion
`BRIDGE AKTIVIEREN`
Danach: `BRIDGE ÜBERWACHEN`

## 8. Tab VERLAUF

### Zweck
Alle Lade- und Entladevorgänge über längere Zeit nachvollziehen, ohne dass Trennen/Wiederanschließen Daten zerstört.

### Zeitbereiche
- 15 min,
- 1 h,
- 6 h,
- 24 h,
- 7 Tage,
- 30 Tage,
- Gesamt.

### Ereignisse im Chart
- Laden gestartet,
- Laden gestoppt,
- Quelle getrennt,
- Quelle wieder verbunden,
- Profil geändert,
- manueller SOC validiert,
- Akku auf 100 % gesetzt,
- Bridge aktiviert/deaktiviert,
- Pass-through erkannt/markiert,
- fehlender Messkanal.

### Zykluskarte
Je Zyklus:
- Start/Ende,
- Dauer,
- Profil/Quelle,
- Start-/End-SOC,
- integrierte Energie Wh,
- Durchschnitts-/Peak-Leistung,
- Unterbrechungsdauer,
- Datenvollständigkeit.

### Primäraktion
`ZYKLUS VERGLEICHEN`

## 9. Tab BERICHT

### Zweck
Messung, Fehler, fehlende Daten und Entwicklungsbedarf direkt als nachvollziehbaren Auftrag exportieren.

### Berichtstypen
- Messbericht,
- Ladezyklus-Bericht,
- Profilbericht,
- Bridge-Bericht,
- Bug-Report,
- Entwicklungsauftrag.

### Automatisch enthalten
- App-/Build-Version,
- Android-/Geräteinformationen,
- aktiver Tab,
- aktive Profile,
- Bridge-Konfiguration,
- aktueller Snapshot,
- verwendete Datenquellen,
- fehlende Datenkanäle,
- Chart-Zeitraum,
- relevante Historie,
- Ladezyklen,
- SOC-Validierungen,
- OCR-Rohdaten, sofern vom Nutzer gewählt,
- Benutzer-Notiz.

### Entwicklungsauftrag aus fehlender Funktion
Fehlt eine gewünschte Kennzahl oder ist ein Chart nicht vollständig, kann der Nutzer direkt `ALS ENTWICKLUNGSAUFTRAG` wählen.

Automatische Felder:
- Erwartung,
- Ist-Zustand,
- betroffene Ansicht,
- betroffener Messkanal,
- Geräte-/Profilkontext,
- Priorität P0/P1/P2,
- reproduzierbare Schritte,
- Datenbeispiel,
- gewünschtes Abnahmekriterium.

### Primäraktion
`BERICHT EXPORTIEREN`

## 10. Datenmodell

### DeviceProfile
Pflichtfelder:
- id,
- name,
- type,
- manufacturer,
- model,
- nominalMah,
- nominalWh,
- nominalVoltage,
- maxVoltage,
- maxCurrent,
- maxPower,
- efficiency,
- passThroughCapable,
- photoUri,
- ocrRawText,
- lastValidatedSoc,
- lastValidationTime,
- notes.

### BatteryState
- profileId,
- timestamp,
- soc,
- remainingWh,
- voltage,
- current,
- power,
- temperature,
- sourceQuality,
- sourceChannel,
- confidence.

### BridgeProfile
- id,
- name,
- batteryAProfileId,
- batteryBProfileId,
- targetProfileId,
- mode,
- active,
- createdAt,
- lastActivatedAt.

### MeasurementPoint
- timestamp,
- cycleId,
- profileId,
- bridgeId,
- voltage,
- current,
- power,
- soc,
- eventType,
- sourceQuality.

## 11. Datenqualität und Kennzeichnung
Jeder Messwert erhält sichtbar oder intern eine Herkunft:
- `DIRECT` – direkt vom System/Sensor,
- `OEM` – OEM-/Sysfs-Telemetrie,
- `PROFILE` – aus Geräteprofil,
- `MODELLED` – berechnet,
- `MANUAL` – manuell validiert,
- `UNAVAILABLE` – nicht vorhanden.

`PROFILE` oder `MODELLED` dürfen niemals als `LIVE DIREKT` dargestellt werden.

## 12. UX für unbekannte Ladequelle
Wird eine neue Quelle erkannt:
1. Live-Messung läuft weiter.
2. Die App zeigt keinen blockierenden Dialog.
3. Im Kopf und Live-Tab erscheint `Profil fehlt`.
4. Primäraktion wird `PROFIL AUSWÄHLEN`.
5. Profilauswahl zeigt zuletzt verwendete Profile zuerst.
6. Der Nutzer kann Foto/OCR starten oder ein neues Profil anlegen.
7. Nach Auswahl wird die laufende Session mit einem `Profilwechsel`-Marker fortgesetzt; sie wird nicht neu begonnen.

## 13. Persistenz und Hintergrundbetrieb
- Messdienst unabhängig von UI-Navigation.
- Lokale persistente Speicherung.
- Tab-Wechsel verändert keine Messung.
- App-Neustart stellt letzte Session und aktiven Kontext wieder her.
- Bei Trennung der Ladequelle bleibt der Chart bestehen.
- Wiederanschluss erzeugt einen Ereignismarker, keinen Daten-Reset.
- Bridge-Zustand wird separat gespeichert und nach Neustart wiederhergestellt, aber physische Verbindung muss erneut erkannt/validiert werden.

## 14. Mobile-First Layout
- Portrait zuerst.
- Keine zweispaltigen Desktop-Layouts auf Smartphone erzwingen.
- Pro Tab ein vertikaler Hauptfluss.
- Hauptkennzahl im oberen Drittel.
- Chart über nahezu volle Displaybreite.
- Touch-Ziele mindestens ca. 48 dp.
- Keine abgeschnittenen Einheiten oder Werte.
- Keine verschachtelten Scrollflächen im Chart.
- Landscape darf mehr Breite nutzen, aber dieselbe Informationsarchitektur behalten.

## 15. Abnahmekriterien v7
Eine v7-Umsetzung gilt erst als akzeptiert, wenn:
- alle sechs Hauptaufgaben als getrennte Tabs existieren,
- jeder Tab genau eine Hauptaufgabe und eine Primäraktion besitzt,
- Live-Messung beim Tab-Wechsel nicht neu startet,
- aktives Profil von Live und Profile aus auswählbar ist,
- Profile als Quelle, Akku A und Akku B auswählbar sind,
- zwei verschiedene Akkus zu einer Bridge verbunden werden können,
- Bridge-SOC energiegewichtet und nicht als einfacher Prozentmittelwert berechnet wird,
- Gesamtsystem-SOC Smartphone + ausgewählte externe Akkus korrekt in Wh gewichtet wird,
- Unterbrechungen und Wiederanschlüsse im Verlauf sichtbar bleiben,
- Profilwechsel und Bridge-Ereignisse im Chart markiert sind,
- Foto/OCR ein Profil vorschlagen kann,
- manuelle SOC-Validierung pro Akku möglich ist,
- direkte und berechnete Werte eindeutig unterscheidbar sind,
- fehlende Messkanäle direkt als Entwicklungsauftrag exportiert werden können,
- Bericht/Bug-Report den aktuellen UI-, Profil-, Bridge- und Messkontext enthält,
- keine Demo-/Fallback-Zahl als echte Live-Telemetrie erscheint.

## 16. Nicht-Ziele / Sicherheitsgrenzen
- Die App kann keine physische Parallel-/Serienschaltung von Akkus herstellen.
- Eine „Bridge“ ist zunächst eine logisch überwachte Kopplung/Pool-Darstellung und nur dann eine direkte Energieflussmessung, wenn die Hardware/Sensorik die nötigen Werte wirklich liefert.
- Aus USB-PD-Profilgrenzen dürfen keine Live-Werte erfunden werden.
- Pass-through-SOC bleibt als unsicher markiert, wenn weder direkter SOC noch getrennte Ein-/Ausgangsenergie vorliegt.

## 17. Nächster Umsetzungsschritt
Erst nach Review/Freigabe dieser Spezifikation:
1. aktuelle v6 UI gegen die Abnahmekriterien vergleichen,
2. konkrete P0/P1-Lücken dokumentieren,
3. Tab-App-Shell und persistenten Session-State implementieren,
4. Profilwahl vereinheitlichen,
5. Bridge-Datenmodell und UI implementieren,
6. Charts und Historie auf gemeinsame Zeitachse umstellen,
7. Reporting/Development-Task-Export ergänzen,
8. anschließend APK bauen und auf realem Gerät testen.
