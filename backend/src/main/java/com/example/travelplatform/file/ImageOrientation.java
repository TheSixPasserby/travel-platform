package com.example.travelplatform.file;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bounded JPEG APP1 / TIFF IFD0 orientation parsing. Generated pixels use standard orientation. */
final class ImageOrientation {
    private ImageOrientation() { }

    static int read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length < 4 || unsigned(bytes[0]) != 255 || unsigned(bytes[1]) != 216) return 1;
        int position = 2;
        while (position + 4 <= bytes.length && unsigned(bytes[position]) == 255) {
            while (position < bytes.length && unsigned(bytes[position]) == 255) position++;
            if (position + 3 > bytes.length) return 1;
            int marker = unsigned(bytes[position++]);
            if (marker == 218 || marker == 217) return 1;
            int length = unsigned(bytes[position]) * 256 + unsigned(bytes[position + 1]);
            if (length < 2 || position + length > bytes.length) return 1;
            int payload = position + 2;
            if (marker == 225 && length >= 16 && bytes[payload] == 'E' && bytes[payload + 1] == 'x'
                    && bytes[payload + 2] == 'i' && bytes[payload + 3] == 'f' && bytes[payload + 4] == 0 && bytes[payload + 5] == 0) {
                return tiff(ByteBuffer.wrap(bytes, payload + 6, length - 8).slice());
            }
            position += length;
        }
        return 1;
    }

    private static int tiff(ByteBuffer data) {
        if (data.capacity() < 8) return 1;
        if (data.get(0) == 'I' && data.get(1) == 'I') data.order(ByteOrder.LITTLE_ENDIAN);
        else if (data.get(0) == 'M' && data.get(1) == 'M') data.order(ByteOrder.BIG_ENDIAN);
        else return 1;
        if (Short.toUnsignedInt(data.getShort(2)) != 42) return 1;
        long offset = Integer.toUnsignedLong(data.getInt(4));
        if (offset < 8 || offset + 2 > data.capacity()) return 1;
        int directory = (int) offset;
        int count = Short.toUnsignedInt(data.getShort(directory));
        if ((long) directory + 2 + (long) count * 12 > data.capacity()) return 1;
        for (int index = 0; index < count; index++) {
            int entry = directory + 2 + index * 12;
            if (Short.toUnsignedInt(data.getShort(entry)) == 274 && Short.toUnsignedInt(data.getShort(entry + 2)) == 3 && data.getInt(entry + 4) == 1) {
                int orientation = Short.toUnsignedInt(data.getShort(entry + 8));
                return orientation >= 1 && orientation <= 8 ? orientation : 1;
            }
        }
        return 1;
    }

    static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation == 1) return image;
        int width = image.getWidth(), height = image.getHeight();
        var transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> throw new IllegalArgumentException("Unsupported orientation");
        };
        var result = new BufferedImage(orientation >= 5 ? height : width, orientation >= 5 ? width : height,
                image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = result.createGraphics();
        try { graphics.drawImage(image, transform, null); }
        finally { graphics.dispose(); image.flush(); }
        return result;
    }
    private static int unsigned(byte value) { return Byte.toUnsignedInt(value); }
}
