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

        TextView lead = text("Drücken → Loslassen → wiederholen. Bis Pause oder Stop.", 18, Color.DKGRAY);
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

        TextView setupTitle = text("Android 13–15 · richtige Freigabereihenfolge", 18, Color.rgb(18, 18, 20));
        setupTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(setupTitle);

        String restrictedInfo = Build.VERSION.SDK_INT >= 33
                ? "1  Zuerst BEDIENUNGSHILFEN öffnen.\n"
                  + "   AutoClick Point auswählen und das Einschalten versuchen.\n"
                  + "   Falls Android »Eingeschränkte Einstellung« meldet: Meldung bestätigen.\n\n"
                  + "2  Danach SYSTEMEINSTELLUNGEN öffnen.\n"
                  + "   Dort MANUELL zu Apps → Alle Apps → AutoClick Point navigieren.\n"
                  + "   Nicht über einen direkten App-Info-Shortcut gehen.\n\n"
                  + "3  Falls nun ⋮ / »Eingeschränkte Einstellungen zulassen« erscheint: freigeben.\n\n"
                  + "4  Danach BEDIENUNGSHILFEN erneut öffnen und AutoClick Point aktivieren."
                : "1  BEDIENUNGSHILFEN öffnen\n"
                  + "2  AutoClick Point aktivieren\n"
                  + "3  zur gewünschten App wechseln";
        TextView instructions = text(restrictedInfo, 15, Color.rgb(38, 38, 42));
        LinearLayout.LayoutParams instructionsLp = new LinearLayout.LayoutParams(-1, -2);
        instructionsLp.setMargins(0, dp(8), 0, dp(18));
        root.addView(instructions, instructionsLp);

        accessibilityButton = button("1 · BEDIENUNGSHILFEN ÖFFNEN / TESTEN");
        accessibilityButton.setOnClickListener(v -> openAccessibilitySettings());
        LinearLayout.LayoutParams accessibilityLp = new LinearLayout.LayoutParams(-1, dp(58));
        accessibilityLp.setMargins(0, 0, 0, dp(10));
        root.addView(accessibilityButton, accessibilityLp);

        Button systemSettings = button("2 · SYSTEMEINSTELLUNGEN ÖFFNEN");
        systemSettings.setOnClickListener(v -> openSystemSettings());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(-1, dp(58));
        settingsLp.setMargins(0, 0, 0, dp(10));
        root.addView(systemSettings, settingsLp);

        Button checkAgain = button("STATUS ERNEUT PRÜFEN");
        checkAgain.setOnClickListener(v -> refreshStatus());
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(-1, dp(54));
        checkLp.setMargins(0, 0, 0, dp(20));
        root.addView(checkAgain, checkLp);

        TextView motorola = text(
                "Hinweis für Motorola/Android 15: Wenn auf der App-Info-Seite kein ⋮-Menü zu sehen ist, zuerst den Aktivierungsversuch unter Bedienungshilfen ausführen und anschließend über die normale Einstellungen-App manuell zur App-Info navigieren.",
                13,
                Color.rgb(120, 75, 0)
        );
        LinearLayout.LayoutParams motoLp = new LinearLayout.LayoutParams(-1, -2);
        motoLp.setMargins(0, 0, 0, dp(18));
        root.addView(motorola, motoLp);

        root.addView(text(
                "Bedienung: Zielpunkt setzen → Intervall wählen → START. Jeder Zyklus drückt ca. 45 ms und lässt danach los. "
                        + "PAUSE hält an, WEITER setzt fort. STOP beendet die Serie vollständig und entsperrt den Zielpunkt wieder.",
                15,
                Color.rgb(38, 38, 42)
        ));

        TextView privacy = text(
                "Lokal: keine Internet-Berechtigung, kein Root. AutoClick Point liest keine Inhalte anderer Apps; der Dienst sendet nur die von dir festgelegte Bildschirmgeste.",
                13,
                Color.GRAY
        );
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
        privacyLp.setMargins(0, dp(18), 0, 0);
        root.addView(privacy, privacyLp);

        TextView warning = text(
                "Die Android-Sicherheitsfreigabe für eingeschränkte Einstellungen muss vom Nutzer selbst bestätigt werden und kann von der APK nicht automatisch gesetzt oder umgangen werden.",
                13,
                Color.rgb(150, 40, 40)
        );
        LinearLayout.LayoutParams warnLp = new LinearLayout.LayoutParams(-1, -2);
        warnLp.setMargins(0, dp(12), 0, 0);
        root.addView(warning, warnLp);

        setContentView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
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
        } else {
            status.setText("● Systemfreigabe noch NICHT aktiv");
            status.setTextColor(Color.rgb(150, 40, 40));
            accessibilityButton.setText("1 · BEDIENUNGSHILFEN ÖFFNEN / TESTEN");
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
