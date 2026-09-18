package com.myster.net.stream;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.myster.mml.MessagePak;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.type.MysterType;

/**
 * Wire codec for TCP thumbnail section 79, after the standard section acknowledgement.
 * Requests are length-prefixed MessagePaks containing {@code /type} (16-byte binary CID),
 * {@code /filename} (opaque index key) and {@code /size} (maximum width/height, 1–256).
 *
 * <p>A response is an empty MessagePak for an unavailable thumbnail, or a MessagePak containing
 * {@code /mimeType}, {@code /length}, {@code /width} and {@code /height}, followed immediately by
 * exactly {@code /length} image bytes outside MessagePack. Images retain their actual dimensions
 * without padding. Both dimensions must be positive and at most the requested size. Header limits
 * are 64 KiB for requests and 4 KiB for responses; image bodies are at most 256 KiB.
 *
 * <p>Supported bodies are {@code image/png} and {@code image/x-myster-argb32}. Raw pixels are
 * straight-alpha sRGB {@code 0xAARRGGBB} integers in network byte order (A, R, G, B), left to
 * right and top to bottom, without row padding. Their length is exactly width × height × 4.
 * PNG dimensions must match the response header. Unknown MIME types and malformed data fail
 * with {@link IOException}; callers must discard the connection after such a failure.
 */
public final class ThumbnailProtocolUtils {
    public static final int SECTION_NUMBER = 79;
    public static final int MAX_SIZE = 256;
    public static final int MAX_IMAGE_BYTES = MAX_SIZE * MAX_SIZE * Integer.BYTES;
    public static final int MAX_REQUEST_BYTES = 64 * 1024;
    public static final int MAX_RESPONSE_BYTES = 4 * 1024;

    private static final String TYPE = "/type";
    private static final String FILENAME = "/filename";
    private static final String SIZE = "/size";
    private static final String MIME_TYPE = "/mimeType";
    private static final String LENGTH = "/length";
    private static final String WIDTH = "/width";
    private static final String HEIGHT = "/height";
    private static final String PNG_MIME = "image/png";
    private static final String ARGB_MIME = "image/x-myster-argb32";
    private static final String PNG_FORMAT = "png";
    private static final byte[] PNG_SIGNATURE = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };

    private static final int PNG_IHDR = 0x49484452;
    private static final int PNG_IHDR_DATA_BYTES = 13;
    private static final int PNG_IEND = 0x49454e44;
    private static final int PNG_CHUNK_OVERHEAD = 12;

    private ThumbnailProtocolUtils() {}

    /**
     * Builds and checks the complete request before the client writes a section number.
     *
     * @throws IllegalArgumentException for null type/filename, empty filename, an invalid size
     *         or a request exceeding the encoded frame limit
     */
    public static MessagePak createRequest(MysterType type, String filename, int size)
            throws IOException {
        validateSize(size);
        if (type == null || filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Thumbnail requests require a type and filename");
        }
        if (filename.length() > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Thumbnail request exceeds frame limit");
        }
        MessagePak request = MessagePak.newEmpty();
        request.putByteArray(TYPE, type.toBytes());
        request.putString(FILENAME, filename);
        request.putInt(SIZE, size);
        if (request.toBytes().length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Thumbnail request exceeds frame limit");
        }
        return request;
    }

    /** Reads one bounded request; malformed peer arguments are I/O failures. */
    public static Request readRequest(MysterDataInputStream in) throws IOException {
        MessagePak request = in.readMessagePack(MAX_REQUEST_BYTES);
        byte[] type = request.getByteArray(TYPE)
                .orElseThrow(() -> new IOException("Missing thumbnail type"));
        String filename = request.getString(FILENAME)
                .orElseThrow(() -> new IOException("Missing thumbnail filename"));
        int size = requiredInt(request, SIZE);
        if (type.length != 16 || filename.isEmpty() || size < 1 || size > MAX_SIZE) {
            throw new IOException("Invalid thumbnail request bounds");
        }
        return new Request(new MysterType(type), filename, size);
    }

    /**
     * Writes and flushes one response without changing or retaining the source image.
     * Null means unavailable and writes only an empty MessagePak. PNG is used when smaller
     * than raw pixels; raw is also used if no PNG writer is installed. The image and complete
     * encoded body are validated before a successful response header is written.
     *
     * @throws IOException if the image exceeds the requested bound, encoding fails or output fails
     * @throws IllegalArgumentException if size is outside 1–256
     */
    public static void writeResponse(MysterDataOutputStream out, BufferedImage image, int size)
            throws IOException {
        validateSize(size);
        if (image == null) {
            out.writeMessagePack(MessagePak.newEmpty());
            out.flush();
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        validateDimensions(width, height, size);
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        normalized.setRGB(0, 0, width, height, pixels, 0, width);

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        boolean encoded;
        try (var output = new MemoryCacheImageOutputStream(png)) {
            encoded = ImageIO.write(normalized, PNG_FORMAT, output);
        }
        String mime;
        byte[] body;
        int rawLength = pixels.length * Integer.BYTES;
        if (encoded && png.size() < rawLength) {
            mime = PNG_MIME;
            body = png.toByteArray();
        } else {
            mime = ARGB_MIME;
            body = new byte[rawLength];
            ByteBuffer.wrap(body).asIntBuffer().put(pixels);
        }
        if (body.length < 1 || body.length > MAX_IMAGE_BYTES) {
            throw new IOException("Thumbnail image body exceeds bounds");
        }
        MessagePak header = MessagePak.newEmpty();
        header.putString(MIME_TYPE, mime);
        header.putInt(LENGTH, body.length);
        header.putInt(WIDTH, width);
        header.putInt(HEIGHT, height);
        out.writeMessagePack(header);
        out.write(body);
        out.flush();
    }

    /**
     * Reads one bounded response and returns a caller-owned ARGB image, or null for an empty
     * response. Image decoders operate only on the framed body and cannot read the next section.
     *
     * @throws IOException for malformed headers, unsupported MIME types, invalid dimensions,
     *         corrupt images or truncated frames/bodies
     * @throws IllegalArgumentException if size is outside 1–256
     */
    public static BufferedImage readResponse(MysterDataInputStream in, int size) throws IOException {
        validateSize(size);
        MessagePak header = in.readMessagePack(MAX_RESPONSE_BYTES);
        if (header.list("/").isEmpty()) {
            return null;
        }
        String mime = header.getString(MIME_TYPE)
                .orElseThrow(() -> new IOException("Missing thumbnail MIME type"));
        if (!PNG_MIME.equals(mime) && !ARGB_MIME.equals(mime)) {
            throw new IOException("Unsupported thumbnail MIME type: " + mime);
        }
        int length = requiredInt(header, LENGTH);
        int width = requiredInt(header, WIDTH);
        int height = requiredInt(header, HEIGHT);
        validateDimensions(width, height, size);
        if (length < 1 || length > MAX_IMAGE_BYTES
                || (ARGB_MIME.equals(mime) && length != (long) width * height * Integer.BYTES)) {
            throw new IOException("Invalid thumbnail image length: " + length);
        }
        byte[] body = new byte[length];
        in.readFully(body);
        int[] pixels;
        if (PNG_MIME.equals(mime)) {
            BufferedImage decoded = readPng(body, width, height);
            pixels = decoded.getRGB(0, 0, width, height, null, 0, width);
        } else {
            pixels = new int[width * height];
            ByteBuffer.wrap(body).asIntBuffer().get(pixels);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static BufferedImage readPng(byte[] body, int width, int height) throws IOException {
        validatePngChunks(body);
        var readers = ImageIO.getImageReadersByFormatName(PNG_FORMAT);
        if (!readers.hasNext()) {
            throw new IOException("PNG decoder unavailable");
        }
        ImageReader reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(body))) {
            reader.setInput(input, true, true);
            if (reader.getWidth(0) != width || reader.getHeight(0) != height) {
                throw new IOException("PNG dimensions do not match thumbnail header");
            }
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    private static void validatePngChunks(byte[] body) throws IOException {
        if (body.length < PNG_SIGNATURE.length
                || !Arrays.equals(PNG_SIGNATURE, 0, PNG_SIGNATURE.length,
                                  body, 0, PNG_SIGNATURE.length)) {
            throw new IOException("Invalid PNG signature");
        }
        // ImageIO may stop after the pixels without requiring IEND or validating chunk CRCs.
        // Check the bounded PNG framing so corrupt/truncated files are not accepted as images.
        ByteBuffer buffer = ByteBuffer.wrap(body);
        int offset = PNG_SIGNATURE.length;
        while (body.length - offset >= PNG_CHUNK_OVERHEAD) {
            int length = buffer.getInt(offset);
            int type = buffer.getInt(offset + Integer.BYTES);
            if (length < 0 || length > body.length - offset - PNG_CHUNK_OVERHEAD
                    || (offset == PNG_SIGNATURE.length
                        && (type != PNG_IHDR || length != PNG_IHDR_DATA_BYTES))) {
                throw new IOException("Invalid PNG chunk length or header");
            }
            CRC32 crc = new CRC32();
            crc.update(body, offset + Integer.BYTES, Integer.BYTES + length);
            if ((int) crc.getValue() != buffer.getInt(offset + 2 * Integer.BYTES + length)) {
                throw new IOException("Invalid PNG chunk checksum");
            }
            offset += PNG_CHUNK_OVERHEAD + length;
            if (type == PNG_IEND) {
                if (length != 0 || offset != body.length) {
                    throw new IOException("Invalid PNG end chunk");
                }
                return;
            }
        }
        throw new IOException("Missing PNG end chunk");
    }

    private static int requiredInt(MessagePak message, String field) throws IOException {
        return message.getInt(field).orElseThrow(() -> new IOException("Missing or invalid " + field));
    }

    private static void validateDimensions(int width, int height, int size) throws IOException {
        if (width < 1 || height < 1 || width > size || height > size) {
            throw new IOException("Thumbnail dimensions exceed requested bounds: " + width + "x" + height);
        }
    }

    private static void validateSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Thumbnail size must be between 1 and " + MAX_SIZE);
        }
    }

    /** A validated request; filename is a shared-index reference, never a filesystem path. */
    public record Request(MysterType type, String filename, int size) {}
}
