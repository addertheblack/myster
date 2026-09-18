package com.myster.net.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.myster.mml.MessagePak;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;

class TestThumbnailProtocolUtils {
    private static final MysterType TYPE = new MysterType(new byte[16]);
    private static final int NEXT_SECTION = 0x12345678;

    @ParameterizedTest
    @ValueSource(ints = {1, 256})
    void requestPreservesTypeUnicodeFilenameAndSize(int size) throws Exception {
        String filename = "été-写真.mp4";
        MessagePak request = ThumbnailProtocolUtils.createRequest(TYPE, filename, size);
        assertEquals(3, request.list("/").size());
        var decoded = ThumbnailProtocolUtils.readRequest(input(frame(request, new byte[0])));
        assertEquals(TYPE, decoded.type());
        assertEquals(filename, decoded.filename());
        assertEquals(size, decoded.size());
    }

    @Test
    void invalidRequestsFailLocallyAndOnWire() throws Exception {
        for (int size : new int[] {-1, 0, 257, 1024, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ThumbnailProtocolUtils.createRequest(TYPE, "file", size));
            MessagePak request = request();
            request.putInt("/size", size);
            assertBadRequest(request);
        }
        assertThrows(IllegalArgumentException.class,
                () -> ThumbnailProtocolUtils.createRequest(null, "file", 32));
        assertThrows(IllegalArgumentException.class,
                () -> ThumbnailProtocolUtils.createRequest(TYPE, null, 32));
        assertThrows(IllegalArgumentException.class,
                () -> ThumbnailProtocolUtils.createRequest(TYPE, "", 32));
        assertThrows(IllegalArgumentException.class,
                () -> ThumbnailProtocolUtils.createRequest(TYPE, "界".repeat(22000), 32));
        for (Consumer<MessagePak> change : java.util.List.<Consumer<MessagePak>>of(
                pak -> pak.remove("/type"), pak -> pak.putByteArray("/type", new byte[15]),
                pak -> pak.putString("/type", "bad"), pak -> pak.remove("/filename"),
                pak -> pak.putString("/filename", ""), pak -> pak.putInt("/filename", 1),
                pak -> pak.remove("/size"), pak -> pak.putLong("/size", Long.MAX_VALUE),
                pak -> pak.putString("/size", "32"), pak -> pak.putDouble("/size", 32))) {
            MessagePak request = request();
            change.accept(request);
            assertBadRequest(request);
        }
    }

    @Test
    void headersRejectOversizeNegativeMalformedAndTruncatedFrames() throws Exception {
        for (int limit : new int[] {ThumbnailProtocolUtils.MAX_REQUEST_BYTES,
                                   ThumbnailProtocolUtils.MAX_RESPONSE_BYTES}) {
            for (int length : new int[] {-1, limit + 1, Integer.MAX_VALUE}) {
                byte[] bytes = ByteBuffer.allocate(5).putInt(length).put((byte) 0x42).array();
                MysterDataInputStream in = input(bytes);
                assertThrows(IOException.class, () -> readHeader(in, limit));
                assertEquals(0x42, in.read(), "oversized frame must not consume its payload");
            }
            for (byte[] bytes : new byte[][] {new byte[0], new byte[3],
                    ByteBuffer.allocate(5).putInt(2).put((byte) 0x80).array(),
                    ByteBuffer.allocate(5).putInt(1).put((byte) 0xc1).array(),
                    new byte[4]}) {
                assertThrows(IOException.class, () -> readHeader(input(bytes), limit));
            }
        }
    }

    @Test
    void rawFixtureDefinesNetworkArgbAndRowOrderIndependently() throws Exception {
        int[] expected = {0x80402010, 0xff112233, 0x00010203, 0x7f102040, 0xffffffff, 0};
        byte[] raw = { (byte) 0x80, 0x40, 0x20, 0x10, (byte) 0xff, 0x11, 0x22, 0x33,
                0, 1, 2, 3, 0x7f, 0x10, 0x20, 0x40, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, (byte) 0xff, 0, 0, 0, 0 };
        BufferedImage decoded = ThumbnailProtocolUtils.readResponse(
                input(frame(header("image/x-myster-argb32", 3, 2, raw.length), raw)), 32);
        assertEquals(3, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
        assertArrayEquals(expected, pixels(decoded));

        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 3, 2, expected, 0, 3);
        MysterDataInputStream encoded = input(encode(source, 32));
        MessagePak header = encoded.readMessagePack();
        assertEquals("image/x-myster-argb32", header.getString("/mimeType").orElseThrow());
        assertEquals(24, header.getInt("/length").orElseThrow());
        assertArrayEquals(raw, encoded.readAllBytes());
    }

    @ParameterizedTest
    @CsvSource({"64,32", "32,64", "64,64", "12,7", "1,1", "1,63", "256,256"})
    void encodingKeepsActualDimensionsPixelsAndSourceOwnership(int width, int height) throws Exception {
        BufferedImage source = patternedImage(width, height);
        int[] before = pixels(source);
        byte[] bytes = encode(source, 256);
        BufferedImage decoded = ThumbnailProtocolUtils.readResponse(input(bytes), 256);
        assertNotSame(source, decoded);
        assertEquals(width, decoded.getWidth());
        assertEquals(height, decoded.getHeight());
        assertArrayEquals(before, pixels(decoded));
        assertArrayEquals(before, pixels(source));
    }

    @Test
    void pngCarriesEightBitRgbaAndCompressesFinalDimensions() throws Exception {
        BufferedImage source = patternedImage(64, 32);
        MysterDataInputStream in = input(encode(source, 64));
        MessagePak header = in.readMessagePack();
        assertEquals("image/png", header.getString("/mimeType").orElseThrow());
        byte[] body = in.readAllBytes();
        assertEquals(body.length, header.getInt("/length").orElseThrow());
        assertTrue(body.length < 64 * 32 * 4);
        assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10},
                Arrays.copyOf(body, 8));
        assertEquals(8, body[24]);
        assertEquals(6, body[25]);
    }

    @Test
    void premultipliedPixelsAreConvertedWithoutMutatingSource() throws Exception {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB_PRE);
        source.setRGB(0, 0, 0x80402010);
        int[] before = pixels(source);
        BufferedImage decoded = ThumbnailProtocolUtils.readResponse(input(encode(source, 32)), 32);
        assertArrayEquals(before, pixels(decoded));
        assertArrayEquals(before, pixels(source));
        assertTrue(source.isAlphaPremultiplied());
    }

    @Test
    void rawMaximumFitsAndIncompressiblePngFallsBackToRaw() throws Exception {
        BufferedImage source = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new Random(79).ints(256 * 256).toArray();
        source.setRGB(0, 0, 256, 256, pixels, 0, 256);
        byte[] response = encode(source, 256);
        MysterDataInputStream in = input(response);
        MessagePak header = in.readMessagePack();
        assertEquals("image/x-myster-argb32", header.getString("/mimeType").orElseThrow());
        assertEquals(262144, header.getInt("/length").orElseThrow());
        assertEquals(262144, in.readAllBytes().length);
        assertArrayEquals(pixels, pixels(ThumbnailProtocolUtils.readResponse(input(response), 256)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/x-myster-argb32"})
    void rejectsBadHeadersBeforeReadingBody(String mime) throws Exception {
        for (Consumer<MessagePak> change : java.util.List.<Consumer<MessagePak>>of(
                pak -> pak.remove("/mimeType"), pak -> pak.putString("/mimeType", "image/jpeg"),
                pak -> pak.putInt("/mimeType", 1), pak -> pak.remove("/length"),
                pak -> pak.putInt("/length", 0), pak -> pak.putInt("/length", -1),
                pak -> pak.putInt("/length", 262145), pak -> pak.putLong("/length", Long.MAX_VALUE),
                pak -> pak.putString("/length", "4"), pak -> pak.putDouble("/length", 4),
                pak -> pak.remove("/width"), pak -> pak.putInt("/width", 0),
                pak -> pak.putInt("/width", -1), pak -> pak.putInt("/width", 257),
                pak -> pak.putString("/width", "1"), pak -> pak.putDouble("/width", 1),
                pak -> pak.remove("/height"), pak -> pak.putInt("/height", 0),
                pak -> pak.putInt("/height", -1), pak -> pak.putInt("/height", 257),
                pak -> pak.putLong("/height", Long.MAX_VALUE))) {
            MessagePak header = header(mime, 1, 1, 4);
            change.accept(header);
            MysterDataInputStream in = input(frame(header, new byte[] {42}));
            assertThrows(IOException.class, () -> ThumbnailProtocolUtils.readResponse(in, 256));
            assertEquals(42, in.read(), "header failure must not consume image bytes");
        }
    }

    @Test
    void rawLengthMustExactlyMatchDimensionsAndBodyMustBeComplete() {
        for (int length : new int[] {3, 5}) {
            assertThrows(IOException.class, () -> ThumbnailProtocolUtils.readResponse(
                    input(frame(header("image/x-myster-argb32", 1, 1, length), new byte[length])), 32));
        }
        assertThrows(IOException.class, () -> ThumbnailProtocolUtils.readResponse(
                input(frame(header("image/x-myster-argb32", 1, 1, 4), new byte[3])), 32));
    }

    @Test
    void rejectsCorruptTruncatedAndMislabeledPng() throws Exception {
        byte[] png = png(patternedImage(12, 7));
        byte[] corrupt = png.clone();
        corrupt[corrupt.length / 2] ^= 1;
        for (byte[] body : new byte[][] {new byte[] {1, 2, 3}, corrupt,
                Arrays.copyOf(png, png.length - 1), Arrays.copyOf(png, png.length - 12),
                Arrays.copyOf(png, png.length / 2)}) {
            assertThrows(IOException.class, () -> ThumbnailProtocolUtils.readResponse(
                    input(frame(header("image/png", 12, 7, body.length), body)), 32));
        }
        assertThrows(IOException.class, () -> ThumbnailProtocolUtils.readResponse(
                input(frame(header("image/png", 12, 7, png.length), Arrays.copyOf(png, png.length - 1))), 32));
    }

    @Test
    void checksPngDimensionsBeforeRasterAllocation() throws Exception {
        byte[] png = png(patternedImage(12, 7));
        assertThrows(IOException.class, () -> ThumbnailProtocolUtils.readResponse(
                input(frame(header("image/png", 11, 7, png.length), png)), 32));
        ByteBuffer.wrap(png).putInt(16, 1_000_000).putInt(20, 1_000_000);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
        assertThrows(IOException.class, () -> ThumbnailProtocolUtils.readResponse(
                input(frame(header("image/png", 12, 7, png.length), png)), 32));
    }

    @Test
    void eitherEncodingAcceptsSmallRectanglesAndPreservesFollowingBytesOnPartialReads() throws Exception {
        BufferedImage source = patternedImage(12, 7);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        var out = new MysterDataOutputStream(raw);
        for (int pixel : pixels(source)) {
            out.writeInt(pixel);
        }
        byte[] png = png(source);
        for (byte[] response : new byte[][] {
                frame(header("image/png", 12, 7, png.length), png),
                frame(header("image/x-myster-argb32", 12, 7, raw.size()), raw.toByteArray())}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(response);
            new MysterDataOutputStream(bytes).writeInt(NEXT_SECTION);
            var in = new MysterDataInputStream(new ByteArrayInputStream(bytes.toByteArray()) {
                @Override
                public synchronized int read(byte[] buffer, int offset, int length) {
                    return super.read(buffer, offset, Math.min(3, length));
                }
            });
            assertArrayEquals(pixels(source), pixels(ThumbnailProtocolUtils.readResponse(in, 64)));
            assertEquals(NEXT_SECTION, in.readInt());
        }
    }

    @Test
    void emptyResponseHasNoBodyAndOversizedProviderImageHasNoHeader() throws Exception {
        byte[] response = encode(null, 32);
        assertTrue(input(response).readMessagePack().list("/").isEmpty());
        assertEquals(5, response.length);
        assertNull(ThumbnailProtocolUtils.readResponse(input(response), 32));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> ThumbnailProtocolUtils.writeResponse(
                new MysterDataOutputStream(out), patternedImage(33, 12), 32));
        assertEquals(0, out.size());
    }

    private static void readHeader(MysterDataInputStream in, int limit) throws IOException {
        if (limit == ThumbnailProtocolUtils.MAX_REQUEST_BYTES) {
            ThumbnailProtocolUtils.readRequest(in);
        } else {
            ThumbnailProtocolUtils.readResponse(in, 32);
        }
    }

    private static void assertBadRequest(MessagePak request) throws IOException {
        byte[] bytes = frame(request, new byte[0]);
        assertThrows(IOException.class, () -> ThumbnailProtocolUtils.readRequest(input(bytes)));
    }

    private static MessagePak request() throws IOException {
        return ThumbnailProtocolUtils.createRequest(TYPE, "file", 32);
    }

    private static MessagePak header(String mime, int width, int height, int length) {
        MessagePak header = MessagePak.newEmpty();
        header.putString("/mimeType", mime);
        header.putInt("/width", width);
        header.putInt("/height", height);
        header.putInt("/length", length);
        return header;
    }

    private static byte[] frame(MessagePak header, byte[] body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new MysterDataOutputStream(bytes).writeMessagePack(header);
        bytes.write(body);
        return bytes.toByteArray();
    }

    private static byte[] encode(BufferedImage image, int size) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ThumbnailProtocolUtils.writeResponse(new MysterDataOutputStream(bytes), image, size);
        return bytes.toByteArray();
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var out = new MemoryCacheImageOutputStream(bytes)) {
            assertTrue(ImageIO.write(image, "png", out));
        }
        return bytes.toByteArray();
    }

    private static MysterDataInputStream input(byte[] bytes) {
        return new MysterDataInputStream(new ByteArrayInputStream(bytes));
    }

    private static BufferedImage patternedImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x % 2 == 0) ? 0x80402010 : 0xffabcdef);
            }
        }
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
