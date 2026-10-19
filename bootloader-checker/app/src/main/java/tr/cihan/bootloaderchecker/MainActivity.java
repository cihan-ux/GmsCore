package tr.cihan.bootloaderchecker;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import tr.cihan.bootloaderchecker.core.Assessment;
import tr.cihan.bootloaderchecker.core.DeviceInfo;
import tr.cihan.bootloaderchecker.core.Evidence;
import tr.cihan.bootloaderchecker.core.PropertyReading;
import tr.cihan.bootloaderchecker.core.ReportFormatter;

/** Native, local-only UI. Each displayed result belongs to one completed scan. */
public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(23, 47, 43);
    private static final int MUTED = Color.rgb(71, 91, 86);
    private static final int ACCENT = Color.rgb(0, 107, 95);
    private static final int BACKGROUND = Color.rgb(243, 246, 245);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private LinearLayout evidenceList;
    private Button refresh;
    private Button copy;
    private ProgressBar progress;
    private TextView deviceDetails;
    private TextView lockTitle;
    private TextView lockDetail;
    private TextView lockConfidence;
    private TextView supportTitle;
    private TextView supportDetail;
    private TextView supportConfidence;
    private TextView stamp;
    private String report = "";
    private Future<?> runningScan;
    private int scanGeneration;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BACKGROUND);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(28));
        scroll.addView(root);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        scroll.requestApplyInsets();

        addText(root, getString(R.string.eyebrow), 12, ACCENT, true);
        heading(root, getString(R.string.app_name), 30);
        addText(root, getString(R.string.subtitle), 15, MUTED, false);

        LinearLayout device = card();
        heading(device, getString(R.string.device_heading), 19);
        deviceDetails = addText(device, "", 15, MUTED, false);
        deviceDetails.setTextIsSelectable(true);
        addText(device, getString(R.string.target_device), 13, MUTED, false);

        LinearLayout lock = card();
        addText(lock, getString(R.string.lock_heading), 12, ACCENT, true);
        lockTitle = addText(lock, "", 25, INK, true);
        lockTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        lockConfidence = addText(lock, "", 14, INK, true);
        lockDetail = addText(lock, "", 15, MUTED, false);

        LinearLayout support = card();
        addText(support, getString(R.string.support_heading), 12, ACCENT, true);
        supportTitle = addText(support, "", 23, INK, true);
        supportConfidence = addText(support, "", 14, INK, true);
        supportDetail = addText(support, "", 15, MUTED, false);
        addText(support, getString(R.string.support_caveat), 14, MUTED, false);

        LinearLayout huawei = card();
        heading(huawei, getString(R.string.huawei_heading), 19);
        addText(huawei, ReportFormatter.HUAWEI_NOTICE, 15, MUTED, false);
        addText(huawei, getString(R.string.oem_caveat), 14, MUTED, false);

        LinearLayout actions = card();
        progress = new ProgressBar(this);
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        progressParams.bottomMargin = dp(8);
        actions.addView(progress, progressParams);
        stamp = addText(actions, "", 13, MUTED, false);
        stamp.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        refresh = actionButton(actions, R.string.refresh, true);
        refresh.setOnClickListener(view -> scan());
        copy = actionButton(actions, R.string.copy_report, false);
        copy.setOnClickListener(view -> copyReport());
        addText(actions, getString(R.string.local_only), 13, MUTED, false);

        LinearLayout raw = card();
        heading(raw, getString(R.string.evidence_heading), 19);
        addText(raw, getString(R.string.evidence_intro), 14, MUTED, false);
        evidenceList = new LinearLayout(this);
        evidenceList.setOrientation(LinearLayout.VERTICAL);
        raw.addView(evidenceList);
        addText(root, getString(R.string.general_limit), 13, MUTED, false);
        scan();
    }

    private void scan() {
        if (isFinishing() || isDestroyed()) return;
        final int generation = ++scanGeneration;
        // Clear the previous snapshot immediately, including the clipboard source.
        report = "";
        refresh.setEnabled(false);
        copy.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        lockTitle.setText(R.string.checking);
        supportTitle.setText(R.string.checking);
        lockConfidence.setText("");
        supportConfidence.setText("");
        lockDetail.setText(R.string.reading_fresh);
        supportDetail.setText(R.string.reading_fresh);
        evidenceList.removeAllViews();
        addText(evidenceList, getString(R.string.reading_properties), 14, MUTED, false);
        deviceDetails.setText(deviceText(getString(R.string.checking)));
        stamp.setText(R.string.reading_properties);
        runningScan = worker.submit(() -> {
            try {
                PropertyReader.Snapshot snapshot = PropertyReader.read();
                Assessment result = Assessment.evaluate(snapshot.readings);
                DeviceInfo device = new DeviceInfo(Build.MANUFACTURER, Build.MODEL,
                        Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
                        snapshot.emuiVersion(), Build.DISPLAY);
                String time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                        DateFormat.MEDIUM, new Locale("tr", "TR")).format(new Date());
                String currentReport = getString(R.string.report_time, time)
                        + "\n\n" + ReportFormatter.format(device, result);
                runOnUiThread(() -> {
                    if (!isCurrentScan(generation)) return;
                    deviceDetails.setText(deviceText(snapshot.emuiVersion()));
                    lockTitle.setText(result.lockTitle());
                    lockConfidence.setText(getString(R.string.confidence, result.lockConfidenceLabel()));
                    lockDetail.setText(result.lockExplanation());
                    supportTitle.setText(result.supportTitle());
                    supportConfidence.setText(getString(R.string.confidence, result.supportConfidenceLabel()));
                    supportDetail.setText(result.supportExplanation());
                    showEvidence(result);
                    stamp.setText(getString(R.string.last_checked, time));
                    report = currentReport;
                    progress.setVisibility(View.GONE);
                    refresh.setEnabled(true);
                    copy.setEnabled(true);
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> showFailure(generation, R.string.scan_cancelled));
            } catch (RuntimeException exception) {
                runOnUiThread(() -> showFailure(generation, R.string.scan_failed));
            }
        });
    }

    private boolean isCurrentScan(int generation) {
        return generation == scanGeneration && !isFinishing() && !isDestroyed();
    }

    private void showFailure(int generation, int messageId) {
        if (!isCurrentScan(generation)) return;
        report = "";
        lockTitle.setText(R.string.unknown);
        supportTitle.setText(R.string.unknown);
        lockConfidence.setText(R.string.no_confidence);
        supportConfidence.setText(R.string.no_confidence);
        lockDetail.setText(messageId);
        supportDetail.setText(messageId);
        deviceDetails.setText(deviceText(getString(R.string.not_read)));
        evidenceList.removeAllViews();
        addText(evidenceList, getString(messageId), 15, MUTED, false);
        stamp.setText(messageId);
        progress.setVisibility(View.GONE);
        refresh.setEnabled(true);
        copy.setEnabled(false);
    }

    private void showEvidence(Assessment result) {
        evidenceList.removeAllViews();
        for (Evidence evidence : result.evidence) {
            PropertyReading reading = evidence.reading;
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(222, 232, 228));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
            dividerParams.topMargin = dp(16);
            dividerParams.bottomMargin = dp(10);
            evidenceList.addView(divider, dividerParams);
            TextView key = addText(evidenceList, reading.key, 14, INK, true);
            key.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            key.setTextIsSelectable(true);
            TextView raw = addText(evidenceList,
                    getString(R.string.raw_value, displayRaw(reading.raw)), 14, INK, false);
            raw.setTextIsSelectable(true);
            addText(evidenceList, getString(R.string.access_status, accessLabel(reading.status)),
                    14, MUTED, false);
            if (reading.detail != null && !reading.detail.isEmpty()) {
                addText(evidenceList, reading.detail, 14, MUTED, false);
            }
            addText(evidenceList, getString(R.string.evidence_target, evidence.targetLabel),
                    14, INK, true);
            addText(evidenceList, evidence.interpretation, 14, MUTED, false);
        }
    }

    private String deviceText(String emui) {
        return getString(R.string.device_details, Build.MANUFACTURER, Build.MODEL,
                Build.VERSION.RELEASE, Integer.toString(Build.VERSION.SDK_INT), emui, Build.DISPLAY);
    }

    private String displayRaw(String raw) {
        if (raw == null || raw.isEmpty()) return getString(R.string.raw_empty);
        // Escape control characters visibly without changing the collected snapshot.
        return "\u201c" + raw.replace("\\", "\\\\").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\t", "\\t") + "\u201d";
    }

    private String accessLabel(PropertyReading.Status status) {
        switch (status) {
            case VALUE: return getString(R.string.access_read);
            case EMPTY: return getString(R.string.access_empty);
            case TIMEOUT: return getString(R.string.access_timeout);
            default: return getString(R.string.access_error);
        }
    }

    private void copyReport() {
        if (report.isEmpty() || !copy.isEnabled()) return;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            Toast.makeText(this, R.string.clipboard_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.report_label), report));
        Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.rgb(226, 235, 231));
        box.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        root.addView(box, params);
        return box;
    }

    private TextView heading(LinearLayout parent, String text, int size) {
        TextView heading = addText(parent, text, size, INK, true);
        heading.setAccessibilityHeading(true);
        return heading;
    }

    private TextView addText(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(5));
        view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Button actionButton(LinearLayout parent, int textId, boolean primary) {
        Button button = new Button(this);
        button.setText(textId);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setTextColor(primary ? Color.WHITE : ACCENT);
        button.setBackgroundTintList(new ColorStateList(
                new int[][] { new int[] {android.R.attr.state_enabled}, new int[] {} },
                new int[] { primary ? ACCENT : Color.rgb(228, 242, 236), Color.rgb(196, 211, 205) }));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(button, params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        scanGeneration++;
        report = "";
        if (runningScan != null) runningScan.cancel(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
