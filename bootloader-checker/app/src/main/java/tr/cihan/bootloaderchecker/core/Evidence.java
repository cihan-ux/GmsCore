package tr.cihan.bootloaderchecker.core;

/** A human-readable explanation of exactly how a reading contributes to assessment. */
public final class Evidence {
    public final PropertyReading reading;
    public final String interpretation;
    public final String targetLabel;

    public Evidence(PropertyReading reading, String interpretation, String targetLabel) {
        this.reading = reading;
        this.interpretation = interpretation;
        this.targetLabel = targetLabel;
    }
}
