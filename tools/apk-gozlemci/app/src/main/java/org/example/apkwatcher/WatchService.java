package org.example.apkwatcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class WatchService extends Service {
    private static final String NOTIFY_CHANNEL = "apk_ready";
    private static final String SERVICE_CHANNEL = "apk_watching";
    private static final long POLL_DELAY_MS = 5000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Map<String, String> previous = new HashMap<>();
    private SharedPreferences prefs;
    private volatile boolean active;

    private static class Apk {
        String name;
        String id;
        String state;
        String key;
        Uri uri;
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel(
            SERVICE_CHANNEL, "APK klasörü izleme", NotificationManager.IMPORTANCE_LOW));
        notifications.createNotificationChannel(new NotificationChannel(
            NOTIFY_CHANNEL, "Yeni APK dosyaları", NotificationManager.IMPORTANCE_HIGH));
        active = true;
    }

    @Override public int onStartCommand(Intent start, int flags, int startId) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification ongoing = new Notification.Builder(this, SERVICE_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("APK Gözlemci etkin")
            .setContentText("Yeni APK dosyaları izleniyor")
            .setContentIntent(pending)
            .setOngoing(true)
            .build();
        startForeground(1001, ongoing);
        handler.removeCallbacks(poll);
        handler.post(poll);
        return START_STICKY;
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (busy.compareAndSet(false, true)) {
                executor.execute(() -> {
                    try {
                        scan();
                    } catch (Exception e) {
                        Log.e("ApkWatcher", "Klasör taraması başarısız", e);
                    } finally {
                        busy.set(false);
                    }
                });
            }
            handler.postDelayed(this, POLL_DELAY_MS);
        }
    };

    private List<Apk> listApks(Uri tree) {
        List<Apk> apks = new ArrayList<>();
        String parent = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent);
        String[] columns = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) return apks;
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                String id = cursor.getString(0);
                long size = cursor.isNull(2) ? 0L : cursor.getLong(2);
                long modified = cursor.isNull(3) ? 0L : cursor.getLong(3);
                if (id == null || size < 1024L) continue;
                Apk apk = new Apk();
                apk.name = name;
                apk.id = id;
                apk.state = size + ":" + modified;
                apk.key = id + ":" + apk.state;
                apk.uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                apks.add(apk);
            }
        }
        return apks;
    }

    private void scan() {
        String location = prefs.getString(MainActivity.PREF_TREE, null);
        if (location == null) return;
        List<Apk> apks = listApks(Uri.parse(location));
        Set<String> known = new HashSet<>(prefs.getStringSet("known", new HashSet<>()));
        if (!prefs.getBoolean("initialized", false)) {
            for (Apk apk : apks) {
                known.add(apk.key);
                previous.put(apk.id, apk.state);
            }
            prefs.edit().putStringSet("known", known).putBoolean("initialized", true).apply();
            return;
        }
        Map<String, String> current = new HashMap<>();
        boolean changed = false;
        for (Apk apk : apks) {
            current.put(apk.id, apk.state);
            // İndirme sürerken kurulumu açma: iki ardışık taramada dosya sabit olmalı.
            if (!known.contains(apk.key) && apk.state.equals(previous.get(apk.id))
                && validApkHeader(apk.uri)) {
                postInstallNotification(apk);
                known.add(apk.key);
                changed = true;
            }
        }
        previous.clear();
        previous.putAll(current);
        if (changed) {
            if (known.size() > 1000) {
                known.clear();
                for (Apk apk : apks) known.add(apk.key);
            }
            prefs.edit().putStringSet("known", known).apply();
        }
    }

    private boolean validApkHeader(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return false;
            byte[] signature = new byte[4];
            int length = input.read(signature);
            return length == 4 && signature[0] == 'P' && signature[1] == 'K'
                && signature[2] == 3 && signature[3] == 4;
        } catch (Exception e) {
            return false;
        }
    }

    private void postInstallNotification(Apk apk) {
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apk.uri, "application/vnd.android.package-archive");
        install.setClipData(ClipData.newUri(getContentResolver(), "APK", apk.uri));
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        // Mümkünse doğrudan Huawei'nin orijinal paket yükleyicisini kullan.
        Intent huawei = new Intent(install);
        huawei.setPackage("com.android.packageinstaller");
        if (huawei.resolveActivity(getPackageManager()) != null) {
            install = huawei;
        }
        int requestCode = 2000 + (apk.id.hashCode() & 0xfffffff);
        PendingIntent pending = PendingIntent.getActivity(this, requestCode, install,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification ready = new Notification.Builder(this, NOTIFY_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("APK hazır: " + apk.name)
            .setContentText("Kurulum ekranını açmak için dokun")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify(requestCode, ready);
    }

    @Override public void onDestroy() {
        active = false;
        handler.removeCallbacks(poll);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
