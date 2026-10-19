package tr.cihan.bootloaderchecker.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FastbootEvidenceTest {
    @Test public void huaweiGetBootinfoLocked() {
        FastbootEvidence result = FastbootEvidence.parse(
                "(bootloader) locked\\nOKAY [  0.010s]\\nFinished. Total time: 0.018s");
        assertEquals(FastbootEvidence.Lock.LOCKED, result.lock);
        assertEquals(1, result.recognizedLines);
    }

    @Test public void huaweiGetBootinfoUnlocked() {
        assertEquals(FastbootEvidence.Lock.UNLOCKED,
                FastbootEvidence.parse("(bootloader) unlocked").lock);
    }

    @Test public void acceptsExplicitGetvarAndLockState() {
        assertEquals(FastbootEvidence.Lock.UNLOCKED,
                FastbootEvidence.parse("(bootloader) unlocked: yes").lock);
        assertEquals(FastbootEvidence.Lock.LOCKED,
                FastbootEvidence.parse("unlocked: no").lock);
        assertEquals(FastbootEvidence.Lock.LOCKED,
                FastbootEvidence.parse("(bootloader) Lock State: LOCKED").lock);
        assertEquals(FastbootEvidence.Lock.UNLOCKED,
                FastbootEvidence.parse("(bootloader) FB LockState: UNLOCKED").lock);
    }

    @Test public void errorsAndFrpDoNotProveStatus() {
        FastbootEvidence result = FastbootEvidence.parse(
                "FAILED (remote: Command not allowed)\\n(bootloader) FRP LockState: LOCKED"
                        + "\\n(bootloader) unlock_ability: 0");
        assertEquals(FastbootEvidence.Lock.UNKNOWN, result.lock);
        assertEquals(0, result.recognizedLines);
    }

    @Test public void doesNotTreatAnyNakedLockedWordAsEvidence() {
        assertEquals(FastbootEvidence.Lock.UNKNOWN,
                FastbootEvidence.parse("locked\\nthis phone is unlocked").lock);
    }

    @Test public void contradictingStatusGivesConflict() {
        FastbootEvidence result = FastbootEvidence.parse(
                "(bootloader) locked\\n(bootloader) unlocked: yes");
        assertEquals(FastbootEvidence.Lock.CONFLICT, result.lock);
        assertEquals(2, result.recognizedLines);
    }

    @Test public void emptyOrLongOutputIsUnknown() {
        assertEquals(FastbootEvidence.Lock.UNKNOWN, FastbootEvidence.parse(null).lock);
        FastbootEvidence big = FastbootEvidence.parse("(bootloader) locked"
                + new String(new char[12_001]).replace('\\0', 'x'));
        assertEquals(FastbootEvidence.Lock.UNKNOWN, big.lock);
        assertTrue(big.inputTooLong);
    }

    @Test public void manualOutputDoesNotOverrideOnDeviceLock() {
        Assessment local = Assessment.evaluate(java.util.Arrays.asList(
                PropertyReading.value(Assessment.FLASH_LOCKED, "1")));
        FastbootEvidence pasted = FastbootEvidence.parse("(bootloader) unlocked");
        assertEquals(Assessment.Lock.LOCKED, local.lock);
        assertTrue(pasted.compare(local).contains("UYUŞMAZLIK"));
        assertTrue(pasted.report(local).contains("Ham metin rapora eklenmez"));
        assertTrue(pasted.report(local).contains("Kilit açılabilirliği"));
    }

    @Test public void matchingManualStatusIsSeparateEvidence() {
        Assessment local = Assessment.evaluate(java.util.Arrays.asList(
                PropertyReading.value(Assessment.FLASH_LOCKED, "1")));
        assertTrue(FastbootEvidence.parse("(bootloader) locked")
                .compare(local).contains("uyumlu"));
    }

    @Test public void unknownOnDeviceStatusDoesNotBecomeCertain() {
        Assessment local = Assessment.evaluate(java.util.Collections.emptyList());
        assertTrue(FastbootEvidence.parse("(bootloader) locked")
                .compare(local).contains("ayrı tutuldu"));
        assertEquals(Assessment.Lock.UNKNOWN, local.lock);
    }
}
