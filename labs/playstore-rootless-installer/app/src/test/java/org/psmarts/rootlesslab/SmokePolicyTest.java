/* SPDX-License-Identifier: Apache-2.0 */
package org.psmarts.rootlesslab;

import static org.junit.Assert.*;
import org.junit.Test;

public class SmokePolicyTest {
    @Test public void permitsOnlyFreshPermissionlessSmokeApk() {
        assertNull(SmokePolicy.rejectionReason(
                SmokePolicy.TEST_PACKAGE, new String[0], false));
    }

    @Test public void blocksPlayStoreAndGoogleServices() {
        assertNotNull(SmokePolicy.rejectionReason("com.android.vending", new String[0], false));
        assertNotNull(SmokePolicy.rejectionReason("com.google.android.gms", new String[0], false));
        assertNotNull(SmokePolicy.rejectionReason("app.revanced.android.gms", new String[0], false));
    }

    @Test public void blocksMissingAndUnknownIdentity() {
        assertNotNull(SmokePolicy.rejectionReason(null, new String[0], false));
        assertNotNull(SmokePolicy.rejectionReason("com.openai.chatgpt", new String[0], false));
    }

    @Test public void blocksDeclaredPermissionsAndUnknownPermissions() {
        assertNotNull(SmokePolicy.rejectionReason(
                SmokePolicy.TEST_PACKAGE, new String[]{"android.permission.INTERNET"}, false));
        assertNotNull(SmokePolicy.rejectionReason(SmokePolicy.TEST_PACKAGE, null, false));
    }

    @Test public void blocksUpdatesEvenToSmokeApp() {
        assertNotNull(SmokePolicy.rejectionReason(
                SmokePolicy.TEST_PACKAGE, new String[0], true));
    }
}
