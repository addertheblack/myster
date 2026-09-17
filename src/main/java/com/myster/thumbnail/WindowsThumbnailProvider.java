package com.myster.thumbnail;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/** Windows Shell COM calls and GDI ownership stay on one platform worker thread. */
final class WindowsThumbnailProvider implements ThumbnailProvider {
    private static final Logger log = Logger.getLogger(WindowsThumbnailProvider.class.getName());
    private static final int THUMBNAIL_ONLY = 0x08;
    private static final int CACHE_ONLY = 0x10;
    private static final MemoryLayout SIZE = MemoryLayout.structLayout(JAVA_INT, JAVA_INT);

    @Override
    public Optional<BufferedImage> load(Path file, int size) throws IOException {
        if (!WindowsThumbnailProvider.class.getModule().isNativeAccessEnabled()) {
            log.info("Windows thumbnails require --enable-native-access=ALL-UNNAMED");
            return Optional.empty();
        }
        NativeCalls calls = NativeHolder.calls;
        // COINIT_APARTMENTTHREADED. Initialization and cleanup occur on this native thread.
        int initialized = calls.integer(calls.initialize, MemorySegment.NULL, 2);
        if (initialized < 0) {
            throw failure("CoInitializeEx", initialized);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment iid = arena.allocate(16, 4);
            iid.set(JAVA_INT, 0, 0xbcc18b79);
            iid.set(JAVA_SHORT, 4, (short) 0xba16);
            iid.set(JAVA_SHORT, 6, (short) 0x442f);
            MemorySegment.copy(new byte[]{(byte) 0x80, (byte) 0xc4, (byte) 0x8a, 0x59,
                                       (byte) 0xc3, 0x0c, 0x46, 0x3b},
                               0, iid, java.lang.foreign.ValueLayout.JAVA_BYTE, 8, 8);
            MemorySegment path = arena.allocateFrom(file.toString(), StandardCharsets.UTF_16LE);
            MemorySegment factoryOut = arena.allocate(ADDRESS);
            int created = calls.integer(calls.createItem, path, MemorySegment.NULL, iid, factoryOut);
            if (created < 0) {
                throw failure("SHCreateItemFromParsingName", created);
            }
            MemorySegment factory = factoryOut.get(ADDRESS, 0);
            MethodHandle release = calls.method(factory, 2,
                    FunctionDescriptor.of(JAVA_INT, ADDRESS));
            try {
                MethodHandle getImage = calls.method(factory, 3,
                                                     FunctionDescriptor.of(JAVA_INT, ADDRESS, SIZE, JAVA_INT, ADDRESS));
                MemorySegment dimensions = arena.allocate(SIZE);
                dimensions.set(JAVA_INT, 0, size);
                dimensions.set(JAVA_INT, 4, size);
                MemorySegment bitmapOut = arena.allocate(ADDRESS);
                int result = calls.integer(getImage, factory, dimensions, THUMBNAIL_ONLY | CACHE_ONLY, bitmapOut);
                String source = "Windows Shell cache";
                if (result < 0) {
                    bitmapOut.set(ADDRESS, 0, MemorySegment.NULL);
                    result = calls.integer(getImage, factory, dimensions, THUMBNAIL_ONLY, bitmapOut);
                    source = "Windows Shell extraction";
                }
                if (result < 0) {
                    log.info("Windows Shell has no thumbnail: HRESULT=0x"
                                     + Integer.toHexString(result) + " file=" + file);
                    return Optional.empty();
                }
                MemorySegment bitmap = bitmapOut.get(ADDRESS, 0);
                try {
                    BufferedImage image = calls.copyBitmap(bitmap, arena);
                    log.info("Thumbnail source=" + source + " file=" + file);
                    return Optional.of(image);
                } finally {
                    calls.integer(calls.deleteObject, bitmap);
                }
            } finally {
                calls.integer(release, factory);
            }
        } finally {
            calls.invoke(calls.uninitialize);
        }
    }

    private static IOException failure(String operation, int result) {
        return new IOException(operation + " failed: HRESULT=0x" + Integer.toHexString(result));
    }

    private static final class NativeHolder {
        private static final NativeCalls calls = new NativeCalls();
    }

    private static final class NativeCalls {
        private final Linker linker = Linker.nativeLinker();
        private final SymbolLookup ole = SymbolLookup.libraryLookup("ole32", Arena.global());
        private final SymbolLookup shell = SymbolLookup.libraryLookup("shell32", Arena.global());
        private final SymbolLookup gdi = SymbolLookup.libraryLookup("gdi32", Arena.global());
        private final MethodHandle initialize = exported(ole, "CoInitializeEx",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        private final MethodHandle uninitialize = exported(ole, "CoUninitialize",
                FunctionDescriptor.ofVoid());
        private final MethodHandle createItem = exported(shell, "SHCreateItemFromParsingName",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        private final MethodHandle getObject = exported(gdi, "GetObjectW",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        private final MethodHandle getDibits = exported(gdi, "GetDIBits",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT,
                                      ADDRESS, ADDRESS, JAVA_INT));
        private final MethodHandle createDc = exported(gdi, "CreateCompatibleDC",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        private final MethodHandle deleteDc = exported(gdi, "DeleteDC",
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
        private final MethodHandle deleteObject = exported(gdi, "DeleteObject",
                FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private MethodHandle exported(SymbolLookup library, String name, FunctionDescriptor type) {
            return linker.downcallHandle(library.find(name).orElseThrow(), type);
        }

        private MethodHandle method(MemorySegment object, int index, FunctionDescriptor type) {
            MemorySegment table = object.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0)
                    .reinterpret(4 * ADDRESS.byteSize());
            return linker.downcallHandle(table.getAtIndex(ADDRESS, index), type);
        }

        private int integer(MethodHandle method, Object... arguments) throws IOException {
            return (int) invoke(method, arguments);
        }

        private Object invoke(MethodHandle method, Object... arguments) throws IOException {
            try {
                return method.invokeWithArguments(arguments);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                // MethodHandle declares Throwable; translate checked failures at this FFM boundary.
                throw new IOException("Windows thumbnail native call failed", e);
            }
        }

        private BufferedImage copyBitmap(MemorySegment bitmap, Arena arena) throws IOException {
            int structureSize = ADDRESS.byteSize() == 8 ? 32 : 24;
            MemorySegment description = arena.allocate(structureSize, ADDRESS.byteAlignment());
            if (integer(getObject, bitmap, structureSize, description) == 0) {
                throw new IOException("GetObjectW failed");
            }
            int width = description.get(JAVA_INT, 4);
            int height = description.get(JAVA_INT, 8);
            if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
                throw new IOException("Invalid Shell bitmap dimensions: " + width + "x" + height);
            }
            MemorySegment info = arena.allocate(40, 4);
            info.set(JAVA_INT, 0, 40);
            info.set(JAVA_INT, 4, width);
            info.set(JAVA_INT, 8, -height); // Top-down DIB: same row order as BufferedImage.
            info.set(JAVA_SHORT, 12, (short) 1);
            info.set(JAVA_SHORT, 14, (short) 32);
            MemorySegment pixels = arena.allocate((long) width * height * 4, 4);
            MemorySegment dc = (MemorySegment) invoke(createDc, MemorySegment.NULL);
            if (dc.equals(MemorySegment.NULL)) {
                throw new IOException("CreateCompatibleDC failed");
            }
            try {
                if (integer(getDibits, dc, bitmap, 0, height, pixels, info, 0) != height) {
                    throw new IOException("GetDIBits failed");
                }
            } finally {
                integer(deleteDc, dc);
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            MemorySegment.copy(pixels, JAVA_INT, 0, data, 0, data.length);
            boolean hasAlpha = false;
            for (int pixel : data) {
                hasAlpha |= (pixel >>> 24) != 0;
            }
            if (!hasAlpha) {
                // GDI also returns opaque bitmaps with an unused, zero-filled alpha byte.
                for (int i = 0; i < data.length; i++) {
                    data[i] |= 0xff000000;
                }
            }
            return image;
        }
    }
}
