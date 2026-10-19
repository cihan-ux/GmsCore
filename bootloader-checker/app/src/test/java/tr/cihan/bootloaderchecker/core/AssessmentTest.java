package tr.cihan.bootloaderchecker.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class AssessmentTest {
    private static Assessment evaluate(PropertyReading... readings) {
        return Assessment.evaluate(Arrays.asList(readings));
    }

    private static PropertyReading value(String key, String value) {
        return PropertyReading.value(key, value);
    }

    @Test public void flashOneReportsLocked() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "1"));
        assertEquals(Assessment.Lock.LOCKED, result.lock);
        assertEquals("Kilitli", result.lockTitle());
        assertEquals(Assessment.Confidence.MEDIUM, result.lockConfidence);
    }

    @Test public void flashZeroReportsUnlocked() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "0"));
        assertEquals(Assessment.Lock.UNLOCKED, result.lock);
        assertEquals("Kilit açık", result.lockTitle());
    }

    @Test public void vbmetaLockedReportsLocked() {
        assertEquals(Assessment.Lock.LOCKED,
                evaluate(value(Assessment.VBMETA_STATE, "locked")).lock);
    }

    @Test public void vbmetaUnlockedReportsUnlocked() {
        assertEquals(Assessment.Lock.UNLOCKED,
                evaluate(value(Assessment.VBMETA_STATE, "unlocked")).lock);
    }

    @Test public void missingPropertiesGiveUnknownResults() {
        Assessment result = Assessment.evaluate(Collections.emptyList());
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertEquals(Assessment.Support.UNKNOWN, result.support);
        assertEquals(Assessment.Confidence.NONE, result.lockConfidence);
        assertEquals(Assessment.Confidence.NONE, result.supportConfidence);
    }

    @Test public void emptyPropertiesAreNotNegativeEvidence() {
        Assessment result = evaluate(PropertyReading.empty(Assessment.FLASH_LOCKED),
                PropertyReading.empty(Assessment.OEM_SUPPORT));
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertEquals(Assessment.Support.UNKNOWN, result.support);
        assertTrue(result.evidence.get(0).interpretation.contains("ayırt edilemez"));
    }

    @Test public void opposingDirectSignalsAreConflicting() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "1"),
                value(Assessment.VBMETA_STATE, "unlocked"));
        assertEquals(Assessment.Lock.CONFLICT, result.lock);
        assertEquals(Assessment.Confidence.LOW, result.lockConfidence);
        assertEquals("Çelişkili", result.lockTitle());
    }

    @Test public void reverseOpposingDirectSignalsAreConflicting() {
        assertEquals(Assessment.Lock.CONFLICT, evaluate(value(Assessment.FLASH_LOCKED, "0"),
                value(Assessment.VBMETA_STATE, "locked")).lock);
    }

    @Test public void agreeingDirectSignalsStayConsistent() {
        Assessment locked = evaluate(value(Assessment.FLASH_LOCKED, "1"),
                value(Assessment.VBMETA_STATE, "locked"));
        Assessment unlocked = evaluate(value(Assessment.FLASH_LOCKED, "0"),
                value(Assessment.VBMETA_STATE, "unlocked"));
        assertEquals(Assessment.Lock.LOCKED, locked.lock);
        assertEquals(Assessment.Lock.UNLOCKED, unlocked.lock);
        // Multiple software properties do not become hardware attestation.
        assertEquals(Assessment.Confidence.MEDIUM, locked.lockConfidence);
        assertEquals(Assessment.Confidence.MEDIUM, unlocked.lockConfidence);
    }

    @Test public void verifiedBootAloneNeverDecidesLock() {
        for (String color : Arrays.asList("green", "yellow", "orange", "red")) {
            Assessment result = evaluate(value(Assessment.VERIFIED_BOOT_STATE, color));
            assertEquals(color, Assessment.Lock.UNKNOWN, result.lock);
            assertEquals(color, Assessment.Confidence.NONE, result.lockConfidence);
            assertTrue(result.evidence.get(0).targetLabel.contains("bağlam"));
        }
    }

    @Test public void verifiedBootDoesNotResolveStrongConflict() {
        assertEquals(Assessment.Lock.CONFLICT, evaluate(value(Assessment.FLASH_LOCKED, "1"),
                value(Assessment.VBMETA_STATE, "unlocked"),
                value(Assessment.VERIFIED_BOOT_STATE, "green")).lock);
    }

    @Test public void contraryVerifiedBootLowersConfidenceAndIsDisclosed() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "1"),
                value(Assessment.VERIFIED_BOOT_STATE, "orange"));
        assertEquals(Assessment.Lock.LOCKED, result.lock);
        assertEquals(Assessment.Confidence.LOW, result.lockConfidence);
        assertTrue(result.lockExplanation().contains("uyuşmuyor"));
        assertTrue(result.lockExplanation().contains("güven düzeyi düşürüldü"));
    }

    @Test public void contraryGreenOrYellowDoesNotOverrideUnlockedSignal() {
        for (String color : Arrays.asList("green", "yellow")) {
            Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "0"),
                    value(Assessment.VERIFIED_BOOT_STATE, color));
            assertEquals(color, Assessment.Lock.UNLOCKED, result.lock);
            assertEquals(color, Assessment.Confidence.LOW, result.lockConfidence);
        }
    }

    @Test public void matchingVerifiedBootDoesNotCreateArtificialCertainty() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "1"),
                value(Assessment.VERIFIED_BOOT_STATE, "green"));
        assertEquals(Assessment.Confidence.MEDIUM, result.lockConfidence);
        assertTrue(result.lockExplanation().contains("donanımsal doğrulama değildir"));
    }

    @Test public void verityModeIsContextOnly() {
        for (String mode : Arrays.asList("enforcing", "logging", "eio", "restart")) {
            Assessment result = evaluate(value(Assessment.VERITY_MODE, mode));
            assertEquals(mode, Assessment.Lock.UNKNOWN, result.lock);
            assertEquals(mode, Assessment.Support.UNKNOWN, result.support);
            assertTrue(result.evidence.get(0).interpretation.contains("kanıtı değildir"));
        }
    }

    @Test public void oemSupportOneIsEvidenceWithoutUnlockPromise() {
        Assessment result = evaluate(value(Assessment.OEM_SUPPORT, "1"));
        assertEquals(Assessment.Support.SUPPORTED_EVIDENCE, result.support);
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertTrue(result.supportTitle().contains("kanıt var"));
        assertTrue(result.supportExplanation().contains("kilidin kesin açılacağını"));
        assertTrue(result.supportExplanation().contains("kanıtlamaz"));
        assertTrue(result.evidence.get(0).interpretation.contains("garantisi değildir"));
    }

    @Test public void oemSupportZeroIsLimitedUnsupportedEvidence() {
        Assessment result = evaluate(value(Assessment.OEM_SUPPORT, "0"));
        assertEquals(Assessment.Support.UNSUPPORTED_EVIDENCE, result.support);
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertTrue(result.supportExplanation().contains("tüm yöntemlerin imkânsız olduğunu kanıtlamaz"));
    }

    @Test public void enabledPermissionAloneDoesNotProveSupportOrUnlock() {
        Assessment result = evaluate(value(Assessment.OEM_ALLOWED, "1"));
        assertEquals(Assessment.Support.UNKNOWN, result.support);
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertTrue(result.supportExplanation().contains("izin sinyali açık"));
        assertTrue(result.supportExplanation().contains("garantisi değildir"));
    }

    @Test public void disabledPermissionDoesNotMeanUnsupportedDevice() {
        Assessment result = evaluate(value(Assessment.OEM_ALLOWED, "0"));
        assertEquals(Assessment.Support.UNKNOWN, result.support);
        assertTrue(result.supportExplanation().contains("kapalı izin, destek yok demek değildir"));
    }

    @Test public void supportedButPermissionDisabledRetainsSupportEvidence() {
        Assessment result = evaluate(value(Assessment.OEM_SUPPORT, "1"),
                value(Assessment.OEM_ALLOWED, "0"));
        assertEquals(Assessment.Support.SUPPORTED_EVIDENCE, result.support);
        assertTrue(result.supportExplanation().contains("izin sinyali kapalı"));
        assertTrue(result.supportExplanation().contains("farklıdır"));
    }

    @Test public void unsupportedButPermissionEnabledDisclosesInconsistentData() {
        Assessment result = evaluate(value(Assessment.OEM_SUPPORT, "0"),
                value(Assessment.OEM_ALLOWED, "1"));
        assertEquals(Assessment.Support.CONFLICT, result.support);
        assertEquals(Assessment.Confidence.LOW, result.supportConfidence);
        assertTrue(result.supportExplanation().contains("destek yokken izin açık"));
        assertTrue(result.supportExplanation().contains("uygunluğu sonucu çıkarılmadı"));
    }

    @Test public void opposingSupportObservationsRemainVisible() {
        Assessment result = evaluate(value(Assessment.OEM_SUPPORT, "1"),
                value(Assessment.OEM_SUPPORT, "0"));
        assertEquals(Assessment.Support.CONFLICT, result.support);
        assertEquals(2, result.evidence.size());
    }

    @Test public void opposingPermissionObservationsAreNotSilentlyOverwritten() {
        Assessment result = evaluate(value(Assessment.OEM_ALLOWED, "1"),
                value(Assessment.OEM_ALLOWED, "0"));
        assertEquals(Assessment.Support.CONFLICT, result.support);
        assertEquals(2, result.evidence.size());
    }

    @Test public void oemInformationNeverChangesLockConclusion() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "1"),
                value(Assessment.OEM_SUPPORT, "1"), value(Assessment.OEM_ALLOWED, "1"));
        assertEquals(Assessment.Lock.LOCKED, result.lock);
        assertEquals(Assessment.Support.SUPPORTED_EVIDENCE, result.support);
    }

    @Test public void unlockedBootloaderDoesNotProveReunlockSupport() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "0"));
        assertEquals(Assessment.Lock.UNLOCKED, result.lock);
        assertEquals(Assessment.Support.UNKNOWN, result.support);
    }

    @Test public void accessErrorsGiveUnknownInsteadOfAssumedValues() {
        Assessment result = evaluate(PropertyReading.error(Assessment.FLASH_LOCKED, "Erişim reddedildi"),
                PropertyReading.error(Assessment.OEM_SUPPORT, "İşlem başlatılamadı"));
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertEquals(Assessment.Support.UNKNOWN, result.support);
        assertTrue(result.evidence.get(0).interpretation.contains("hata"));
        assertEquals("Erişim reddedildi", result.evidence.get(0).reading.detail);
    }

    @Test public void partialRawValueFromFailedReadIsNotUsed() {
        Assessment result = evaluate(new PropertyReading(Assessment.FLASH_LOCKED, "1",
                PropertyReading.Status.ERROR, "Komut hata koduyla bitti"));
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertEquals("1", result.evidence.get(0).reading.raw);
    }

    @Test public void timeoutsGiveUnknownAndExplainWhy() {
        Assessment result = evaluate(PropertyReading.timeout(Assessment.FLASH_LOCKED),
                PropertyReading.timeout(Assessment.OEM_SUPPORT));
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertEquals(Assessment.Support.UNKNOWN, result.support);
        assertTrue(result.evidence.get(0).interpretation.contains("zamanında tamamlanmadı"));
    }

    @Test public void invalidValuesArePreservedWithoutGuessing() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "true"),
                value(Assessment.VBMETA_STATE, "secure"), value(Assessment.OEM_SUPPORT, "yes"),
                value(Assessment.OEM_ALLOWED, "enabled"));
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertEquals(Assessment.Support.UNKNOWN, result.support);
        assertEquals("true", result.evidence.get(0).reading.raw);
        for (Evidence evidence : result.evidence) {
            assertTrue(evidence.interpretation.contains("tanınmayan"));
        }
    }

    @Test public void normalizationDoesNotDestroyRawEvidence() {
        String raw = "  UNLOCKED  ";
        Assessment result = evaluate(value(Assessment.VBMETA_STATE, raw));
        assertEquals(Assessment.Lock.UNLOCKED, result.lock);
        assertEquals(raw, result.evidence.get(0).reading.raw);
    }

    @Test public void nullAndWhitespaceValuesBecomeEmptyReadings() {
        PropertyReading nullValue = value(Assessment.FLASH_LOCKED, null);
        PropertyReading blankValue = value(Assessment.OEM_SUPPORT, "  ");
        assertEquals(PropertyReading.Status.EMPTY, nullValue.status);
        assertEquals(PropertyReading.Status.EMPTY, blankValue.status);
        assertEquals(Assessment.Lock.UNKNOWN, evaluate(nullValue, blankValue).lock);
    }

    @Test public void unrelatedBootPropertiesDoNotDecideEitherResult() {
        Assessment result = evaluate(value("ro.boot.secureboot", "0"),
                value("ro.secure", "0"), value("ro.debuggable", "1"),
                value("ro.boot.bootloader", "unlocked"), value("ro.boot.avb_version", "1.0"));
        assertEquals(Assessment.Lock.UNKNOWN, result.lock);
        assertEquals(Assessment.Support.UNKNOWN, result.support);
    }

    @Test public void newScanDoesNotReusePreviousSuccessfulReadings() {
        Assessment first = evaluate(value(Assessment.FLASH_LOCKED, "1"),
                value(Assessment.OEM_SUPPORT, "1"));
        Assessment second = evaluate(PropertyReading.error(Assessment.FLASH_LOCKED, "Okuma hatası"),
                PropertyReading.empty(Assessment.OEM_SUPPORT));
        assertEquals(Assessment.Lock.LOCKED, first.lock);
        assertEquals(Assessment.Support.SUPPORTED_EVIDENCE, first.support);
        assertEquals(Assessment.Lock.UNKNOWN, second.lock);
        assertEquals(Assessment.Support.UNKNOWN, second.support);
        assertEquals(2, second.evidence.size());
        assertEquals(PropertyReading.Status.ERROR, second.evidence.get(0).reading.status);
    }

    @Test public void newScanCanChangeToOppositeResultWithoutStaleConflict() {
        Assessment first = evaluate(value(Assessment.FLASH_LOCKED, "1"));
        Assessment second = evaluate(value(Assessment.FLASH_LOCKED, "0"));
        assertEquals(Assessment.Lock.LOCKED, first.lock);
        assertEquals(Assessment.Lock.UNLOCKED, second.lock);
        assertEquals(1, second.evidence.size());
    }

    @Test public void modifyingSourceListCannotChangeAnExistingReport() {
        List<PropertyReading> source = new ArrayList<>();
        source.add(value(Assessment.FLASH_LOCKED, "1"));
        Assessment first = Assessment.evaluate(source);
        source.clear();
        source.add(value(Assessment.FLASH_LOCKED, "0"));
        assertEquals("1", first.evidence.get(0).reading.raw);
        assertEquals(Assessment.Lock.LOCKED, first.lock);
        assertEquals(Assessment.Lock.UNLOCKED, Assessment.evaluate(source).lock);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void evidenceSnapshotIsImmutable() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "1"));
        result.evidence.clear();
    }

    @Test public void confidenceIsNeverHardwareCertainty() {
        Assessment result = evaluate(value(Assessment.FLASH_LOCKED, "1"),
                value(Assessment.VBMETA_STATE, "locked"), value(Assessment.OEM_SUPPORT, "1"));
        assertFalse(result.lockConfidence == Assessment.Confidence.HIGH);
        assertFalse(result.supportConfidence == Assessment.Confidence.HIGH);
        assertTrue(result.lockConfidenceLabel().contains("firmware"));
        assertTrue(result.lockExplanation().contains("yanlış değer bildirebilir"));
    }
}
