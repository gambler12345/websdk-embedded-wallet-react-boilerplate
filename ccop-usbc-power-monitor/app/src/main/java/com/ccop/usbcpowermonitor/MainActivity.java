package com.ccop.usbcpowermonitor;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8,12,16), PANEL = Color.rgb(17,23,29), LINE = Color.rgb(43,56,68);
    private static final int TEXT = Color.rgb(247,249,252), MUTED = Color.rgb(151,164,178), OK = Color.rgb(72,229,139);
    private static final int WARN = Color.rgb(255,210,105), BLUE = Color.rgb(117,164,255), RED = Color.rgb(255,114,114);
    private static final int REQ_PHOTO = 1001, REQ_EXPORT_REPORT = 2001, REQ_EXPORT_BUG = 2002;

    private final java.util.Map<String, TextView> fields = new java.util.LinkedHashMap<>();
    private ProfileStore profileStore;
    private List<ProfileStore.Profile> profiles = new ArrayList<>();
    private ProfileStore.Profile editingProfile;

    private TextView speedBig, speedSub, liveOrigin, sourceStatus, sourceSocText, aggregateText, phoneSocText;
    private TextView profileStatus, ocrStatus, bugStatus, breakdownText, chartRangeLabel;
    private ProgressBar aggregateBar, phoneBar, sourceBar;
    private HistoryChartView powerChart, socChart;
    private Spinner profileSpinner, typeSpinner;
    private EditText nameEdit, capacityEdit, nominalVEdit, socEdit, efficiencyEdit, profileMaxVEdit, profileMaxAEdit, noteEdit, reportNoteEdit;
    private CheckBox passthroughCheck;

    private long chartRangeMs = 60L * 60L * 1000L;
    private long lastChartRefresh = 0L;
    private JSONObject lastTelemetry = new JSONObject();
    private double lastSourceDirectSoc = Double.NaN;
    private String pendingExportContent = "";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (TelemetryService.ACTION_TELEMETRY.equals(intent.getAction())) updateFromTelemetry(intent);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        profileStore = new ProfileStore(this);
        setContentView(buildUi());
        reloadProfiles(profileStore.getActiveId());
        startMonitor();
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 901); } catch (Throwable ignored) { }
        }
        refreshCharts();
        updateBreakdown(Double.NaN);
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(TelemetryService.ACTION_TELEMETRY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override protected void onPause() {
        try { unregisterReceiver(receiver); } catch (Throwable ignored) { }
        super.onPause();
    }

    private void startMonitor() {
        Intent i = new Intent(this, TelemetryService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void stopMonitor() {
        Intent i = new Intent(this, TelemetryService.class).setAction(TelemetryService.ACTION_STOP);
        startService(i);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(28), dp(14), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("CCOP LadeMonitor · ENERGY HISTORY v6", 23, TEXT, true));
        TextView intro = text("Live-Ladeleistung · persistente Historie · Geräteprofile · Powerbank-SOC · Foto/OCR · Reports", 12, MUTED, false);
        intro.setPadding(0, dp(4), 0, dp(12));
        root.addView(intro);

        LinearLayout hero = card();
        hero.addView(title("LADEGESCHWINDIGKEIT · LIVE"));
        speedBig = text("— mA", 46, OK, true);
        speedBig.setPadding(0, dp(7), 0, dp(2));
        hero.addView(speedBig);
        speedSub = text("— A · — W netto zum Akku", 18, TEXT, true);
        hero.addView(speedSub);
        liveOrigin = text("Warte auf BatteryManager-Livewert…", 11, MUTED, false);
        liveOrigin.setPadding(0, dp(5), 0, dp(10));
        hero.addView(liveOrigin);
        metric(hero, "batteryVoltage", "Akkuspannung · LIVE");
        metric(hero, "batteryPct", "Smartphone-SOC");
        metric(hero, "cycleId", "Ladezyklus");
        metric(hero, "plugType", "Aktive Quelle");
        root.addView(hero, mb());

        LinearLayout total = card();
        total.addView(title("AKKU-GESAMTLEISTE · ALLE GERÄTE"));
        aggregateText = text("Gesamt: — %", 27, WARN, true);
        total.addView(aggregateText);
        aggregateBar = progressBar();
        total.addView(aggregateBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
        phoneSocText = text("Smartphone: — %", 14, TEXT, true);
        phoneSocText.setPadding(0, dp(10), 0, dp(3));
        total.addView(phoneSocText);
        phoneBar = progressBar();
        total.addView(phoneBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        sourceSocText = text("Aktive Quelle: —", 14, TEXT, true);
        sourceSocText.setPadding(0, dp(10), 0, dp(3));
        total.addView(sourceSocText);
        sourceBar = progressBar();
        total.addView(sourceBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        breakdownText = text("Profile werden geladen…", 12, MUTED, false);
        breakdownText.setPadding(0, dp(10), 0, 0);
        total.addView(breakdownText);
        root.addView(total, mb());

        LinearLayout charts = card();
        charts.addView(title("LIVE-VERLAUF · LEISTUNG UND SOC"));
        chartRangeLabel = text("Zeitraum: 1 Stunde", 12, MUTED, true);
        chartRangeLabel.setPadding(0, dp(4), 0, dp(6));
        charts.addView(chartRangeLabel);
        LinearLayout ranges = new LinearLayout(this);
        ranges.setOrientation(LinearLayout.HORIZONTAL);
        addRangeButton(ranges, "15m", 15L * 60L * 1000L);
        addRangeButton(ranges, "1h", 60L * 60L * 1000L);
        addRangeButton(ranges, "6h", 6L * 60L * 60L * 1000L);
        addRangeButton(ranges, "24h", 24L * 60L * 60L * 1000L);
        addRangeButton(ranges, "ALLE", 0L);
        charts.addView(ranges);
        TextView ptitle = text("Netto-Akkuleistung / Quellenleistung · W", 12, TEXT, true);
        ptitle.setPadding(0, dp(12), 0, dp(4));
        charts.addView(ptitle);
        powerChart = new HistoryChartView(this, HistoryChartView.Mode.POWER);
        charts.addView(powerChart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        TextView stitle = text("SOC-Verlauf · Smartphone / aktive Quelle / Gesamt", 12, TEXT, true);
        stitle.setPadding(0, dp(12), 0, dp(4));
        charts.addView(stitle);
        socChart = new HistoryChartView(this, HistoryChartView.Mode.SOC);
        charts.addView(socChart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        TextView gap = text("Rote vertikale Marker = Start/Stop eines Ladezyklus. Historie bleibt über Unterbrechungen und App-Neustarts erhalten.", 11, MUTED, false);
        gap.setPadding(0, dp(8), 0, 0);
        charts.addView(gap);
        root.addView(charts, mb());

        LinearLayout energy = card();
        energy.addView(title("ENERGIE · ZEIT · ZYKLEN"));
        metric(energy, "cycleNetWh", "Netto in Akku · aktueller Zyklus");
        metric(energy, "cycleSourceWh", "Quelle · aktueller Zyklus");
        metric(energy, "totalNetWh", "Netto in Akku · Gesamt");
        metric(energy, "totalSourceWh", "Quelle · Gesamt geschätzt/gemessen");
        metric(energy, "historyCount", "Historische Messpunkte");
        metric(energy, "chargeCounterMah", "Android Charge Counter");
        root.addView(energy, mb());

        LinearLayout source = card();
        source.addView(title("AKTIVE QUELLE · LIVE / PROFIL"));
        sourceStatus = text("Warte auf Quellendaten…", 12, MUTED, true);
        sourceStatus.setPadding(0, dp(4), 0, dp(8));
        source.addView(sourceStatus);
        metric(source, "sourceV", "USB-C/VBUS Spannung · LIVE");
        metric(source, "sourceA", "USB-C/IBUS Strom · LIVE");
        metric(source, "sourceW", "Quellenleistung");
        metric(source, "sourceType", "USB/PD Typ");
        metric(source, "profileMax", "Android Profil / MAX");
        root.addView(source, mb());

        LinearLayout profileCard = card();
        profileCard.addView(title("GERÄTE- UND QUELLENPROFILE"));
        profileSpinner = new Spinner(this);
        profileCard.addView(profileSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        profileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < profiles.size()) loadProfileIntoForm(profiles.get(position));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        nameEdit = edit("Name / Modell");
        profileCard.addView(nameEdit);
        typeSpinner = new Spinner(this);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"POWERBANK", "EXTERNAL_BATTERY", "CHARGER"});
        typeSpinner.setAdapter(typeAdapter);
        profileCard.addView(typeSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        LinearLayout row1 = horizontal();
        capacityEdit = edit("Kapazität mAh"); nominalVEdit = edit("Nennspannung V");
        row1.addView(capacityEdit, half()); row1.addView(nominalVEdit, halfRight()); profileCard.addView(row1);
        LinearLayout row2 = horizontal();
        socEdit = edit("SOC %"); efficiencyEdit = edit("Wirkungsgrad %");
        row2.addView(socEdit, half()); row2.addView(efficiencyEdit, halfRight()); profileCard.addView(row2);
        LinearLayout row3 = horizontal();
        profileMaxVEdit = edit("Profil max V"); profileMaxAEdit = edit("Profil max A");
        row3.addView(profileMaxVEdit, half()); row3.addView(profileMaxAEdit, halfRight()); profileCard.addView(row3);
        noteEdit = edit("Profil-Notiz"); profileCard.addView(noteEdit);
        passthroughCheck = new CheckBox(this);
        passthroughCheck.setText("Pass-through / Netz + Powerbank gleichzeitig");
        passthroughCheck.setTextColor(TEXT);
        profileCard.addView(passthroughCheck);
        LinearLayout pbuttons1 = horizontal();
        Button save = button("PROFIL SPEICHERN"); save.setOnClickListener(v -> saveProfileFromForm(false));
        Button active = button("ALS QUELLE AKTIV"); active.setOnClickListener(v -> saveProfileFromForm(true));
        pbuttons1.addView(save, halfButton()); pbuttons1.addView(active, halfButtonRight()); profileCard.addView(pbuttons1);
        LinearLayout pbuttons2 = horizontal();
        Button validate = button("SOC VALIDIEREN"); validate.setOnClickListener(v -> validateSoc());
        Button reset = button("100% · NEU GELADEN"); reset.setOnClickListener(v -> resetSourceSoc());
        pbuttons2.addView(validate, halfButton()); pbuttons2.addView(reset, halfButtonRight()); profileCard.addView(pbuttons2);
        LinearLayout pbuttons3 = horizontal();
        Button newP = button("NEUES PROFIL"); newP.setOnClickListener(v -> newProfile());
        Button deleteP = button("PROFIL LÖSCHEN"); deleteP.setOnClickListener(v -> deleteProfile());
        pbuttons3.addView(newP, halfButton()); pbuttons3.addView(deleteP, halfButtonRight()); profileCard.addView(pbuttons3);
        profileStatus = text("Profil bereit", 11, MUTED, false);
        profileStatus.setPadding(0, dp(8), 0, 0);
        profileCard.addView(profileStatus);
        root.addView(profileCard, mb());

        LinearLayout photo = card();
        photo.addView(title("FOTOIMPORT · TYPENSCHILD / MODELL"));
        TextView ph = text("Foto auswählen → OCR liest Name, mAh, Wh, V und A als Profilvorschlag. Originaltext bleibt lokal im Profil.", 12, MUTED, false);
        ph.setPadding(0, dp(4), 0, dp(8)); photo.addView(ph);
        Button importPhoto = button("FOTO IMPORTIEREN + OCR"); importPhoto.setOnClickListener(v -> choosePhoto()); photo.addView(importPhoto);
        ocrStatus = text("Noch kein Foto eingelesen", 11, MUTED, false); ocrStatus.setPadding(0, dp(8), 0, 0); photo.addView(ocrStatus);
        root.addView(photo, mb());

        LinearLayout report = card();
        report.addView(title("REPORTING · BUG-REPORT · ENTWICKLUNGSAUFTRAG"));
        bugStatus = text("Automatische Prüfung läuft…", 12, MUTED, false); bugStatus.setPadding(0, dp(4), 0, dp(8)); report.addView(bugStatus);
        reportNoteEdit = edit("Notiz / Ergänzung für Bericht"); report.addView(reportNoteEdit);
        LinearLayout rbuttons = horizontal();
        Button export = button("BERICHT EXPORTIEREN"); export.setOnClickListener(v -> exportReport(false));
        Button bug = button("BUG-REPORT EXPORT"); bug.setOnClickListener(v -> exportReport(true));
        rbuttons.addView(export, halfButton()); rbuttons.addView(bug, halfButtonRight()); report.addView(rbuttons);
        LinearLayout monitorButtons = horizontal();
        Button start = button("MONITOR START"); start.setOnClickListener(v -> startMonitor());
        Button stop = button("MONITOR STOP"); stop.setOnClickListener(v -> stopMonitor());
        monitorButtons.addView(start, halfButton()); monitorButtons.addView(stop, halfButtonRight()); report.addView(monitorButtons);
        Button clear = button("VERLAUF LÖSCHEN"); clear.setOnClickListener(v -> { HistoryStore.clear(this); refreshCharts(); });
        report.addView(clear);
        root.addView(report, mb());

        TextView footer = text("Local First · Hintergrund-Monitoring · keine Cloud · keine Demo-Livewerte", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER); root.addView(footer);
        return scroll;
    }

    private void updateFromTelemetry(Intent i) {
        try {
            lastTelemetry = intentToJson(i);
            boolean charging = i.getBooleanExtra("charging", false);
            double ma = getDouble(i, "batteryMa");
            double a = getDouble(i, "batteryA");
            double w = getDouble(i, "batteryW");
            double bv = getDouble(i, "batteryV");
            double phone = getDouble(i, "phonePct");
            double agg = getDouble(i, "aggregateSoc");
            double sourceSoc = getDouble(i, "sourceSoc");
            lastSourceDirectSoc = getDouble(i, "sourceDirectSoc");

            speedBig.setText(!Double.isNaN(ma) ? String.format(Locale.GERMANY, "%.0f mA", Math.abs(ma)) : "— mA");
            speedBig.setTextColor(charging ? OK : WARN);
            speedSub.setText((!Double.isNaN(a) ? String.format(Locale.GERMANY, "%.3f A", Math.abs(a)) : "— A") + " · " +
                    (!Double.isNaN(w) ? String.format(Locale.GERMANY, "%.2f W netto zum Akku", Math.abs(w)) : "— W netto zum Akku"));
            liveOrigin.setText(charging ? "BatteryManager CURRENT_NOW · echte Netto-Akkuseite" : "Aktuell kein aktiver Ladezyklus · Verlauf bleibt erhalten");
            set("batteryVoltage", !Double.isNaN(bv) ? fmt(bv, 3) + " V" : "—", !Double.isNaN(bv) ? OK : MUTED);
            set("batteryPct", !Double.isNaN(phone) ? fmt(phone, 0) + " %" : "—", TEXT);
            set("cycleId", "#" + i.getIntExtra("cycleId", 0), BLUE);
            set("plugType", i.getStringExtra("plugType") != null ? i.getStringExtra("plugType") : "—", charging ? OK : MUTED);

            set("cycleNetWh", fmt(i.getDoubleExtra("cycleNetWh", 0.0), 4) + " Wh", TEXT);
            set("cycleSourceWh", fmt(i.getDoubleExtra("cycleSourceWh", 0.0), 4) + " Wh", TEXT);
            set("totalNetWh", fmt(i.getDoubleExtra("totalNetWh", 0.0), 3) + " Wh", TEXT);
            set("totalSourceWh", fmt(i.getDoubleExtra("totalSourceWh", 0.0), 3) + " Wh", TEXT);
            set("historyCount", String.valueOf(i.getLongExtra("historyCount", 0)), TEXT);
            double cc = getDouble(i, "chargeCounterMah"); set("chargeCounterMah", !Double.isNaN(cc) ? fmt(cc, 0) + " mAh" : "—", TEXT);

            double sv = getDouble(i, "sourceV"), sa = getDouble(i, "sourceA");
            double swExact = getDouble(i, "sourceExactW"), swEff = getDouble(i, "sourceEffectiveW");
            boolean estimated = i.getBooleanExtra("sourcePowerEstimated", false);
            set("sourceV", !Double.isNaN(sv) ? fmt(sv, 3) + " V · LIVE" : "nicht freigegeben", !Double.isNaN(sv) ? OK : RED);
            set("sourceA", !Double.isNaN(sa) ? fmt(sa, 3) + " A · LIVE" : "nicht freigegeben", !Double.isNaN(sa) ? OK : RED);
            set("sourceW", !Double.isNaN(swExact) ? fmt(swExact, 2) + " W · LIVE" : (!Double.isNaN(swEff) ? "≈ " + fmt(swEff, 2) + " W · aus Netto/η" : "—"), !Double.isNaN(swExact) ? OK : WARN);
            set("sourceType", i.getStringExtra("sourceType") != null ? i.getStringExtra("sourceType") : "nicht gemeldet", BLUE);
            double mv = getDouble(i, "profileMaxV"), mA = getDouble(i, "profileMaxA");
            set("profileMax", (!Double.isNaN(mv) && !Double.isNaN(mA)) ? fmt(mv, 1) + " V × " + fmt(mA, 1) + " A = " + fmt(mv * mA, 1) + " W MAX" : "—", BLUE);
            sourceStatus.setText(!Double.isNaN(sv) ? "Direkte Quellentelemetrie aktiv" : (estimated ? "Quellenseite gesperrt · Quellenleistung als Modellwert aus Netto-Akkuleistung/Wirkungsgrad" : "Quellenseite nicht verfügbar"));
            sourceStatus.setTextColor(!Double.isNaN(sv) ? OK : WARN);

            if (!Double.isNaN(agg)) {
                aggregateText.setText("Gesamt: " + fmt(agg, 1) + " %");
                aggregateBar.setProgress((int) Math.round(agg * 10));
            }
            if (!Double.isNaN(phone)) {
                phoneSocText.setText("Smartphone: " + fmt(phone, 1) + " %");
                phoneBar.setProgress((int) Math.round(phone * 10));
            }
            String activeName = i.getStringExtra("profileName");
            if (!Double.isNaN(sourceSoc)) {
                sourceSocText.setText((activeName != null ? activeName : "Aktive Quelle") + ": " + fmt(sourceSoc, 1) + " %" + (!Double.isNaN(lastSourceDirectSoc) ? " · DIREKT" : " · MODELL"));
                sourceBar.setProgress((int) Math.round(sourceSoc * 10));
            } else {
                sourceSocText.setText((activeName != null ? activeName : "Aktive Quelle") + ": kein Kapazitäts-SOC");
                sourceBar.setProgress(0);
            }

            updateBreakdown(phone);
            updateBugStatus(i);
            long now = System.currentTimeMillis();
            if (now - lastChartRefresh > 4500L) { refreshCharts(); lastChartRefresh = now; }
        } catch (Throwable t) {
            bugStatus.setText("UI-Telemetriefehler: " + t.getClass().getSimpleName());
            bugStatus.setTextColor(RED);
        }
    }

    private void refreshCharts() {
        long since = chartRangeMs == 0 ? 0 : System.currentTimeMillis() - chartRangeMs;
        List<HistoryStore.Point> pts = HistoryStore.readSince(this, since, 2500);
        if (powerChart != null) powerChart.setPoints(pts);
        if (socChart != null) socChart.setPoints(pts);
    }

    private void addRangeButton(LinearLayout row, String label, long rangeMs) {
        Button b = button(label);
        b.setOnClickListener(v -> {
            chartRangeMs = rangeMs;
            chartRangeLabel.setText("Zeitraum: " + (rangeMs == 0 ? "Gesamt" : label));
            refreshCharts();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(0, 0, dp(5), 0);
        row.addView(b, lp);
    }

    private void reloadProfiles(String selectId) {
        profiles = profileStore.load();
        List<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < profiles.size(); i++) {
            ProfileStore.Profile p = profiles.get(i);
            names.add(p.name + (p.id.equals(profileStore.getActiveId()) ? " · AKTIV" : ""));
            if (p.id.equals(selectId)) selected = i;
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        profileSpinner.setAdapter(a);
        if (!profiles.isEmpty()) profileSpinner.setSelection(Math.min(selected, profiles.size() - 1));
    }

    private void loadProfileIntoForm(ProfileStore.Profile p) {
        editingProfile = p;
        nameEdit.setText(p.name);
        capacityEdit.setText(fmtPlain(p.capacityMah));
        nominalVEdit.setText(fmtPlain(p.nominalV));
        socEdit.setText(fmtPlain(p.estimatedSoc));
        efficiencyEdit.setText(fmtPlain(p.efficiency * 100.0));
        profileMaxVEdit.setText(p.profileMaxVoltage > 0 ? fmtPlain(p.profileMaxVoltage) : "");
        profileMaxAEdit.setText(p.profileMaxCurrent > 0 ? fmtPlain(p.profileMaxCurrent) : "");
        noteEdit.setText(p.note);
        passthroughCheck.setChecked(p.passthrough);
        selectType(p.type);
        profileStatus.setText("Wh: " + fmt(p.capacityWh(), 2) + " · SOC-Modell: " + fmt(p.estimatedSoc, 1) + "% · Validierungsabweichung: " + fmt(p.lastValidationDelta, 1) + " %-Pkt.");
        ocrStatus.setText(p.ocrText == null || p.ocrText.isEmpty() ? "Kein OCR-Text gespeichert" : "OCR im Profil vorhanden · " + Math.min(9999, p.ocrText.length()) + " Zeichen");
    }

    private void saveProfileFromForm(boolean activate) {
        if (editingProfile == null) editingProfile = new ProfileStore.Profile();
        editingProfile.name = valueOr(nameEdit.getText().toString().trim(), "Powerbank");
        editingProfile.type = typeSpinner.getSelectedItem() != null ? typeSpinner.getSelectedItem().toString() : "POWERBANK";
        editingProfile.capacityMah = parse(capacityEdit.getText().toString(), editingProfile.capacityMah);
        editingProfile.nominalV = parse(nominalVEdit.getText().toString(), editingProfile.nominalV);
        editingProfile.estimatedSoc = clamp(parse(socEdit.getText().toString(), editingProfile.estimatedSoc), 0, 100);
        editingProfile.efficiency = clamp(parse(efficiencyEdit.getText().toString(), editingProfile.efficiency * 100.0) / 100.0, 0.5, 1.0);
        editingProfile.profileMaxVoltage = parse(profileMaxVEdit.getText().toString(), 0.0);
        editingProfile.profileMaxCurrent = parse(profileMaxAEdit.getText().toString(), 0.0);
        editingProfile.note = noteEdit.getText().toString();
        editingProfile.passthrough = passthroughCheck.isChecked();
        profileStore.saveProfile(editingProfile);
        if (activate) profileStore.setActiveId(editingProfile.id);
        reloadProfiles(editingProfile.id);
        profileStatus.setText(activate ? "Gespeichert und als aktive Quelle gesetzt" : "Profil gespeichert");
    }

    private void newProfile() {
        editingProfile = new ProfileStore.Profile();
        editingProfile.name = "Neue Powerbank";
        loadProfileIntoForm(editingProfile);
        profileStatus.setText("Neues Profil · noch nicht gespeichert");
    }

    private void deleteProfile() {
        if (editingProfile == null) return;
        String id = editingProfile.id;
        profileStore.deleteProfile(id);
        editingProfile = null;
        reloadProfiles(profileStore.getActiveId());
        profileStatus.setText("Profil gelöscht");
    }

    private void validateSoc() {
        if (editingProfile == null) return;
        double observed = clamp(parse(socEdit.getText().toString(), editingProfile.estimatedSoc), 0, 100);
        double before = editingProfile.estimatedSoc;
        editingProfile.lastValidationDelta = observed - before;
        editingProfile.lastValidatedSoc = observed;
        editingProfile.estimatedSoc = observed;
        editingProfile.lastValidationAt = System.currentTimeMillis();
        profileStore.saveProfile(editingProfile);
        reloadProfiles(editingProfile.id);
        profileStatus.setText("SOC validiert: " + fmt(observed, 1) + "% · Abweichung zum Modell: " + fmt(editingProfile.lastValidationDelta, 1) + " %-Pkt.");
    }

    private void resetSourceSoc() {
        if (editingProfile == null) return;
        editingProfile.estimatedSoc = 100.0;
        editingProfile.lastValidatedSoc = 100.0;
        editingProfile.lastValidationAt = System.currentTimeMillis();
        editingProfile.cumulativeSourceWh = 0.0;
        editingProfile.lastValidationDelta = 0.0;
        profileStore.saveProfile(editingProfile);
        socEdit.setText("100");
        reloadProfiles(editingProfile.id);
        profileStatus.setText("Quelle als vollständig neu geladen markiert · 100%");
    }

    private void updateBreakdown(double phonePct) {
        StringBuilder sb = new StringBuilder();
        double phoneWh = profileStore.getPhoneCapacityMah() / 1000.0 * profileStore.getPhoneNominalV();
        if (!Double.isNaN(phonePct)) sb.append("Smartphone: ").append(fmt(phonePct,1)).append("% · ≈ ").append(fmt(phoneWh * phonePct / 100.0, 2)).append("/").append(fmt(phoneWh,2)).append(" Wh\n");
        for (ProfileStore.Profile p : profileStore.load()) {
            if ("CHARGER".equalsIgnoreCase(p.type)) {
                sb.append(p.name).append(": Netzteil/Ladegerät · keine gespeicherte Akku-Kapazität\n");
                continue;
            }
            double full = p.capacityWh();
            double rem = full * clamp(p.estimatedSoc, 0, 100) / 100.0;
            sb.append(p.name).append(": ").append(fmt(p.estimatedSoc,1)).append("% · ").append(fmt(rem,2)).append("/").append(fmt(full,2)).append(" Wh");
            if (p.passthrough) sb.append(" · PASS-THROUGH");
            if (p.id.equals(profileStore.getActiveId())) sb.append(" · AKTIV");
            sb.append('\n');
        }
        breakdownText.setText(sb.toString().trim());
    }

    private void choosePhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_PHOTO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_PHOTO) {
            Uri uri = data.getData();
            if (uri == null) return;
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Throwable ignored) { }
            runOcr(uri);
        } else if (requestCode == REQ_EXPORT_REPORT || requestCode == REQ_EXPORT_BUG) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os != null) { os.write(pendingExportContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)); os.close(); }
                bugStatus.setText("Export gespeichert"); bugStatus.setTextColor(OK);
            } catch (Exception e) {
                bugStatus.setText("Exportfehler: " + e.getMessage()); bugStatus.setTextColor(RED);
            }
        }
    }

    private void runOcr(Uri uri) {
        ocrStatus.setText("OCR läuft…");
        try {
            InputImage img = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(img)
                    .addOnSuccessListener(result -> {
                        String raw = result.getText();
                        if (editingProfile == null) editingProfile = new ProfileStore.Profile();
                        editingProfile.photoUri = uri.toString();
                        editingProfile.ocrText = raw;
                        applyOcrToForm(raw);
                        profileStore.saveProfile(editingProfile);
                        reloadProfiles(editingProfile.id);
                        ocrStatus.setText("OCR erfolgreich · Werte als Profilvorschlag übernommen");
                        ocrStatus.setTextColor(OK);
                        recognizer.close();
                    })
                    .addOnFailureListener(e -> {
                        ocrStatus.setText("OCR fehlgeschlagen: " + e.getClass().getSimpleName());
                        ocrStatus.setTextColor(RED);
                        recognizer.close();
                    });
        } catch (Exception e) {
            ocrStatus.setText("Foto konnte nicht gelesen werden: " + e.getMessage());
            ocrStatus.setTextColor(RED);
        }
    }

    private void applyOcrToForm(String raw) {
        if (raw == null) return;
        String normalized = raw.replace(',', '.');
        Matcher mah = Pattern.compile("(?i)(\\d{3,6})\\s*mAh").matcher(normalized);
        if (mah.find()) capacityEdit.setText(mah.group(1));
        Matcher wh = Pattern.compile("(?i)(\\d{1,4}(?:\\.\\d{1,3})?)\\s*Wh").matcher(normalized);
        double foundWh = wh.find() ? parse(wh.group(1), 0.0) : 0.0;
        Matcher volts = Pattern.compile("(?i)(\\d{1,2}(?:\\.\\d{1,3})?)\\s*V").matcher(normalized);
        if (volts.find()) {
            double v = parse(volts.group(1), 0.0);
            if (v >= 3.0 && v <= 4.5) nominalVEdit.setText(fmtPlain(v));
            else if (v > 4.5 && v <= 30) profileMaxVEdit.setText(fmtPlain(v));
        }
        Matcher amps = Pattern.compile("(?i)(\\d{1,2}(?:\\.\\d{1,3})?)\\s*A(?:mp)?").matcher(normalized);
        if (amps.find()) profileMaxAEdit.setText(amps.group(1));
        if (foundWh > 0 && (capacityEdit.getText().toString().trim().isEmpty() || parse(capacityEdit.getText().toString(), 0) <= 0)) {
            double v = parse(nominalVEdit.getText().toString(), 3.7);
            if (v > 0) capacityEdit.setText(fmtPlain(foundWh / v * 1000.0));
        }
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String x = line.trim();
            if (x.length() >= 3 && x.length() <= 55 && !x.toLowerCase(Locale.ROOT).contains("mah") && !x.toLowerCase(Locale.ROOT).contains("wh")) {
                if (nameEdit.getText().toString().trim().isEmpty() || nameEdit.getText().toString().startsWith("Neue")) nameEdit.setText(x);
                break;
            }
        }
        editingProfile.name = valueOr(nameEdit.getText().toString().trim(), editingProfile.name);
        editingProfile.capacityMah = parse(capacityEdit.getText().toString(), editingProfile.capacityMah);
        editingProfile.nominalV = parse(nominalVEdit.getText().toString(), editingProfile.nominalV);
        editingProfile.profileMaxVoltage = parse(profileMaxVEdit.getText().toString(), editingProfile.profileMaxVoltage);
        editingProfile.profileMaxCurrent = parse(profileMaxAEdit.getText().toString(), editingProfile.profileMaxCurrent);
    }

    private void updateBugStatus(Intent i) {
        List<String> bugs = currentBugList(i);
        if (bugs.isEmpty()) {
            bugStatus.setText("Self-Test: keine aktuellen P0/P1-Datenlücken erkannt");
            bugStatus.setTextColor(OK);
        } else {
            StringBuilder sb = new StringBuilder("Automatische Entwicklungsaufträge:\n");
            for (String b : bugs) sb.append("• ").append(b).append('\n');
            bugStatus.setText(sb.toString().trim());
            bugStatus.setTextColor(WARN);
        }
    }

    private List<String> currentBugList(Intent i) {
        ArrayList<String> b = new ArrayList<>();
        if (Double.isNaN(getDouble(i, "sourceV"))) b.add("P0 · Externe VBUS-Spannung wird vom OEM/Android-Pfad nicht freigegeben.");
        if (Double.isNaN(getDouble(i, "sourceA"))) b.add("P0 · Externer IBUS-/USB-Strom wird nicht direkt freigegeben.");
        ProfileStore.Profile p = profileStore.getActive();
        if (p != null && !"CHARGER".equalsIgnoreCase(p.type) && Double.isNaN(getDouble(i, "sourceDirectSoc"))) b.add("P1 · Powerbank-SOC nicht direkt übertragen; Modellschätzung aktiv.");
        if (p != null && p.passthrough && Double.isNaN(getDouble(i, "sourceDirectSoc"))) b.add("P0 · Pass-through aktiv ohne direkten Quellen-SOC; Restmenge ist nicht sicher ableitbar.");
        if (p != null && !"CHARGER".equalsIgnoreCase(p.type) && p.capacityWh() <= 0) b.add("P1 · Profilkapazität fehlt; Restenergie kann nicht berechnet werden.");
        return b;
    }

    private void exportReport(boolean bugOnly) {
        try {
            JSONObject report = buildReport(bugOnly);
            pendingExportContent = report.toString(2);
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/json");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.GERMANY).format(new Date());
            i.putExtra(Intent.EXTRA_TITLE, bugOnly ? "CCOP-LadeMonitor-BugReport-" + stamp + ".json" : "CCOP-LadeMonitor-Report-" + stamp + ".json");
            startActivityForResult(i, bugOnly ? REQ_EXPORT_BUG : REQ_EXPORT_REPORT);
        } catch (Exception e) {
            bugStatus.setText("Report konnte nicht erstellt werden: " + e.getMessage());
            bugStatus.setTextColor(RED);
        }
    }

    private JSONObject buildReport(boolean bugOnly) throws Exception {
        JSONObject r = new JSONObject();
        r.put("schema", "ccop-lademonitor-v6-report");
        r.put("type", bugOnly ? "bug-development-order" : "full-energy-report");
        r.put("createdAt", System.currentTimeMillis());
        r.put("createdAtText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(new Date()));
        JSONObject device = new JSONObject();
        device.put("manufacturer", Build.MANUFACTURER);
        device.put("model", Build.MODEL);
        device.put("device", Build.DEVICE);
        device.put("sdk", Build.VERSION.SDK_INT);
        device.put("androidId", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        r.put("device", device);
        r.put("telemetry", lastTelemetry);
        r.put("historyPoints", HistoryStore.count(this));
        r.put("note", reportNoteEdit.getText().toString());
        JSONArray pa = new JSONArray();
        for (ProfileStore.Profile p : profileStore.load()) pa.put(p.toJson());
        r.put("profiles", pa);
        r.put("activeProfileId", profileStore.getActiveId());
        JSONArray bugs = new JSONArray();
        Intent fake = jsonToIntent(lastTelemetry);
        for (String b : currentBugList(fake)) bugs.put(b);
        r.put("developmentOrders", bugs);
        JSONObject acceptance = new JSONObject();
        acceptance.put("liveBatteryCurrent", !Double.isNaN(optTelemetryDouble("batteryMa")));
        acceptance.put("liveBatteryPower", !Double.isNaN(optTelemetryDouble("batteryW")));
        acceptance.put("sourceVbus", !Double.isNaN(optTelemetryDouble("sourceV")));
        acceptance.put("sourceIbus", !Double.isNaN(optTelemetryDouble("sourceA")));
        acceptance.put("sourceSocDirect", !Double.isNaN(optTelemetryDouble("sourceDirectSoc")));
        acceptance.put("persistentHistory", HistoryStore.count(this) > 0);
        r.put("selfTest", acceptance);
        return r;
    }

    private Intent jsonToIntent(JSONObject j) {
        Intent i = new Intent();
        java.util.Iterator<String> it = j.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = j.opt(k);
            if (v instanceof Number) i.putExtra(k, ((Number) v).doubleValue());
            else if (v instanceof Boolean) i.putExtra(k, (Boolean) v);
            else if (v != null) i.putExtra(k, String.valueOf(v));
        }
        return i;
    }

    private double optTelemetryDouble(String k) {
        return lastTelemetry.has(k) ? lastTelemetry.optDouble(k, Double.NaN) : Double.NaN;
    }

    private JSONObject intentToJson(Intent i) {
        JSONObject o = new JSONObject();
        Bundle b = i.getExtras();
        if (b != null) {
            for (String k : b.keySet()) {
                Object v = b.get(k);
                try { o.put(k, JSONObject.wrap(v)); } catch (Exception ignored) { }
            }
        }
        return o;
    }

    private double getDouble(Intent i, String key) {
        return i.hasExtra(key) ? i.getDoubleExtra(key, Double.NaN) : Double.NaN;
    }

    private ProgressBar progressBar() {
        ProgressBar p = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(1000);
        p.setProgress(0);
        return p;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setSingleLine(true);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(10, 16, 21));
        gd.setCornerRadius(dp(11));
        gd.setStroke(dp(1), LINE);
        e.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(5), 0, dp(5));
        e.setLayoutParams(lp);
        return e;
    }

    private void metric(LinearLayout parent, String key, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView l = text(label, 13, MUTED, false);
        TextView v = text("—", 14, TEXT, true);
        v.setGravity(Gravity.END);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f));
        fields.put(key, v);
        parent.addView(row);
    }

    private void set(String key, String value, int color) {
        TextView t = fields.get(key);
        if (t != null) { t.setText(value); t.setTextColor(color); }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(PANEL);
        gd.setCornerRadius(dp(24));
        gd.setStroke(dp(1), LINE);
        c.setBackground(gd);
        return c;
    }

    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams half() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1f); lp.setMargins(0, dp(4), dp(4), dp(4)); return lp; }
    private LinearLayout.LayoutParams halfRight() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1f); lp.setMargins(dp(4), dp(4), 0, dp(4)); return lp; }
    private LinearLayout.LayoutParams halfButton() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f); lp.setMargins(0, dp(5), dp(4), dp(5)); return lp; }
    private LinearLayout.LayoutParams halfButtonRight() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f); lp.setMargins(dp(4), dp(5), 0, dp(5)); return lp; }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(24,32,40));
        gd.setCornerRadius(dp(14));
        gd.setStroke(dp(1), LINE);
        b.setBackground(gd);
        return b;
    }

    private TextView title(String s) { TextView t = text(s, 12, MUTED, true); t.setLetterSpacing(0.11f); return t; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setLineSpacing(0,1.08f); return t; }
    private LinearLayout.LayoutParams mb() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,0,0,dp(12)); return lp; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void selectType(String type) {
        for (int i = 0; i < typeSpinner.getCount(); i++) if (typeSpinner.getItemAtPosition(i).toString().equalsIgnoreCase(type)) { typeSpinner.setSelection(i); return; }
    }

    private double parse(String s, double fallback) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return fallback; }
    }
    private String fmt(double v, int d) { return String.format(Locale.GERMANY, "%." + d + "f", v); }
    private String fmtPlain(double v) { return String.format(Locale.US, "%.2f", v).replaceAll("\\.?0+$", ""); }
    private String valueOr(String s, String f) { return s == null || s.trim().isEmpty() ? f : s; }
    private double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
}
