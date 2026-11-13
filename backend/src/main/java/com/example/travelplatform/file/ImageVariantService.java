package com.example.travelplatform.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class ImageVariantService {
    private final Path directory;
    private final Semaphore decoders = new Semaphore(2);
    public ImageVariantService(@Value("${travel.storage.thumbnail-dir:./.local/image-variants}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    /** Input is already resolved by ManagedImagePaths. Failures preserve the original image path. */
    public Path variant(Path original, int width) {
        try {
            if (!decoders.tryAcquire(2, TimeUnit.SECONDS)) return original;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); return original;
        }
        try {
            if (Files.size(original) > 5L * 1024 * 1024) return original;
            Files.createDirectories(directory);
            Path cache = directory.toRealPath();
            String key = digest(original + "|" + Files.size(original) + "|" + Files.getLastModifiedTime(original) + "|" + width + "|v2");
            for (String extension : new String[]{"jpg", "png"}) {
                Path target = cache.resolve(key + "." + extension);
                if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return target;
            }
            return generate(original, width, cache, key);
        } catch (IOException | RuntimeException exception) {
            return original;
        } finally { decoders.release(); }
    }

    private Path generate(Path original, int width, Path cache, String key) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(original.toFile())) {
            if (input == null) return original;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return original;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int sourceWidth = reader.getWidth(0), sourceHeight = reader.getHeight(0);
                if (sourceWidth < 1 || sourceHeight < 1 || sourceWidth > 10000 || sourceHeight > 10000
                        || (long) sourceWidth * sourceHeight > 25000000) return original;
                int orientation = ImageOrientation.read(original);
                int orientedWidth = orientation >= 5 ? sourceHeight : sourceWidth;
                int orientedHeight = orientation >= 5 ? sourceWidth : sourceHeight;
                if (orientedWidth <= width) return original;
                var options = reader.getDefaultReadParam();
                int sample = Math.max(1, orientedWidth / width);
                options.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage decoded = ImageOrientation.apply(reader.read(0, options), orientation);
                int height = Math.max(1, (int) Math.round((double) orientedHeight * width / orientedWidth));
                boolean alpha = decoded.getColorModel().hasAlpha();
                var resized = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                var graphics = resized.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(decoded, 0, 0, width, height, null);
                } finally { graphics.dispose(); decoded.flush(); }
                try { return publish(resized, alpha, cache, key); }
                finally { resized.flush(); }
            } finally { reader.dispose(); }
        }
    }

    private Path publish(BufferedImage image, boolean alpha, Path cache, String key) throws IOException {
        String format = alpha ? "png" : "jpg";
        Path target = cache.resolve(key + "." + format);
        Path temporary = Files.createTempFile(cache, ".variant-", ".tmp");
        try {
            var writer = ImageIO.getImageWritersByFormatName(format).next();
            try (ImageOutputStream output = ImageIO.createImageOutputStream(temporary.toFile())) {
                writer.setOutput(output);
                var options = writer.getDefaultWriteParam();
                if (!alpha) { options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); options.setCompressionQuality(.78f); }
                writer.write(null, new IIOImage(image, null, null), options);
            } finally { writer.dispose(); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
