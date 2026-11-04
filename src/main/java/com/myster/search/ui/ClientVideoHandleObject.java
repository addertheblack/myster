package com.myster.search.ui;

import java.util.Locale;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableLong;
import com.general.mclist.SortableString;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.mml.ReadOnlyMessagePak;
import com.myster.search.SearchResult;

/**
 * Video column handler derived from {@code /VideoLengthSec}, {@code /VideoWidth},
 * {@code /VideoHeight}, {@code /VideoCodec}, and {@code /VideoBitRate} file metadata.
 * It appends Duration, Resolution, Codec, and Bit Rate to the generic file columns.
 */
public class ClientVideoHandleObject extends ClientGenericHandleObject {
    private static final String[] HEADER_ARRAY = {
            "Duration", "Resolution", "Codec", "Bit Rate"
    };
    private static final int[] HEADER_SIZE = { 80, 100, 120, 90 };

    private static final String KEY_DURATION = "/VideoLengthSec";
    private static final String KEY_WIDTH = "/VideoWidth";
    private static final String KEY_HEIGHT = "/VideoHeight";
    private static final String KEY_CODEC = "/VideoCodec";
    private static final String KEY_BIT_RATE = "/VideoBitRate";

    private final int numOfColumns;

    public ClientVideoHandleObject() {
        numOfColumns = super.getColumnCount();
    }

    @Override
    public int getColumnCount() {
        return super.getColumnCount() + HEADER_ARRAY.length;
    }

    @Override
    public String getHeader(int index) {
        if (index < numOfColumns) {
            return super.getHeader(index);
        }
        return HEADER_ARRAY[index - numOfColumns];
    }

    @Override
    public int getHeaderSize(int index) {
        if (index < numOfColumns) {
            return super.getHeaderSize(index);
        }
        return HEADER_SIZE[index - numOfColumns];
    }

    @Override
    public MCListItemInterface<SearchResult> getSearchItem(SearchResult result) {
        return new VideoSearchItem(result);
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

                return videoColumn(column - numOfColumns, record.metaData());
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

    private static Sortable videoColumn(int column, ReadOnlyMessagePak metadata) {
        return switch (column) {
            case 0 -> new SortableDuration(metadata.getLong(KEY_DURATION)
                    .filter(value -> value > 0).orElse(-1L));
            case 1 -> new SortableResolution(metadata.getLong(KEY_WIDTH)
                    .filter(value -> value > 0).orElse(-1L), metadata.getLong(KEY_HEIGHT)
                    .filter(value -> value > 0).orElse(-1L));
            case 2 -> new SortableString(normalize(metadata.getString(KEY_CODEC).orElse(null)));
            case 3 -> new SortableBitRate(metadata.getLong(KEY_BIT_RATE)
                    .filter(value -> value > 0).orElse(-1L));
            default -> throw new RuntimeException("Column " + column + " doesn't exist");
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private class VideoSearchItem extends GenericSearchItem {
        VideoSearchItem(SearchResult result) {
            super(result);
        }

        @Override
        public Sortable getValueOfColumn(int index) {
            if (index < numOfColumns) {
                return super.getValueOfColumn(index);
            }

            return videoColumn(index - numOfColumns, result.getMetaData());
        }
    }

    static class SortableDuration extends SortableLong {
        SortableDuration(long seconds) {
            super(seconds);
        }

        @Override
        public String toString() {
            if (number <= 0) {
                return "-";
            }
            long hours = number / 3600;
            long minutes = (number % 3600) / 60;
            long seconds = number % 60;
            if (hours > 0) {
                return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
            }
            return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
        }
    }

    static class SortableResolution extends SortableLong {
        private final long width;
        private final long height;

        SortableResolution(long width, long height) {
            super(pixelArea(width, height));
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return width > 0 && height > 0 ? width + "x" + height : "-";
        }

        private static long pixelArea(long width, long height) {
            if (width <= 0 || height <= 0) {
                return -1;
            }
            return width > Long.MAX_VALUE / height ? Long.MAX_VALUE : width * height;
        }
    }

    static class SortableBitRate extends SortableLong {
        SortableBitRate(long bitsPerSecond) {
            super(bitsPerSecond);
        }

        @Override
        public String toString() {
            if (number <= 0) {
                return "-";
            }
            if (number < 1_000_000) {
                return Math.round(number / 1_000d) + " kbps";
            }
            if (number % 1_000_000 == 0) {
                return (number / 1_000_000) + " Mbps";
            }
            return String.format(Locale.ROOT, "%.1f Mbps", number / 1_000_000d);
        }
    }
}
