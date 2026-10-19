package tr.cihan.bootloaderchecker.core;

/** Informational device metadata, never used as evidence of lock or unlock support. */
public final class DeviceInfo {
    public final String manufacturer;
    public final String model;
    public final String androidVersion;
    public final String emuiVersion;
    public final String buildDisplay;

    public DeviceInfo(String manufacturer, String model, String androidVersion,
            String emuiVersion, String buildDisplay) {
        this.manufacturer = display(manufacturer);
        this.model = display(model);
        this.androidVersion = display(androidVersion);
        this.emuiVersion = display(emuiVersion);
        this.buildDisplay = display(buildDisplay);
    }

    private static String display(String value) {
        return value == null || value.trim().isEmpty() ? "Bilinmiyor" : value;
    }
}
