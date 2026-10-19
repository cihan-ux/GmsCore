package tr.cihan.bootloaderchecker.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class ReportFormatterTest {
    private static DeviceInfo huawei() {
        return new DeviceInfo("HUAWEI", "AQM-L21A", "10", "EmotionUI_12.0.0", "AQM-L21A 12.0.0");
    }

    @Test public void huaweiReportExplains2018ServiceClosureAndNoUnlockGuarantee() {
        Assessment assessment = Assessment.evaluate(Arrays.asList(
                PropertyReading.value(Assessment.OEM_SUPPORT, "1"),
                PropertyReading.value(Assessment.OEM_ALLOWED, "1")));
        String report = ReportFormatter.format(huawei(), assessment);
        assertTrue(report.contains("Huawei, resmî bootloader kilit açma kodu hizmetini 2018 yılında sonlandırdı"));
        assertTrue(report.contains("Huawei P Smart S (AQM-L21A)"));
        assertTrue(report.contains("Üçüncü taraf yöntemlerle"));
        assertTrue(report.contains("kesin açılabileceği iddia edilmez"));
        assertTrue(report.contains("resmî kod alınabileceğini"));
        assertTrue(report.contains("garantisi değildir"));
    }

    @Test public void huaweiModelNameDoesNotBecomeUnsupportedOrLockedEvidence() {
        Assessment assessment = Assessment.evaluate(Collections.emptyList());
        String report = ReportFormatter.format(huawei(), assessment);
        assertEquals(Assessment.Lock.UNKNOWN, assessment.lock);
        assertEquals(Assessment.Support.UNKNOWN, assessment.support);
        assertTrue(report.contains("Bootloader kilit durumu: Bilinmiyor"));
        assertTrue(report.contains("Bootloader açılabilirlik değerlendirmesi: Bilinmiyor"));
    }

    @Test public void reportIncludesDeviceVersionsSeparateOutcomesAndConfidence() {
        Assessment assessment = Assessment.evaluate(Arrays.asList(
                PropertyReading.value(Assessment.FLASH_LOCKED, "1"),
                PropertyReading.value(Assessment.OEM_SUPPORT, "1")));
        String report = ReportFormatter.format(huawei(), assessment);
        assertTrue(report.contains("Üretici: HUAWEI"));
        assertTrue(report.contains("Model: AQM-L21A"));
        assertTrue(report.contains("Android sürümü: 10"));
        assertTrue(report.contains("EMUI sürümü: EmotionUI_12.0.0"));
        assertTrue(report.contains("Derleme: AQM-L21A 12.0.0"));
        assertTrue(report.contains("Bootloader kilit durumu: Kilitli"));
        assertTrue(report.contains("Açılabilirlik desteğine ilişkin kanıt var"));
        assertTrue(report.contains("Kilit değerlendirmesi güven düzeyi:"));
        assertTrue(report.contains("Destek değerlendirmesi güven düzeyi:"));
    }

    @Test public void reportIncludesEveryReadingRawStatusInterpretationAndTarget() {
        Assessment assessment = Assessment.evaluate(Arrays.asList(
                PropertyReading.value(Assessment.FLASH_LOCKED, " 1 "),
                PropertyReading.empty(Assessment.VBMETA_STATE),
                PropertyReading.error(Assessment.OEM_SUPPORT, "Erişim reddedildi"),
                PropertyReading.timeout(Assessment.OEM_ALLOWED)));
        String report = ReportFormatter.format(huawei(), assessment);
        for (Evidence evidence : assessment.evidence) {
            assertTrue(report.contains(evidence.reading.key));
            assertTrue(report.contains("Erişim durumu: " + evidence.reading.statusLabel()));
            assertTrue(report.contains("Yorum: " + evidence.interpretation));
            assertTrue(report.contains("Kanıtın etkisi: " + evidence.targetLabel));
        }
        assertTrue(report.contains("Ham değer:  1 "));
        assertTrue(report.contains("Ham değer: (boş)"));
        assertTrue(report.contains("Okuma ayrıntısı: Erişim reddedildi"));
        assertTrue(report.contains("Zaman aşımı"));
    }

    @Test public void reportEscapesRawLineBreaksToKeepPropertyBoundariesClear() {
        Assessment assessment = Assessment.evaluate(Collections.singletonList(
                PropertyReading.value("ro.example", "ilk\nikinci\rson")));
        String report = ReportFormatter.format(huawei(), assessment);
        assertTrue(report.contains("Ham değer: ilk\\nikinci\\rson"));
        assertFalse(report.contains("Ham değer: ilk\nikinci"));
    }

    @Test public void reportShowsMissingMetadataAsUnknownInsteadOfGuessingEmui() {
        DeviceInfo unavailable = new DeviceInfo(null, "", "10", null, " ");
        String report = ReportFormatter.format(unavailable,
                Assessment.evaluate(Collections.emptyList()));
        assertTrue(report.contains("Üretici: Bilinmiyor"));
        assertTrue(report.contains("Model: Bilinmiyor"));
        assertTrue(report.contains("EMUI sürümü: Bilinmiyor"));
        assertTrue(report.contains("Derleme: Bilinmiyor"));
    }

    @Test public void secondScanReportDoesNotContainFirstScanSuccessfulEvidence() {
        Assessment first = Assessment.evaluate(Collections.singletonList(
                PropertyReading.value(Assessment.FLASH_LOCKED, "1")));
        Assessment second = Assessment.evaluate(Collections.singletonList(
                PropertyReading.error(Assessment.FLASH_LOCKED, "Yeni taramada erişim yok")));
        assertTrue(ReportFormatter.format(huawei(), first).contains("Bootloader kilit durumu: Kilitli"));
        String report = ReportFormatter.format(huawei(), second);
        assertTrue(report.contains("Bootloader kilit durumu: Bilinmiyor"));
        assertTrue(report.contains("Yeni taramada erişim yok"));
        assertFalse(report.contains("Ham değer: 1\n"));
        assertFalse(report.contains("Kilit durumu: Kilitli kanıtı"));
    }

    @Test public void reportExplainsLocalReadOnlyOperationAndUserInitiatedCopy() {
        String report = ReportFormatter.format(huawei(), Assessment.evaluate(Collections.emptyList()));
        assertTrue(report.contains("yerel ve salt okunur"));
        assertTrue(report.contains("root veya internet izni istemez"));
        assertTrue(report.contains("cihazı yeniden başlatmaz"));
        assertTrue(report.contains("otomatik olarak dışarı göndermez"));
        assertTrue(report.contains("kullanıcının düğmeye basmasıyla"));
    }
}
