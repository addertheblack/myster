package com.myster.search.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.OptionalLong;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableLong;
import com.general.mclist.SortableString;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.mml.MessagePak;
import com.myster.search.SearchResult;

/**
 * Picture column handler for the built-in {@code PICT} type.
 *
 * <p>The visible image columns are derived from the picture file-stat keys:
 * resolution from {@code /ImageWidth} and {@code /ImageHeight}, capture date
 * from {@code /ImageTakenAtMillis}, camera from {@code /CameraMake} and
 * {@code /CameraModel}, orientation from {@code /ImageOrientation}, and bit
 * depth from {@code /ImageBitDepth}.
 */
public class ClientImageHandleObject extends ClientGenericHandleObject {
    private static final String[] HEADER_ARRAY = {
            "Resolution", "Taken", "Camera", "Orientation", "Bit Depth"
    };

    private static final int[] HEADER_SIZE = { 110, 140, 170, 90, 80 };

    private static final String KEY_WIDTH = "/ImageWidth";
    private static final String KEY_HEIGHT = "/ImageHeight";
    private static final String KEY_TAKEN = "/ImageTakenAtMillis";
    private static final String KEY_CAMERA_MAKE = "/CameraMake";
    private static final String KEY_CAMERA_MODEL = "/CameraModel";
    private static final String KEY_ORIENTATION = "/ImageOrientation";
    private static final String KEY_BIT_DEPTH = "/ImageBitDepth";

    private final int numOfColumns;

    public ClientImageHandleObject() {
        super();
        numOfColumns = super.getColumnCount();
    }

    @Override
    public int getColumnCount() {
        return HEADER_ARRAY.length + super.getColumnCount();
    }

    @Override
    public String getHeader(int index) {
        if (index < super.getColumnCount()) {
            return super.getHeader(index);
        }
        return HEADER_ARRAY[index - super.getColumnCount()];
    }

    @Override
    public int getHeaderSize(int index) {
        if (index < super.getColumnCount()) {
            return super.getHeaderSize(index);
        }
        return HEADER_SIZE[index - super.getColumnCount()];
    }

    @Override
    public MCListItemInterface<SearchResult> getSearchItem(SearchResult s) {
        return new ImageSearchItem(s);
    }

    @Override
    public ColumnSortable<String> getFileItem(FileRecord record) {
        ColumnSortable<String> base = super.getFileItem(record);
        return new ColumnSortable<String>() {
            @Override
            public Sortable getValueOfColumn(int column) {
                if (column < numOfColumns) {
                    return base.getValueOfColumn(column);
                }
                return imageColumn(column - numOfColumns, record.metaData());
            }

            @Override
            public String getObject() {
                return record.file();
            }
        };
    }

    @Override
    public ColumnSortable<String> getFolderItem(String folderName) {
        ColumnSortable<String> base = super.getFolderItem(folderName);
        return new ColumnSortable<String>() {
            @Override
            public Sortable getValueOfColumn(int column) {
                if (column < numOfColumns) {
                    return base.getValueOfColumn(column);
                }
                return new SortableString("-");
            }

            @Override
            public String getObject() {
                return folderName;
            }
        };
    }

    private static Sortable imageColumn(int imageColumn, MessagePak metadata) {
        return switch (imageColumn) {
            case 0 -> new SortableResolution(metadata.getLong(KEY_WIDTH).orElse(-1L),
                    metadata.getLong(KEY_HEIGHT).orElse(-1L));
            case 1 -> new SortableTimestamp(metadata.getLong(KEY_TAKEN).orElse(-1L));
            case 2 -> new SortableString(camera(metadata.getString(KEY_CAMERA_MAKE).orElse(null),
                    metadata.getString(KEY_CAMERA_MODEL).orElse(null)));
            case 3 -> new SortableOrientation(metadata.getLong(KEY_ORIENTATION).orElse(-1L));
            case 4 -> new SortableBitDepth(metadata.getLong(KEY_BIT_DEPTH).orElse(-1L));
            default -> throw new RuntimeException("Column " + imageColumn + " doesn't exist");
        };
    }

    private static Sortable imageColumn(int imageColumn, SearchResult result) {
        return switch (imageColumn) {
            case 0 -> new SortableResolution(parseLong(result.getMetaData(KEY_WIDTH)).orElse(-1L),
                    parseLong(result.getMetaData(KEY_HEIGHT)).orElse(-1L));
            case 1 -> new SortableTimestamp(parseLong(result.getMetaData(KEY_TAKEN)).orElse(-1L));
            case 2 -> new SortableString(camera(result.getMetaData(KEY_CAMERA_MAKE),
                    result.getMetaData(KEY_CAMERA_MODEL)));
            case 3 -> new SortableOrientation(
                    parseLong(result.getMetaData(KEY_ORIENTATION)).orElse(-1L));
            case 4 -> new SortableBitDepth(parseLong(result.getMetaData(KEY_BIT_DEPTH)).orElse(-1L));
            default -> throw new RuntimeException("Column " + imageColumn + " doesn't exist");
        };
    }

    private static String camera(String make, String model) {
        String normalizedMake = normalize(make);
        String normalizedModel = normalize(model);
        if (normalizedMake.equals("-")) {
            return normalizedModel;
        }
        if (normalizedModel.equals("-") || normalizedModel.startsWith(normalizedMake)) {
            return normalizedMake;
        }
        return normalizedMake + " " + normalizedModel;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static OptionalLong parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private class ImageSearchItem extends GenericSearchItem {
        public ImageSearchItem(SearchResult s) {
            super(s);
        }

        @Override
        public Sortable getValueOfColumn(int index) {
            if (index < numOfColumns) {
                return super.getValueOfColumn(index);
            }
            return imageColumn(index - numOfColumns, result);
        }
    }

    static class SortableResolution extends SortableLong {
        private final long width;
        private final long height;

        SortableResolution(long width, long height) {
            super(width > 0 && height > 0 ? width * height : -1);
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            if (width <= 0 || height <= 0) {
                return "-";
            }
            return width + " x " + height;
        }
    }

    static class SortableTimestamp extends SortableLong {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());

        SortableTimestamp(long epochMillis) {
            super(epochMillis);
        }

        @Override
        public String toString() {
            if (number < 0) {
                return "-";
            }
            return FORMATTER.format(Instant.ofEpochMilli(number));
        }
    }

    static class SortableOrientation extends SortableLong {
        SortableOrientation(long orientation) {
            super(orientation);
        }

        @Override
        public String toString() {
            return switch ((int) number) {
                case 1 -> "Normal";
                case 3 -> "180";
                case 6 -> "90 CW";
                case 8 -> "90 CCW";
                default -> number > 0 ? Long.toString(number) : "-";
            };
        }
    }

    static class SortableBitDepth extends SortableLong {
        SortableBitDepth(long bits) {
            super(bits);
        }

        @Override
        public String toString() {
            if (number <= 0) {
                return "-";
            }
            return number + "-bit";
        }
    }
}
