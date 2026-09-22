package de.mpconsulting.autoclickpoint;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private TextView status;
    private Button accessibilityButton;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(0f, 1.08f);
        return v;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);
        root.setGravity(Gravity.TOP);

        TextView title = text("AUTOCLICK\nPOINT", 32, Color.rgb(18, 18, 20));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView lead = text("Ein fester Punkt. Wiederholte Klicks. Bis Pause.", 18, Color.DKGRAY);
        LinearLayout.LayoutParams leadLp = new LinearLayout.LayoutParams(-1, -2);
        leadLp.setMargins(0, dp(10), 0, dp(18));
        root.addView(lead, leadLp);

        status = text("Status wird geprüft …", 16, Color.rgb(150, 40, 40));
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, 0, 0, dp(18));
        root.addView(status, statusLp);

        String restrictedInfo = Build.VERSION.SDK_INT >= 33
                ? "Android 13–15 kann Bedienungshilfen bei manuell installierten APKs zunächst sperren.\n\n"
                  + "1  APP-FREIGABE öffnen\n"
                  + "2  oben rechts ⋮ antippen\n"
                  + "3  »Eingeschränkte Einstellungen zulassen« wählen\n"
                  + "4  zurück zu AutoClick Point\n"
                  + "5  BEDIENUNGSHILFE öffnen und AutoClick Point aktivieren"
                : "1  BEDIENUNGSHILFE öffnen\n"
                  + "2  AutoClick Point aktivieren\n"
                  + "3  zur gewünschten App wechseln";

        root.addView(text(restrictedInfo, 15, Color.rgb(38, 38, 42)));

        Button appInfo = new Button(this);
        appInfo.setText("1 · APP-FREIGABE ÖFFNEN");
        appInfo.setTextSize(15);
        appInfo.setAllCaps(false);
        appInfo.setOnClickListener(v -> openAppDetails());
        LinearLayout.LayoutParams appInfoLp = new LinearLayout.LayoutParams(-1, dp(56));
        appInfoLp.setMargins(0, dp(22), 0, dp(10));
        root.addView(appInfo, appInfoLp);

        accessibilityButton = new Button(this);
        accessibilityButton.setText("2 · BEDIENUNGSHILFE ÖFFNEN");
        accessibilityButton.setTextSize(15);
        accessibilityButton.setAllCaps(false);
        accessibilityButton.setOnClickListener(v -> openAccessibilitySettings());
        LinearLayout.LayoutParams accessibilityLp = new LinearLayout.LayoutParams(-1, dp(56));
        accessibilityLp.setMargins(0, 0, 0, dp(18));
        root.addView(accessibilityButton, accessibilityLp);

        root.addView(text(
                "Danach: gewünschte App öffnen → roten Zielpunkt positionieren → Intervall wählen → START. "
                        + "PAUSE beendet die Klickserie jederzeit.",
                15,
                Color.rgb(38, 38, 42)
        ));

        TextView privacy = text(
                "Lokal: keine Internet-Berechtigung, kein Root. Der Dienst führt nur die von dir festgelegte wiederholte Bildschirmgeste aus.",
                13,
                Color.GRAY
        );
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
        privacyLp.setMargins(0, dp(18), 0, 0);
        root.addView(privacy, privacyLp);

        TextView warning = text(
                "Wichtig: »Eingeschränkte Einstellungen zulassen« ist eine Android-Sicherheitsfreigabe und kann von einer APK nicht selbst bestätigt oder umgangen werden.",
                13,
                Color.rgb(150, 40, 40)
        );
        LinearLayout.LayoutParams warnLp = new LinearLayout.LayoutParams(-1, -2);
        warnLp.setMargins(0, dp(12), 0, 0);
        root.addView(warning, warnLp);

        setContentView(root, new LinearLayout.LayoutParams(-1, -1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void openAppDetails() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void refreshStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            status.setText("✓ Bedienungshilfe ist AKTIV");
            status.setTextColor(Color.rgb(20, 120, 65));
            accessibilityButton.setText("✓ BEDIENUNGSHILFE AKTIV");
        } else {
            status.setText("● Bedienungshilfe noch NICHT aktiv");
            status.setTextColor(Color.rgb(150, 40, 40));
            accessibilityButton.setText("2 · BEDIENUNGSHILFE ÖFFNEN");
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
