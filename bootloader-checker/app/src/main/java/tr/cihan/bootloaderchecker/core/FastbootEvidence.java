package tr.cihan.bootloaderchecker.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interprets user-pasted fastboot output. Never runs fastboot or changes the device.
 * This is separate from on-device property evidence and must not imply unlockability.
 */
public final class FastbootEvidence {
    public enum Lock { LOCKED, UNLOCKED, UNKNOWN, CONFLICT }

    private static final int MAX_LENGTH = 12_000;
    private static final Pattern PREFIX = Pattern.compile(
            "^\\s*\\(bootloader\\)\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCK_STATE = Pattern.compile(
            "^(?:bootloader\\s+)?lock\\s*state\\s*:\\s*(locked|unlocked)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNLOCKED_VAR = Pattern.compile(
            "^unlocked\\s*:\\s*(yes|no|true|false|1|0)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FB_LOCK = Pattern.compile(
            "^fb\\s+lockstate\\s*:\\s*(locked|unlocked)$",
            Pattern.CASE_INSENSITIVE);

    public final Lock lock;
    public final int recognizedLines;
    public final boolean inputTooLong;

    private FastbootEvidence(Lock lock, int recognizedLines, boolean inputTooLong) {
        this.lock = lock;
        this.recognizedLines = recognizedLines;
        this.inputTooLong = inputTooLong;
    }

    public static FastbootEvidence parse(String output) {
        if (output == null || output.trim().isEmpty()) {
            return new FastbootEvidence(Lock.UNKNOWN, 0, false);
        }
        if (output.length() > MAX_LENGTH) {
            return new FastbootEvidence(Lock.UNKNOWN, 0, true);
        }
        boolean locked = false;
        boolean unlocked = false;
        int count = 0;
        for (String rawLine : output.split("\\r?\\n")) {
            Matcher prefix = PREFIX.matcher(rawLine);
            boolean hasBootloaderPrefix = prefix.matches();
            String line = (hasBootloaderPrefix ? prefix.group(1) : rawLine).trim()
                    .toLowerCase(Locale.ROOT);
            // Naked words outside an actual (bootloader) response are not evidence.
            if (hasBootloaderPrefix && ("locked".equals(line) || "unlocked".equals(line))) {
                locked |= "locked".equals(line);
                unlocked |= "unlocked".equals(line);
                count++;
                continue;
            }
            Matcher state = LOCK_STATE.matcher(line);
            Matcher fbLock = FB_LOCK.matcher(line);
            Matcher getvar = UNLOCKED_VAR.matcher(line);
            if (state.matches()) {
                locked |= "locked".equals(state.group(1));
                unlocked |= "unlocked".equals(state.group(1));
                count++;
            } else if (fbLock.matches()) {
                locked |= "locked".equals(fbLock.group(1));
                unlocked |= "unlocked".equals(fbLock.group(1));
                count++;
            } else if (getvar.matches()) {
                String value = getvar.group(1);
                unlocked |= "yes".equals(value) || "true".equals(value) || "1".equals(value);
                locked |= "no".equals(value) || "false".equals(value) || "0".equals(value);
                count++;
            }
            // FRP lock, OEM permission, and FAILED/remote errors are not lock evidence.
        }
        Lock result = locked && unlocked ? Lock.CONFLICT
                : locked ? Lock.LOCKED : unlocked ? Lock.UNLOCKED : Lock.UNKNOWN;
        return new FastbootEvidence(result, count, false);
    }

    public String title() {
        switch (lock) {
            case LOCKED: return "Kilitli bildiriliyor";
            case UNLOCKED: return "Kilit açık bildiriliyor";
            case CONFLICT: return "Çelişkili bildirimler";
            default: return "Bilinmiyor";
        }
    }

    public String explanation() {
        if (inputTooLong) {
            return "Yapıştırılan çıktı 12.000 karakteri aşıyor. İşlenmedi; daha kısa çıktı kullanın.";
        }
        switch (lock) {
            case LOCKED:
                return "Yapıştırılan Fastboot metninde kilitli durumu bildiren satır bulundu.";
            case UNLOCKED:
                return "Yapıştırılan Fastboot metninde kilit açık durumu bildiren satır bulundu.";
            case CONFLICT:
                return "Yapıştırılan metinde hem kilitli hem kilit açık bildirimi var. "
                        + "Karar verilmedi.";
            default:
                return "Desteklenen bir kilit durumu bildirimi bulunamadı. "
                        + "FAILED / Command not allowed çıktıları durum kanıtı değildir.";
        }
    }

    public String compare(Assessment assessment) {
        if (assessment == null || lock == Lock.UNKNOWN || lock == Lock.CONFLICT) {
            return "Cihazdaki sistem özelliği sonucuyla güvenilir karşılaştırma yapılamadı.";
        }
        if (assessment.lock != Assessment.Lock.LOCKED
                && assessment.lock != Assessment.Lock.UNLOCKED) {
            return "Uygulama cihaz özelliklerinden kesin bir kilit durumu okuyamadı; "
                    + "Fastboot metni ayrı tutuldu.";
        }
        boolean matches = (lock == Lock.LOCKED && assessment.lock == Assessment.Lock.LOCKED)
                || (lock == Lock.UNLOCKED && assessment.lock == Assessment.Lock.UNLOCKED);
        return matches ? "Yapıştırılan Fastboot bildirimi cihaz özellikleriyle uyumlu."
                : "UYUŞMAZLIK: Yapıştırılan Fastboot bildirimi cihaz özellikleriyle çelişiyor. "
                    + "Her iki kaynağı bağımsız olarak yeniden doğrulayın.";
    }

    public String report(Assessment assessment) {
        return "MANUEL FASTBOOT ÇIKTISI DEĞERLENDİRMESİ\n"
                + "Durum: " + title() + "\n"
                + "Tanınan durum satırı sayısı: " + recognizedLines + "\n"
                + explanation() + "\n"
                + compare(assessment) + "\n"
                + "Bu veri kullanıcı tarafından yapıştırılmıştır; orijinal cihaz çıktısı "
                + "olduğu doğrulanamaz. Ham metin rapora eklenmez. Kilit açılabilirliği, "
                + "üretici yetkisi veya kod bulunabilirliği hakkında karar verdirmez.\n";
    }
}
