package tr.cihan.bootloaderchecker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import tr.cihan.bootloaderchecker.core.PropertyReading;

/** Reads a fixed allowlist of public properties; never invokes a shell or requests root. */
final class PropertyReader {
    private static final int TIMEOUT_MILLIS = 1_000;
    private static final int OUTPUT_LIMIT_BYTES = 1_024;
    static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
            "ro.boot.flash.locked",
            "ro.boot.vbmeta.device_state",
            "ro.boot.verifiedbootstate",
            "ro.boot.veritymode",
            "ro.oem_unlock_supported",
            "sys.oem_unlock_allowed",
            "ro.boot.bootloader",
            "ro.boot.avb_version",
            "ro.boot.secureboot",
            "ro.secure",
            "ro.debuggable",
            "ro.build.version.emui"));

    static final class Snapshot {
        final List<PropertyReading> readings;

        Snapshot(List<PropertyReading> readings) {
            this.readings = Collections.unmodifiableList(new ArrayList<>(readings));
        }

        String emuiVersion() {
            for (PropertyReading reading : readings) {
                if ("ro.build.version.emui".equals(reading.key)
                        && reading.status == PropertyReading.Status.VALUE) {
                    return reading.raw;
                }
            }
            return "Erişilemedi / sunulmuyor";
        }
    }

    static Snapshot read() throws InterruptedException {
        // A new list for every scan prevents old values surviving a failed refresh.
        List<PropertyReading> readings = new ArrayList<>();
        for (String key : KEYS) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            readings.add(readOne(key));
        }
        return new Snapshot(readings);
    }

    private static PropertyReading readOne(String key) throws InterruptedException {
        Process process = null;
        try {
            // Fixed executable and separate arguments. No sh, su, reflection or hidden API.
            process = new ProcessBuilder("/system/bin/getprop", key).start();
            process.getOutputStream().close();
            if (!process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                return PropertyReading.timeout(key);
            }
            String raw = readBounded(process.getInputStream());
            if (process.exitValue() != 0) {
                return new PropertyReading(key, raw, PropertyReading.Status.ERROR,
                        "getprop başarısız oldu (çıkış kodu " + process.exitValue()
                                + "); erişim reddedilmiş olabilir.");
            }
            if (raw.trim().isEmpty()) {
                // Android does not reveal whether an empty result means missing or hidden.
                return new PropertyReading(key, raw, PropertyReading.Status.EMPTY,
                        "Boş yanıt: özellik sunulmuyor, tanımlı değil veya erişim kısıtlı olabilir.");
            }
            return PropertyReading.value(key, raw);
        } catch (SecurityException exception) {
            return PropertyReading.error(key, "Sistem erişimi reddetti.");
        } catch (IOException exception) {
            return PropertyReading.error(key,
                    "Özellik okunamadı (" + exception.getClass().getSimpleName() + ").");
        } finally {
            if (process != null) {
                if (process.isAlive()) process.destroyForcibly();
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                try { process.getOutputStream().close(); } catch (IOException ignored) { }
            }
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        // Read only after process exit. A full pipe before exit reaches the process timeout.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = input.read(buffer, 0,
                Math.min(buffer.length, OUTPUT_LIMIT_BYTES + 1 - output.size()))) != -1) {
            output.write(buffer, 0, count);
            if (output.size() > OUTPUT_LIMIT_BYTES) {
                throw new IOException("Property output limit exceeded");
            }
        }
        String raw = new String(output.toByteArray(), StandardCharsets.UTF_8);
        // Android getprop appends one LF; retain any control characters in the value.
        return raw.endsWith("\n") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private static void closeQuietly(InputStream stream) {
        try { stream.close(); } catch (IOException ignored) { }
    }

    private PropertyReader() { }
}
