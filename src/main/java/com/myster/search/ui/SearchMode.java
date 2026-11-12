package com.myster.search.ui;

/** Search input selection, with stable preference values independent of labels or enum order. */
public enum SearchMode {
    FILES("Files", "files"),
    SERVERS("Servers", "servers");

    private final String label;
    private final String preferenceValue;

    SearchMode(String label, String preferenceValue) {
        this.label = label;
        this.preferenceValue = preferenceValue;
    }

    public String preferenceValue() {
        return preferenceValue;
    }

    /** Old or unrecognized preferences preserve the default file-search behavior. */
    public static SearchMode fromPreference(String value) {
        return SERVERS.preferenceValue.equals(value) ? SERVERS : FILES;
    }

    @Override
    public String toString() {
        return label;
    }
}
