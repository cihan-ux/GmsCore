package dev.cihan.gmshwassistant;

import static org.junit.Assert.*;
import org.junit.Test;

public class DiagnosticRulesTest {
    @Test public void walletDebugIsIdentified() {
        assertTrue(DiagnosticRules.signatureLabel("app.revanced.android.gms",
                DiagnosticRules.WALLET_A_DEBUG_CERT).contains("Wallet A"));
    }
    @Test public void morpheIsIdentified() {
        assertTrue(DiagnosticRules.signatureLabel("app.revanced.android.gms",
                DiagnosticRules.MORPHE_CERT).contains("Morphe"));
    }
    @Test public void unrelatedPackageIsNotMisidentified() {
        assertTrue(DiagnosticRules.signatureLabel("com.android.vending",
                DiagnosticRules.WALLET_A_DEBUG_CERT).contains("tanimli degil"));
    }
    @Test public void unknownSignatureStaysUnknown() {
        assertTrue(DiagnosticRules.signatureLabel("app.revanced.android.gms",
                "deadbeef").contains("tanimlanmamis"));
    }
    @Test public void noServiceNeverMeansNoSupport() {
        assertTrue(DiagnosticRules.spoofingService(false).contains("kesin degil"));
    }
    @Test public void permissionIsNotPresentedAsSufficient() {
        assertTrue(DiagnosticRules.protectedPermission(true, true).contains("kaniti degil"));
    }
    @Test public void requestedPrivilegedPermissionDoesNotClaimAdbFix() {
        assertTrue(DiagnosticRules.permissionState(true, false, "android.permission.MANAGE_USERS")
                .contains("normal ADB ile verilemez"));
    }
    @Test public void unrequestedPermissionIsNotFlaggedMissing() {
        assertTrue(DiagnosticRules.permissionState(false, false, "android.permission.MANAGE_USERS")
                .contains("eksik izin olarak yorumlanamaz"));
    }
    @Test public void developmentPermissionDoesNotPromiseSuccess() {
        assertTrue(DiagnosticRules.permissionState(true, false, "android.permission.INTERACT_ACROSS_USERS")
                .contains("denenebilir"));
    }
    @Test public void grantedPermissionDoesNotClaimWorkingPlayStore() {
        assertTrue(DiagnosticRules.permissionState(true, true, "android.permission.MANAGE_USERS")
                .contains("kaniti degil"));
    }
    @Test public void accountsAreNotInferredFromAuthenticator() {
        assertTrue(DiagnosticRules.accountType(true).contains("kanitlamaz"));
    }
}
