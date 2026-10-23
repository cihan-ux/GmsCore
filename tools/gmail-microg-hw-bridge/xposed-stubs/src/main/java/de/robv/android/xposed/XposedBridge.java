package de.robv.android.xposed;
import java.util.Set;
public final class XposedBridge {
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> cls, String name, XC_MethodHook hook) { throw new UnsupportedOperationException("compile only"); }
    public static void log(String message) { throw new UnsupportedOperationException("compile only"); }
    public static void log(Throwable error) { throw new UnsupportedOperationException("compile only"); }
}
