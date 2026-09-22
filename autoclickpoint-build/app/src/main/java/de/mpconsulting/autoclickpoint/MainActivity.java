package de.mpconsulting.autoclickpoint;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private TextView status;
    private TextView diagnostics;
    private Button accessibilityButton;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(0f, 1.10f);
        return v;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        root.setBackgroundColor(Color.WHITE);
        root.setGravity(Gravity.TOP);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("AUTOCLICK\nPOINT", 32, Color.rgb(18, 18, 20));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView lead = text("FIX oder RANDOM · optional mit Bildwächter am Klickpunkt.", 18, Color.DKGRAY);
        LinearLayout.LayoutParams leadLp = new LinearLayout.LayoutParams(-1, -2);
        leadLp.setMargins(0, dp(10), 0, dp(16));
        root.addView(lead, leadLp);

        status = text("Status wird geprüft …", 16, Color.rgb(150, 40, 40));
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, 0, 0, dp(8));
        root.addView(status, statusLp);

        diagnostics = text("", 13, Color.DKGRAY);
        LinearLayout.LayoutParams diagLp = new LinearLayout.LayoutParams(-1, -2);
        diagLp.setMargins(0, 0, 0, dp(18));
        root.addView(diagnostics, diagLp);

        TextView setupTitle = text("Android 13–15 · Freigabe", 18, Color.rgb(18, 18, 20));
        setupTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(setupTitle);

        String restrictedInfo = Build.VERSION.SDK_INT >= 33
                ? "1  BEDIENUNGSHILFEN öffnen und AutoClick Point aktivieren.\n"
                  + "2  Falls Android eine eingeschränkte Einstellung meldet, diese über die Systemeinstellungen freigeben.\n"
                  + "3  Danach hierher zurückkehren."
                : "1  BEDIENUNGSHILFEN öffnen\n"
                  + "2  AutoClick Point aktivieren\n"
                  + "3  zur gewünschten App wechseln";
        TextView instructions = text(restrictedInfo, 15, Color.rgb(38, 38, 42));
        LinearLayout.LayoutParams instructionsLp = new LinearLayout.LayoutParams(-1, -2);
        instructionsLp.setMargins(0, dp(8), 0, dp(18));
        root.addView(instructions, instructionsLp);

        accessibilityButton = button("BEDIENUNGSHILFEN ÖFFNEN / TESTEN");
        accessibilityButton.setOnClickListener(v -> openAccessibilitySettings());
        LinearLayout.LayoutParams accessibilityLp = new LinearLayout.LayoutParams(-1, dp(58));
        accessibilityLp.setMargins(0, 0, 0, dp(10));
        root.addView(accessibilityButton, accessibilityLp);

        Button systemSettings = button("SYSTEMEINSTELLUNGEN ÖFFNEN");
        systemSettings.setOnClickListener(v -> openSystemSettings());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(-1, dp(58));
        settingsLp.setMargins(0, 0, 0, dp(10));
        root.addView(systemSettings, settingsLp);

        Button checkAgain = button("STATUS ERNEUT PRÜFEN");
        checkAgain.setOnClickListener(v -> refreshStatus());
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(-1, dp(54));
        checkLp.setMargins(0, 0, 0, dp(20));
        root.addView(checkAgain, checkLp);

        TextView behavior = text(
                "Neu in v1.0.7 · Bildwächter: Zielpunkt auf das gewünschte Feld setzen und im Pop-up BILD LERNEN wählen. "
                        + "Der kleine Bereich um den Punkt wird als lokale Referenz gelernt. Mit BILD AN wird vor jedem Klick frisch geprüft: "
                        + "passt das Bild, wird geklickt; passt es nicht, steht WARTET AUF BILD und es wird nicht geklickt. "
                        + "Sobald das Referenzbild wieder erscheint, setzt die Klickserie automatisch fort.",
                14,
                Color.rgb(38, 38, 42)
        );
        LinearLayout.LayoutParams behaviorLp = new LinearLayout.LayoutParams(-1, -2);
        behaviorLp.setMargins(0, 0, 0, dp(18));
        root.addView(behavior, behaviorLp);

        root.addView(text(
                "Bedienung im Pop-up: ↕ ZIEHEN verschiebt das Menü, ✕ BEENDEN schließt Pop-up und Zielpunkt. "
                        + "FIX oder RANDOM steuert das Timing. BILD LERNEN / BILD AN aktiviert die visuelle Sicherheitsprüfung. "
                        + "Wird der rote Zielpunkt danach verschoben, muss die Bildreferenz neu gelernt werden.",
                15,
                Color.rgb(38, 38, 42)
        ));

        TextView timingNote = text(
                "Hinweis: Android begrenzt Accessibility-Screenshots. Mit aktivem Bildwächter wird deshalb nur nach einer frischen Bildprüfung geklickt; "
                        + "der effektive Klickabstand beträgt in diesem Modus mindestens ungefähr 0,4 Sekunden. Ohne Bildwächter bleiben die schnellen Intervalle verfügbar.",
                13,
                Color.rgb(120, 75, 0)
        );
        LinearLayout.LayoutParams timingLp = new LinearLayout.LayoutParams(-1, -2);
        timingLp.setMargins(0, dp(16), 0, 0);
        root.addView(timingNote, timingLp);

        TextView privacy = text(
                "Lokal und ohne Internet-Berechtigung. Für den optionalen Bildwächter nimmt der Accessibility-Dienst kurz einen Bildschirmframe auf, "
                        + "wertet ausschließlich den kleinen Bereich um den Klickpunkt aus und speichert nur eine kompakte Bildsignatur. "
                        + "Screenshots werden nicht gespeichert oder übertragen.",
                13,
                Color.GRAY
        );
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
        privacyLp.setMargins(0, dp(18), 0, 0);
        root.addView(privacy, privacyLp);

        setContentView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AutoClickAccessibilityService.requestShowOverlays(this);
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && !isChangingConfigurations()) {
            AutoClickAccessibilityService.requestHideOverlays(this);
        }
        super.onDestroy();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            openSystemSettings();
        }
    }

    private void openSystemSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    private void refreshStatus() {
        boolean installed = isServiceDeclared();
        boolean enabled = isAccessibilityServiceEnabled();

        if (enabled) {
            status.setText("✓ Bedienungshilfe ist AKTIV");
            status.setTextColor(Color.rgb(20, 120, 65));
            accessibilityButton.setText("✓ BEDIENUNGSHILFE AKTIV");
            AutoClickAccessibilityService.requestShowOverlays(this);
        } else {
            status.setText("● Systemfreigabe noch NICHT aktiv");
            status.setTextColor(Color.rgb(150, 40, 40));
            accessibilityButton.setText("BEDIENUNGSHILFEN ÖFFNEN / TESTEN");
        }

        String device = Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE;
        diagnostics.setText((installed ? "✓ Klickdienst ist in der APK registriert" : "✕ Klickdienst wurde vom System nicht gefunden")
                + "\n" + device
                + (enabled ? "\n✓ Android hat den Dienst freigegeben" : "\n● Android-Freigabe steht noch aus"));
        diagnostics.setTextColor(installed ? Color.DKGRAY : Color.rgb(150, 40, 40));
    }

    private boolean isServiceDeclared() {
        try {
            ComponentName component = new ComponentName(this, AutoClickAccessibilityService.class);
            getPackageManager().getServiceInfo(component, PackageManager.GET_META_DATA);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;

        List<AccessibilityServiceInfo> enabledServices =
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            String packageName = info.getResolveInfo().serviceInfo.packageName;
            String className = info.getResolveInfo().serviceInfo.name;
            if (getPackageName().equals(packageName)
                    && AutoClickAccessibilityService.class.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }
}
