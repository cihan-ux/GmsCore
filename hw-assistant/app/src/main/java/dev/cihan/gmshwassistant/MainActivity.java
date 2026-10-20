package dev.cihan.gmshwassistant;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Single-screen offline diagnostics; no app/account mutations. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 19, 32);
    private static final int CARD = Color.rgb(24, 36, 53);
    private static final int WHITE = Color.rgb(238, 246, 252);
    private static final int MUTED = Color.rgb(165, 184, 200);
    private static final int ACCENT = Color.rgb(46, 218, 183);
    private static final int WARNING = Color.rgb(252, 194, 102);
    private DeviceScanner.Scan lastScan;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        refresh();
    }

    private void refresh() {
        lastScan = DeviceScanner.run(this);
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
        scroller.addView(root);

        addText(root, "GMSCORE  /  HUAWEI", 12, ACCENT, true, 0);
        addText(root, "HW Assistant", 30, WHITE, true, 7);
        addText(root, "v1.0  ·  Yerel tanilama  ·  Root gerekmez", 13, MUTED, false, 5);

        LinearLayout hero = card(root, 20, 14);
        addText(hero, "Cihaz tanilama raporu", 20, WHITE, true, 0);
        addText(hero, lastScan.installedPackages + " bilinen paket bulundu  •  " +
                lastScan.cautions + " dikkat kaydi", 14, MUTED, false, 10);
        addText(hero, "Bu uygulama Play Store'u degistirmez; korumali izinleri veremez.",
                12, MUTED, false, 11);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(15), 0, dp(18));
        root.addView(actions);
        action(actions, "Yeniden tara", ACCENT, BG, () -> refresh(), 1);
        action(actions, "Raporu paylas", CARD, WHITE, () -> shareReport(), 1);

        addText(root, "KONTROLLER", 12, MUTED, true, 6);
        for (DeviceScanner.Finding finding : lastScan.findings) {
            LinearLayout item = card(root, 14, 10);
            LinearLayout heading = new LinearLayout(this);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(heading);
            TextView dot = addText(heading, "●", 16,
                    finding.level == DeviceScanner.CAUTION ? WARNING :
                    finding.level == DeviceScanner.OK ? ACCENT : MUTED, true, 0);
            dot.setPadding(0, 0, dp(8), 0);
            TextView label = addText(heading, finding.title, 15, WHITE, true, 0);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            addText(item, finding.details, 12, MUTED, false, 9).setTextIsSelectable(true);
        }

        addText(root, "HIZLI ERISIM", 12, MUTED, true, 18);
        action(root, "Play Store uygulama bilgisi",
                CARD, WHITE, () -> openAppDetails("com.android.vending"), 0);
        action(root, "Morphe microG uygulama bilgisi",
                CARD, WHITE, () -> openAppDetails("app.revanced.android.gms"), 0);
        addText(root, "Gizlilik: E-posta, hesap adi, token, cihaz kimligi ve logcat alinmaz. " +
                "Rapor yalnizca sen paylasirsan cihazdan cikar.", 12, MUTED, false, 21);

        setContentView(scroller);
    }

    private void shareReport() {
        if (lastScan == null || lastScan.report == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "GmsCore HW Assistant raporu");
        send.putExtra(Intent.EXTRA_TEXT, lastScan.report);
        startActivity(Intent.createChooser(send, "Tanilama raporunu paylas"));
    }

    private void openAppDetails(String pkg) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + pkg));
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            Toast.makeText(this, "Uygulama bilgisi acilamadi.", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout card(LinearLayout parent, int padding, int marginTop) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        wrapper.setBackground(round(CARD, 16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginTop);
        parent.addView(wrapper, params);
        return wrapper;
    }

    private void action(LinearLayout parent, String title, int bg, int color,
                        Runnable onClick, int weight) {
        TextView text = new TextView(this);
        text.setText(title);
        text.setTextColor(color);
        text.setTextSize(13);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setPadding(dp(9), dp(14), dp(9), dp(14));
        text.setBackground(round(bg, 11));
        text.setClickable(true);
        text.setFocusable(true);
        text.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = weight > 0
                ? new LinearLayout.LayoutParams(0, -2, weight)
                : new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(3), dp(5), dp(3), dp(5));
        parent.addView(text, params);
    }

    private TextView addText(LinearLayout parent, String value, int size, int color,
                             boolean bold, int marginTop) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginTop);
        parent.addView(view, params);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
