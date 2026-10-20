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

        addText(root, "CIHAN-UX  /  ANDROID DIAGNOSTICS", 12, ACCENT, true, 0);
        addText(root, "Huawei GMS Diagnostics", 27, WHITE, true, 7);
        addText(root, "v2.0.1  ·  Salt okunur  ·  Root / GMS gerekmez", 13, MUTED, false, 5);

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

        LinearLayout exportActions = new LinearLayout(this);
        exportActions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(exportActions);
        action(exportActions, "Kopyala", CARD, WHITE, () -> copyReport(), 1);
        action(exportActions, "TXT kaydet", CARD, WHITE, () -> saveReport(), 1);

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
        action(root, "Google Play Hizmetleri (GMS) bilgisi",
                CARD, WHITE, () -> openAppDetails("com.google.android.gms"), 0);
        action(root, "Google Services Framework (GSF) bilgisi",
                CARD, WHITE, () -> openAppDetails("com.google.android.gsf"), 0);
        action(root, "ReVanced microG uygulama bilgisi",
                CARD, WHITE, () -> openAppDetails("app.revanced.android.gms"), 0);
        addText(root, "Gizlilik: E-posta, hesap adi, token, Android ID ve logcat okunmaz. "
                + "Izinler sadece sorgulanir. Rapor sen kopyalar, kaydeder veya paylasirsan cihazdan cikar.",
                12, MUTED, false, 21);

        setContentView(scroller);
    }

    private void shareReport() {
        if (lastScan == null || lastScan.report == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Huawei GMS Diagnostics v2.0 raporu");
        send.putExtra(Intent.EXTRA_TEXT, lastScan.report);
        startActivity(Intent.createChooser(send, "Tanilama raporunu paylas"));
    }

    private void copyReport() {
        if (lastScan == null || lastScan.report == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("GMS tani raporu", lastScan.report));
        Toast.makeText(this, "Rapor panoya kopyalandi", Toast.LENGTH_SHORT).show();
    }

    private static final int REQUEST_SAVE_TXT = 27;
    private void saveReport() {
        if (lastScan == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "huawei-gms-diagnostics.txt");
        try { startActivityForResult(intent, REQUEST_SAVE_TXT); }
        catch (RuntimeException ignored) {
            Toast.makeText(this, "Dosya kaydetme ekrani acilamadi", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SAVE_TXT || resultCode != RESULT_OK || data == null
                || data.getData() == null || lastScan == null) return;
        try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new java.io.IOException("Dosya acilamadi");
            out.write(lastScan.report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(this, "Tani raporu kaydedildi", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Rapor kaydedilemedi", Toast.LENGTH_LONG).show();
        }
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
