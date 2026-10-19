package tr.cihan.bootloaderchecker.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Stateless Android-independent evaluation of a single complete scan snapshot. */
public final class Assessment {
    public enum Lock { LOCKED, UNLOCKED, UNKNOWN, CONFLICT }
    public enum Support { SUPPORTED_EVIDENCE, UNSUPPORTED_EVIDENCE, UNKNOWN, CONFLICT }
    public enum Confidence { HIGH, MEDIUM, LOW, NONE }

    public static final String FLASH_LOCKED = "ro.boot.flash.locked";
    public static final String VBMETA_STATE = "ro.boot.vbmeta.device_state";
    public static final String VERIFIED_BOOT_STATE = "ro.boot.verifiedbootstate";
    public static final String VERITY_MODE = "ro.boot.veritymode";
    public static final String OEM_SUPPORT = "ro.oem_unlock_supported";
    public static final String OEM_ALLOWED = "sys.oem_unlock_allowed";

    public final Lock lock;
    public final Support support;
    public final Confidence lockConfidence;
    public final Confidence supportConfidence;
    public final List<Evidence> evidence;
    private final boolean verifiedContextDisagrees;
    private final boolean permissionEnabled;
    private final boolean permissionDisabled;

    private Assessment(Lock lock, Support support, Confidence lockConfidence,
            Confidence supportConfidence, List<Evidence> evidence,
            boolean verifiedContextDisagrees, boolean permissionEnabled,
            boolean permissionDisabled) {
        this.lock = lock;
        this.support = support;
        this.lockConfidence = lockConfidence;
        this.supportConfidence = supportConfidence;
        this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
        this.verifiedContextDisagrees = verifiedContextDisagrees;
        this.permissionEnabled = permissionEnabled;
        this.permissionDisabled = permissionDisabled;
    }

    /**
     * Every invocation starts from zero. Callers must supply only the new scan's readings.
     * Duplicated observations are retained, so contradictory direct readings are not hidden.
     */
    public static Assessment evaluate(List<PropertyReading> readings) {
        if (readings == null) {
            throw new IllegalArgumentException("Tarama sonuçları belirtilmelidir.");
        }
        boolean directLocked = false;
        boolean directUnlocked = false;
        boolean supported = false;
        boolean unsupported = false;
        boolean allowed = false;
        boolean disallowed = false;
        boolean verifiedLockedContext = false;
        boolean verifiedUnlockedContext = false;
        List<Evidence> explained = new ArrayList<>();

        // Work from a defensive snapshot, not a shared collection carried across scans.
        List<PropertyReading> snapshot = new ArrayList<>(readings);
        for (PropertyReading reading : snapshot) {
            if (reading == null) {
                throw new IllegalArgumentException("Tarama sonucu boş bir öğe içeremez.");
            }
            explained.add(explain(reading));
            if (reading.status != PropertyReading.Status.VALUE) {
                continue;
            }
            String value = normalize(reading.raw);
            switch (reading.key) {
                case FLASH_LOCKED:
                    directLocked |= "1".equals(value);
                    directUnlocked |= "0".equals(value);
                    break;
                case VBMETA_STATE:
                    directLocked |= "locked".equals(value);
                    directUnlocked |= "unlocked".equals(value);
                    break;
                case VERIFIED_BOOT_STATE:
                    verifiedLockedContext |= "green".equals(value) || "yellow".equals(value);
                    verifiedUnlockedContext |= "orange".equals(value);
                    break;
                case OEM_SUPPORT:
                    supported |= "1".equals(value);
                    unsupported |= "0".equals(value);
                    break;
                case OEM_ALLOWED:
                    allowed |= "1".equals(value);
                    disallowed |= "0".equals(value);
                    break;
                default:
                    // Other values provide context only; they never decide either result.
                    break;
            }
        }

        Lock lock = directLocked && directUnlocked ? Lock.CONFLICT
                : directLocked ? Lock.LOCKED
                : directUnlocked ? Lock.UNLOCKED : Lock.UNKNOWN;
        boolean contextDisagrees = lock == Lock.LOCKED && verifiedUnlockedContext
                || lock == Lock.UNLOCKED && verifiedLockedContext;
        Confidence lockConfidence = lock == Lock.UNKNOWN ? Confidence.NONE
                : lock == Lock.CONFLICT || contextDisagrees ? Confidence.LOW : Confidence.MEDIUM;

        // Permission alone never proves support. An explicit no-support/permission-on
        // combination is reported as inconsistent firmware data, not as an unlock promise.
        boolean supportConflict = supported && unsupported || unsupported && allowed
                || allowed && disallowed;
        Support support = supportConflict ? Support.CONFLICT
                : supported ? Support.SUPPORTED_EVIDENCE
                : unsupported ? Support.UNSUPPORTED_EVIDENCE : Support.UNKNOWN;
        Confidence supportConfidence = support == Support.UNKNOWN ? Confidence.NONE
                : support == Support.CONFLICT ? Confidence.LOW : Confidence.MEDIUM;
        return new Assessment(lock, support, lockConfidence, supportConfidence, explained,
                contextDisagrees, allowed, disallowed);
    }

    private static Evidence explain(PropertyReading reading) {
        if (reading.status != PropertyReading.Status.VALUE) {
            String explanation;
            switch (reading.status) {
                case EMPTY:
                    explanation = "Değer sunulmadı. Özellik bulunmuyor, boş olabilir veya Android "
                            + "erişimi kısıtlıyor olabilir; yalnızca boş çıktıyla bunlar ayırt edilemez.";
                    break;
                case TIMEOUT:
                    explanation = "Okuma zamanında tamamlanmadı; bu taramada kanıt olarak kullanılmadı.";
                    break;
                case ERROR:
                    explanation = "Özellik okunamadı; hata, kilit veya açılabilirlik sonucu sayılmaz.";
                    break;
                default:
                    throw new AssertionError(reading.status);
            }
            return new Evidence(reading, explanation, "Hiçbir sonuca kanıt değil");
        }

        String value = normalize(reading.raw);
        switch (reading.key) {
            case FLASH_LOCKED:
                if ("1".equals(value)) {
                    return new Evidence(reading, "Firmware bootloader kilidinin kapalı olduğunu "
                            + "doğrudan bildiriyor.", "Kilit durumu: Kilitli kanıtı");
                }
                if ("0".equals(value)) {
                    return new Evidence(reading, "Firmware bootloader kilidinin açık olduğunu "
                            + "doğrudan bildiriyor.", "Kilit durumu: Kilit açık kanıtı");
                }
                break;
            case VBMETA_STATE:
                if ("locked".equals(value)) {
                    return new Evidence(reading, "VBMeta cihaz durumu kilitli olarak bildiriliyor.",
                            "Kilit durumu: Kilitli kanıtı");
                }
                if ("unlocked".equals(value)) {
                    return new Evidence(reading, "VBMeta cihaz durumu kilidi açık olarak bildiriliyor.",
                            "Kilit durumu: Kilit açık kanıtı");
                }
                break;
            case VERIFIED_BOOT_STATE:
                switch (value) {
                    case "green":
                        return context(reading, "Verified Boot üretici güven köküyle doğrulama "
                                + "bildiriyor. Kilitli durumla genellikle uyumludur; tek başına "
                                + "kesin bootloader kilit kanıtı değildir.");
                    case "yellow":
                        return context(reading, "Verified Boot özel bir güven köküyle doğrulama "
                                + "bildiriyor. Kilitli durumla genellikle uyumludur; tek başına "
                                + "kesin bootloader kilit kanıtı değildir.");
                    case "orange":
                        return context(reading, "Verified Boot turuncu durum bildiriyor; genellikle "
                                + "kilidi açık cihazlarla ilişkilidir. Tek başına kesin bootloader "
                                + "kilit kanıtı değildir.");
                    case "red":
                        return context(reading, "Verified Boot doğrulama başarısızlığı bildiriyor. "
                                + "Bu değer kilidin açık veya kapalı olduğunu tek başına göstermez.");
                    default:
                        break;
                }
                break;
            case VERITY_MODE:
                return context(reading, "dm-verity çalışma modu bildiriliyor. Bölüm doğrulama "
                        + "davranışıdır; bootloader kilit durumu veya açılabilirlik kanıtı değildir.");
            case OEM_SUPPORT:
                if ("1".equals(value)) {
                    return new Evidence(reading, "Firmware OEM kilit açma desteği bildiriyor. "
                            + "Destek sinyali; kullanılabilir bir kod, üretici onayı veya başarılı "
                            + "kilit açma garantisi değildir.", "Açılabilirlik desteği: Destek kanıtı");
                }
                if ("0".equals(value)) {
                    return new Evidence(reading, "Firmware OEM kilit açma desteği sunmadığını "
                            + "bildiriyor. Bu bildirim, tüm üçüncü taraf yöntemlerin imkânsız "
                            + "olduğunu kanıtlamaz.", "Açılabilirlik desteği: Desteklenmeme kanıtı");
                }
                break;
            case OEM_ALLOWED:
                if ("1".equals(value)) {
                    return new Evidence(reading, "Mevcut OEM kilit açma izin sinyali açık. İzin, "
                            + "destek veya gerçek kilit açabilme garantisi değildir; bu uygulama "
                            + "ayarı değiştirmez.", "Açılabilirlik: Yalnızca mevcut izin bağlamı");
                }
                if ("0".equals(value)) {
                    return new Evidence(reading, "Mevcut OEM kilit açma izin sinyali kapalı. "
                            + "Bu, cihazın destek sunmadığını veya kalıcı olarak açılamayacağını "
                            + "göstermez.", "Açılabilirlik: Yalnızca mevcut izin bağlamı");
                }
                break;
            case "ro.build.version.emui":
                return context(reading, "Firmware'in bildirdiği EMUI sürümü; kilit sonuçlarını etkilemez.");
            case "ro.build.display.id":
                return context(reading, "Firmware'in bildirdiği derleme kimliği; kilit sonuçlarını etkilemez.");
            default:
                return context(reading, "Ek cihaz / boot özelliği. Bu özelliğin değeri tek başına "
                        + "kilit durumu veya açılabilirlik desteği kararı için kullanılmaz.");
        }
        return new Evidence(reading, "Bu özellik için tanınmayan değer; değer korunur ve "
                + "herhangi bir kilit ya da destek kararı için kullanılmaz.", "Hiçbir sonuca kanıt değil");
    }

    private static Evidence context(PropertyReading reading, String explanation) {
        return new Evidence(reading, explanation, "Yalnızca bağlam; kesin karar kanıtı değil");
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public String lockTitle() {
        switch (lock) {
            case LOCKED: return "Kilitli";
            case UNLOCKED: return "Kilit açık";
            case CONFLICT: return "Çelişkili";
            case UNKNOWN: return "Bilinmiyor";
            default: throw new AssertionError(lock);
        }
    }

    public String supportTitle() {
        switch (support) {
            case SUPPORTED_EVIDENCE: return "Açılabilirlik desteğine ilişkin kanıt var";
            case UNSUPPORTED_EVIDENCE: return "Desteklenmediğine ilişkin kanıt var";
            case CONFLICT: return "Çelişkili";
            case UNKNOWN: return "Bilinmiyor";
            default: throw new AssertionError(support);
        }
    }

    public String lockExplanation() {
        String explanation;
        switch (lock) {
            case LOCKED:
            case UNLOCKED:
                explanation = "Sonuç yalnızca ro.boot.flash.locked ve/veya "
                        + "ro.boot.vbmeta.device_state doğrudan firmware bildirimine dayanır. ";
                if (verifiedContextDisagrees) {
                    explanation += "Verified Boot bağlamı bu bildirimle uyuşmuyor; doğrudan "
                            + "bildirim korunurken güven düzeyi düşürüldü. Bu uyumsuzluk "
                            + "kesin bir donanım sonucu çıkarılmasını engeller. ";
                }
                break;
            case CONFLICT:
                explanation = "Doğrudan kilit bildirimleri birbirine zıt; tek bir kilit sonucu "
                        + "çıkarılmadı. Verified Boot bu çelişkiyi çözmek için kullanılmaz. ";
                break;
            case UNKNOWN:
                explanation = "Tanınan doğrudan bir kilit bildirimi okunamadı. Eksik, erişilemeyen "
                        + "ve tanınmayan değerler kilitli veya açık sayılmaz. Verified Boot ve "
                        + "dm-verity tek başına kesin kilit kararı verdirmez. ";
                break;
            default: throw new AssertionError(lock);
        }
        return explanation + "Güven düzeyi yalnızca okunan firmware kanıtının gücünü anlatır; "
                + "donanımsal doğrulama değildir. Değiştirilmiş yazılım yanlış değer bildirebilir.";
    }

    public String supportExplanation() {
        String explanation;
        switch (support) {
            case SUPPORTED_EVIDENCE:
                explanation = "ro.oem_unlock_supported=1, firmware'in destek bildirimidir. ";
                break;
            case UNSUPPORTED_EVIDENCE:
                explanation = "ro.oem_unlock_supported=0, mevcut firmware'in destek sunmadığına "
                        + "ilişkin bildirimdir; tüm yöntemlerin imkânsız olduğunu kanıtlamaz. ";
                break;
            case CONFLICT:
                explanation = "Destek / izin bildirimleri tutarsız: zıt destek değerleri, zıt "
                        + "izin değerleri veya destek yokken izin açık bildirimi var. Bu sonuç "
                        + "farklı bildirimlerin uyuşmadığını gösterir; kilit açma uygunluğu "
                        + "sonucu çıkarılmadı. ";
                break;
            case UNKNOWN:
                explanation = "Tanınan bir destek bildirimi okunamadı. OEM izin sinyali tek "
                        + "başına açılabilirlik desteği olarak değerlendirilmez. ";
                break;
            default: throw new AssertionError(support);
        }
        if (permissionEnabled) {
            explanation += "OEM izin sinyali açık bildiriliyor; bu bir açılabilirlik garantisi değildir. ";
        }
        if (permissionDisabled) {
            explanation += "OEM izin sinyali kapalı bildiriliyor; kapalı izin, destek yok demek değildir. ";
        }
        return explanation + "Destek, mevcut izin, üretici onayı ve gerçekten kilit açabilme "
                + "imkânı farklıdır. Hiçbir özellik değeri kilidin kesin açılacağını veya bir "
                + "kilit açma kodunun alınabileceğini kanıtlamaz. Kilit durumu ayrı değerlendirilir. "
                + "Güven düzeyi yalnızca firmware bildirimine ilişkindir.";
    }

    public String lockConfidenceLabel() {
        return confidenceLabel(lockConfidence);
    }

    public String supportConfidenceLabel() {
        return confidenceLabel(supportConfidence);
    }

    private static String confidenceLabel(Confidence confidence) {
        switch (confidence) {
            case HIGH: return "Yüksek (yalnızca firmware kanıtı)";
            case MEDIUM: return "Orta (yalnızca firmware kanıtı)";
            case LOW: return "Düşük (uyuşmazlık var)";
            case NONE: return "Belirlenemedi (yeterli kanıt yok)";
            default: throw new AssertionError(confidence);
        }
    }
}
