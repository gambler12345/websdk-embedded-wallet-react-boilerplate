package de.mpconsulting.autoclickpoint;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String value,float sp,int color){ TextView v=new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setLineSpacing(0f,1.08f); return v; }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(32),dp(24),dp(24));
        root.setBackgroundColor(Color.WHITE);
        root.setGravity(Gravity.TOP);

        TextView title=text("AUTOCLICK\nPOINT",32,Color.rgb(18,18,20));
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView lead=text("Ein fester Punkt. Wiederholte Klicks. Bis Pause.",18,Color.DKGRAY);
        LinearLayout.LayoutParams leadLp=new LinearLayout.LayoutParams(-1,-2); leadLp.setMargins(0,dp(12),0,dp(28)); root.addView(lead,leadLp);

        root.addView(text("1  Bedienungshilfe einschalten\n\n2  Zur gewünschten App wechseln\n\n3  Roten Zielpunkt an die Klickstelle ziehen\n\n4  Intervall wählen – MAX = schnellstmögliche Folge\n\n5  START drücken; PAUSE beendet die Klickserie",16,Color.rgb(38,38,42)));

        Button enable=new Button(this);
        enable.setText("BEDIENUNGSHILFE ÖFFNEN"); enable.setTextSize(15); enable.setAllCaps(false);
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams btnLp=new LinearLayout.LayoutParams(-1,dp(56)); btnLp.setMargins(0,dp(28),0,dp(14)); root.addView(enable,btnLp);

        root.addView(text("Lokal: keine Internet-Berechtigung, kein Root. Der Klickdienst führt nur die von dir festgelegte wiederholte Bildschirmgeste aus.",13,Color.GRAY));
        TextView warning=text("Hinweis: Manche Apps oder Sicherheitsoberflächen können automatisierte Eingaben blockieren oder in ihren Nutzungsbedingungen untersagen.",13,Color.rgb(150,40,40));
        LinearLayout.LayoutParams warnLp=new LinearLayout.LayoutParams(-1,-2); warnLp.setMargins(0,dp(16),0,0); root.addView(warning,warnLp);
        setContentView(root,new LinearLayout.LayoutParams(-1,-1));
    }
}
