// SPDX-License-Identifier: Apache-2.0
package android.util;

/** Host-only log sink. Not shipped in the application. */
public final class Log {
    public static final StringBuilder messages = new StringBuilder();
    public static int d(String tag, String message) {
        messages.append(tag).append(':').append(message).append('\n');
        return 0;
    }
    public static int w(String tag, String message) { return d(tag, message); }
    public static int w(String tag, Throwable error) { return d(tag, error.toString()); }
}
