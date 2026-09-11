# CCOP LadeMonitor v6 · Energy History + Device Profiles

## Ziel
Native Android-App zur kontinuierlichen Erfassung der Netto-Ladeleistung des Smartphone-Akkus und – soweit Android/OEM sie freigibt – der externen USB-C-/PD-Quellentelemetrie. Die App darf fehlende Quellwerte niemals durch Akku- oder Profilwerte ersetzen.

## P0-Abnahmekriterien
- Große Hauptkennzahl `LADEGESCHWINDIGKEIT LIVE` aus `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW`.
- Netto-Akkuleistung = echte Akkuspannung × aktueller Akku-Netto-Strom.
- Persistentes Liniendiagramm über Zeit; Unterbrechungen bleiben sichtbar.
- Kumulierte Energie je Ladezyklus und gesamt in Wh/mWh.
- Ladezyklen werden beim Trennen/Wiederanschließen getrennt, Historie bleibt bestehen.
- Geräte-/Quellenprofile für Powerbanks, Netzteile und externe Akkus.
- Profilfelder: Name, Typ, Nennkapazität mAh/Wh, Nennspannung, Start-SOC, Wirkungsgrad, Pass-through-Flag, Notiz und Foto/OCR-Rohtext.
- Powerbank-SOC: direkter Systemwert hat Vorrang; sonst modellbasierte Schätzung aus Start-SOC minus entnommener Energie.
- Manuelle SOC-Validierung mit Differenz zwischen geschätztem und beobachtetem Wert.
- Reset `100 % / neu geladen` pro Profil.
- Gesamte Akkuleiste über Smartphone + alle kapazitätsfähigen Profile als gewichtete Restenergie.
- Aufschlüsselung je Gerät/Powerbank in Wh und %.
- Fotoimport für Typenschild; OCR liest u. a. mAh, Wh, V, A und nutzt die Werte als Profilvorschlag.
- Bericht/Bug-Report exportierbar als JSON; fehlende Messkanäle erzeugen automatisch Entwicklungsaufträge.
- Keine Demo-/Fallback-Werte als LIVE kennzeichnen.

## Datenherkunft
### Smartphone-Akku
- `ACTION_BATTERY_CHANGED`: Ladezustand, Akkuspannung, Temperatur, Plug-Type.
- `BatteryManager`: CURRENT_NOW, CURRENT_AVERAGE, CHARGE_COUNTER.

### Externe Quelle
Best effort über lesbare `/sys/class/power_supply/*`-Felder wie `vbus_voltage`, `ibus_current`, `pd_voltage`, `pd_current`, `voltage_now`, `current_now`, `capacity`, `usb_type`. Werte werden nur angezeigt, wenn der konkrete externe Knoten eindeutig identifiziert und lesbar ist.

### Ladeprofil
`max_charging_voltage` und `max_charging_current` aus Battery-Broadcast sind Profil-/Grenzwerte, keine Live-Messung.

## Historie
- Speicherung lokal als JSONL in App-internem Speicher.
- Während eines Ladezyklus: Messpunkt alle 5 s.
- Außerhalb des Ladezyklus: Statuspunkt bei Zustandswechsel und mindestens alle 60 s, damit Unterbrechungen im Chart sichtbar bleiben.
- Chart-Zeiträume: 15 min, 1 h, 6 h, 24 h, Gesamt.
- Energieintegration über Zeit aus Netto-Akkuleistung.

## Profile / SOC-Modell
Kapazität in Wh = `mAh / 1000 × Nennspannung`.
Geschätzte Restenergie einer Powerbank = letzter validierter SOC × Nennenergie – seitdem geschätzte Quellenenergie.
Wenn direkte Powerbank-Kapazität/SOC aus Android/OEM verfügbar ist, überschreibt der direkte Wert die Schätzung.

## Pass-through
Wenn eine Powerbank gleichzeitig extern nachgeladen und als Quelle genutzt wird, darf eine reine Entnahme-Schätzung nicht als sicherer SOC ausgegeben werden. Profil kann `Pass-through` aktiviert haben; dann wird der SOC als `unsicher / Telemetrie erforderlich` markiert, solange kein direkter Powerbank-SOC vorliegt.

## Reporting
Export enthält:
- Geräte-/App-/Build-Informationen
- aktive Quelle / Profil
- aktuelles Telemetrie-Snapshot
- Ladezyklen und kumulierte Energie
- Profile und SOC-Zustände
- automatische Entwicklungsaufträge für fehlende Signale (z. B. VBUS/IBUS/SOC)
- Benutzer-Notiz

## Datenschutz
Local First. Keine Cloud-Übertragung. Foto/OCR und Messhistorie verbleiben lokal, sofern der Nutzer sie nicht explizit teilt/exportiert.
