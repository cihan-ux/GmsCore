// SPDX-License-Identifier: Apache-2.0
package android.net;

/** Host-only URI encoding shim for synthetic, unreserved request values. */
public final class Uri {
    public static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
