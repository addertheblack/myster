package com.myster.net;

/** Shared character limit for local and advertised server descriptions. */
public final class ServerDescriptionUtils {
    public static final int MAX_LENGTH = 255;

    private ServerDescriptionUtils() {}

    /** Returns a non-null description limited to 255 Unicode code points. */
    public static String truncate(String description) {
        if (description == null) {
            return "";
        }
        int length = description.codePointCount(0, description.length());
        return length <= MAX_LENGTH ? description
                : description.substring(0, description.offsetByCodePoints(0, MAX_LENGTH));
    }
}
