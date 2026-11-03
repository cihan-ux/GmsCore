/* SPDX-License-Identifier: Apache-2.0 */
package org.psmarts.rootlesslab;

/**
 * Fail-closed scope control for the first, disposable installer experiment.
 * No Android dependency: this decision is covered by ordinary JVM tests.
 */
public final class SmokePolicy {
    public static final String TEST_PACKAGE = "org.psmarts.rootlesslab.smoke";
    private SmokePolicy() {}

    /** Null means allowed; otherwise return a fixed, non-sensitive explanation. */
    public static String rejectionReason(String packageName,
                                         String[] requestedPermissions,
                                         boolean alreadyInstalled) {
        if (!TEST_PACKAGE.equals(packageName)) {
            return "Blocked: only the disposable Installer Smoke Test APK is allowed.";
        }
        if (requestedPermissions == null) {
            return "Blocked: APK permission declarations could not be checked.";
        }
        if (requestedPermissions.length != 0) {
            return "Blocked: the smoke APK must request zero permissions.";
        }
        if (alreadyInstalled) {
            return "Blocked: this lab must not replace an installed application.";
        }
        return null;
    }
}
