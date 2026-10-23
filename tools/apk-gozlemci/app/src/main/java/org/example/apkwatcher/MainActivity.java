package org.example.apkwatcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER = 42;
    public static final String PREFS = "apk_watch";
    public static final String PREF_TREE = "tree_uri";
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 48, 28, 28);
        TextView title = new TextView(this);
        title.setText("APK Gözlemci");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        layout.addView(title);
        TextView help = new TextView(this);
        help.setText("Yeni APK dosyalarını algılayıp Huawei paket yükleyicisiyle açar. Orijinal yükleyici ve dijital imzası değiştirilmez. Kurulum onayın gerekir.");
        help.setTextSize(16);
        help.setPadding(0, 24, 0, 24);
        layout.addView(help);
        status = new TextView(this);
        layout.addView(status);
        addButton(layout, "1. APK klasörünü seç", () -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pick, PICK_FOLDER);
        });
        addButton(layout, "2. İzlemeyi başlat", this::startWatching);
        addButton(layout, "İzlemeyi durdur", () -> {
            stopService(new Intent(this, WatchService.class));
            Toast.makeText(this, "İzleme durduruldu", Toast.LENGTH_SHORT).show();
        });
        TextView note = new TextView(this);
        note.setPadding(0, 28, 0, 0);
        note.setText("İndirilenler klasörünü veya içindeki APK alt klasörünü seç. Yeni APK algılanınca bildirime dokunup kurulum ekranını açabilirsin. EMUI pil optimizasyonu izlemenin durmasına yol açabilir.");
        layout.addView(note);
        setContentView(layout);
        refreshStatus();
    }

    private void addButton(LinearLayout layout, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        layout.addView(button, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button.setOnClickListener(v -> action.run());
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FOLDER || resultCode != RESULT_OK ||
            data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit().putString(PREF_TREE, tree.toString()).remove("known")
                .putBoolean("initialized", false).apply();
            refreshStatus();
            startWatching();
        } catch (SecurityException e) {
            Toast.makeText(this, "Klasör izni alınamadı", Toast.LENGTH_LONG).show();
        }
    }

    private void startWatching() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_TREE, null);
        if (value == null) {
            Toast.makeText(this, "Önce klasör seç", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DocumentsContract.getTreeDocumentId(Uri.parse(value));
            startForegroundService(new Intent(this, WatchService.class));
            Toast.makeText(this, "APK izleme başlatıldı", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "İzleme başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshStatus() {
        String tree = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_TREE, null);
        status.setText(tree == null ? "Klasör seçilmedi" : "İzlenen klasör: " + tree);
    }
}
