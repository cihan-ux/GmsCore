// SPDX-License-Identifier: Apache-2.0
package org.microg.gms.common;

/** Host-only stream shim. Android runtime behavior is covered by APK builds. */
public final class Utils {
    public static byte[] readStreamToEnd(java.io.InputStream stream) throws java.io.IOException {
        return stream.readAllBytes();
    }
}
