package tr.cihan.bootloaderchecker.core;

/** Produces the same complete Turkish report for screen and clipboard, without Android APIs. */
public final class ReportFormatter {
    public static final String HUAWEI_NOTICE = "Huawei, resmî bootloader kilit açma kodu "
            + "hizmetini 2018 yılında sonlandırdı. Huawei P Smart S (AQM-L21A) için bir "
            + "destek veya izin sinyali görülmesi, resmî kod alınabileceğini veya kilidin "
            + "açılabileceğini göstermez. Üçüncü taraf yöntemlerle bu cihazın kilidinin "
            + "kesin açılabileceği iddia edilmez. Bu uygulama kilit açmaz ve ayar değiştirmez.";

    private ReportFormatter() { }

    public static String format(DeviceInfo device, Assessment assessment) {
        if (device == null || assessment == null) {
            throw new IllegalArgumentException("Cihaz ve değerlendirme bilgileri gereklidir.");
        }
        StringBuilder report = new StringBuilder("BOOTLOADER CHECKER TR\n\n");
        report.append("Üretici: ").append(device.manufacturer).append('\n');
        report.append("Model: ").append(device.model).append('\n');
        report.append("Android sürümü: ").append(device.androidVersion).append('\n');
        report.append("EMUI sürümü: ").append(device.emuiVersion).append('\n');
        report.append("Derleme: ").append(device.buildDisplay).append("\n\n");
        report.append("Bootloader kilit durumu: ").append(assessment.lockTitle()).append('\n');
        report.append("Kilit değerlendirmesi güven düzeyi: ")
                .append(assessment.lockConfidenceLabel()).append('\n');
        report.append(assessment.lockExplanation()).append("\n\n");
        report.append("Bootloader açılabilirlik değerlendirmesi: ")
                .append(assessment.supportTitle()).append('\n');
        report.append("Destek değerlendirmesi güven düzeyi: ")
                .append(assessment.supportConfidenceLabel()).append('\n');
        report.append(assessment.supportExplanation()).append("\n\n");
        report.append("KANITLAR VE HAM ÖZELLİKLER\n");
        if (assessment.evidence.isEmpty()) {
            report.append("Bu taramada özellik sonucu yok.\n");
        }
        for (Evidence evidence : assessment.evidence) {
            PropertyReading reading = evidence.reading;
            report.append('\n').append(reading.key).append('\n');
            report.append("Ham değer: ").append(escape(reading.rawDisplay())).append('\n');
            report.append("Erişim durumu: ").append(reading.statusLabel()).append('\n');
            if (!reading.detail.isEmpty()) {
                report.append("Okuma ayrıntısı: ").append(escape(reading.detail)).append('\n');
            }
            report.append("Yorum: ").append(evidence.interpretation).append('\n');
            report.append("Kanıtın etkisi: ").append(evidence.targetLabel).append('\n');
        }
        report.append("\nHUAWEI UYARISI\n").append(HUAWEI_NOTICE).append("\n\n");
        report.append("Bu rapor yalnızca bu taramanın yerel ve salt okunur sonuçlarını içerir. ")
                .append("Uygulama root veya internet izni istemez, cihazı yeniden başlatmaz ve ")
                .append("verileri otomatik olarak dışarı göndermez. Panoya kopyalama yalnızca ")
                .append("kullanıcının düğmeye basmasıyla yapılır. Normal Android uygulamaları ")
                .append("bazı boot özelliklerini okuyamayabilir. Bilinmiyor sonucu cihazın ")
                .append("kilitli, açık, destekli veya desteksiz olduğunu kanıtlamaz.\n");
        return report.toString();
    }

    private static String escape(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
