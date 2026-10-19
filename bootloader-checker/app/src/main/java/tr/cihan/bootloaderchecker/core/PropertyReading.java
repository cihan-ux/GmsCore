package tr.cihan.bootloaderchecker.core;

/** One immutable result of a read attempt; a failed read never becomes evidence. */
public final class PropertyReading {
    public enum Status { VALUE, EMPTY, ERROR, TIMEOUT }

    public final String key;
    public final String raw;
    public final Status status;
    public final String detail;

    public PropertyReading(String key, String raw, Status status, String detail) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Özellik adı boş olamaz.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Erişim durumu belirtilmelidir.");
        }
        this.key = key;
        this.raw = raw == null ? "" : raw;
        this.status = status == Status.VALUE && this.raw.trim().isEmpty()
                ? Status.EMPTY : status;
        this.detail = detail == null ? "" : detail;
    }

    public static PropertyReading value(String key, String raw) {
        return new PropertyReading(key, raw, Status.VALUE, "");
    }

    public static PropertyReading empty(String key) {
        return new PropertyReading(key, "", Status.EMPTY, "");
    }

    public static PropertyReading error(String key, String detail) {
        return new PropertyReading(key, "", Status.ERROR, detail);
    }

    public static PropertyReading timeout(String key) {
        return new PropertyReading(key, "", Status.TIMEOUT, "Okuma süresi aşıldı.");
    }

    public String statusLabel() {
        switch (status) {
            case VALUE: return "Değer okundu";
            case EMPTY: return "Boş / sunulmuyor";
            case ERROR: return "Erişim / okuma hatası";
            case TIMEOUT: return "Zaman aşımı";
            default: throw new AssertionError(status);
        }
    }

    public String rawDisplay() {
        return raw.isEmpty() ? "(boş)" : raw;
    }
}
