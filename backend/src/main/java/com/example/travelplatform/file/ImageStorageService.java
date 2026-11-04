package com.example.travelplatform.file;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 上传图片的校验与本地落盘。不信任客户端给出的文件名、扩展名与 MIME：
 * 原始文件名只用于提取扩展名（不拼路径、不写日志），内容类型以 ImageIO 的真实解码结果为准。
 * 只有全部校验通过后才写入磁盘，写盘采用同目录临时文件加原子移动发布。
 */
@Service
public class ImageStorageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_SIDE = 10_000;
    private static final long MAX_PIXELS = 25_000_000L;
    private static final int PUBLISH_ATTEMPTS = 8;
    private static final String URL_PREFIX = "/uploads/";
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final String directory;
    private final UserService users;

    public ImageStorageService(@Value("${travel.storage.upload-dir}") String directory, UserService users) {
        this.directory = directory;
        this.users = users;
    }

    /**
     * 校验并保存一张图片，返回可直接对外使用的站内 URL。登录校验必须先于任何文件检查。
     */
    public String upload(MultipartFile file) {
        users.requireCurrentUser();
        if (file == null || file.isEmpty()) {
            throw validation("请选择要上传的图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw tooLarge();
        }
        Format format = Format.of(file.getOriginalFilename());
        if (format == null || !format.mime().equals(file.getContentType())) {
            throw unsupported();
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw storageFailure();
        }
        // Actual byte length is authoritative; the declared part size is only an early filter.
        if (bytes.length == 0) {
            throw validation("请选择要上传的图片");
        }
        if (bytes.length > MAX_BYTES) {
            throw tooLarge();
        }
        verifyImage(bytes, format);
        return store(bytes, format.extension());
    }

    /**
     * 只做内存解码校验：格式名、头部尺寸与真实解码必须全部通过，否则不产生任何磁盘副作用。
     */
    private static void verifyImage(byte[] bytes, Format format) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                // Allow backward seeks so the dimension probe does not break the subsequent decode.
                reader.setInput(input, false, false);
                String actualFormat = reader.getFormatName();
                if (actualFormat == null
                        || !actualFormat.toLowerCase(Locale.ROOT).equals(format.imageIoFormat())) {
                    throw unsupported();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw unsupported();
                }
                // Reject decompression bombs from the header before any pixel buffer is allocated.
                if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                    throw invalidDimensions();
                }
                if (reader.read(0) == null) {
                    throw unsupported();
                }
            } catch (ApiException exception) {
                throw exception;
            } catch (IOException | RuntimeException exception) {
                // Truncated or corrupt payloads surface as reader failures, never as a server error.
                throw unsupported();
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw unsupported();
        }
    }

    private String store(byte[] bytes, String extension) {
        try {
            return writeAtomically(bytes, extension);
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // The original exception may carry an internal path, so only the generic code is exposed.
            throw storageFailure();
        }
    }

    private String writeAtomically(byte[] bytes, String extension) throws IOException {
        Path configuredRoot = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(configuredRoot);
        // A configured symlink is acceptable: the real root is what all containment checks use.
        Path realRoot = configuredRoot.toRealPath();
        LocalDate today = LocalDate.now(BEIJING);
        String year = Integer.toString(today.getYear());
        String month = String.format(Locale.ROOT, "%02d", today.getMonthValue());
        Path monthDirectory = ensureDirectory(realRoot, year, month);
        Path temp = Files.createTempFile(monthDirectory, ".tmp-", ".tmp");
        try {
            Files.write(temp, bytes);
            Path published = publish(monthDirectory, temp, extension);
            return URL_PREFIX + year + "/" + month + "/" + published.getFileName();
        } finally {
            try {
                // Best effort: remove only this request's temp file, never an already published image.
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Cleanup failure must not change the response of an otherwise successful upload.
            }
        }
    }

    /**
     * 逐级创建 yyyy/MM 目录。已存在的符号链接一律拒绝，创建后再用真实路径确认仍在根目录内，
     * 防止借指向外部的链接在根目录之外创建目录。
     */
    private static Path ensureDirectory(Path realRoot, String... segments) throws IOException {
        Path current = realRoot;
        for (String segment : segments) {
            Path child = current.resolve(segment);
            if (Files.isSymbolicLink(child)) {
                throw storageFailure();
            }
            Files.createDirectories(child);
            Path real = child.toRealPath();
            if (!real.startsWith(realRoot)) {
                throw storageFailure();
            }
            current = real;
        }
        return current;
    }

    /**
     * 同目录临时文件原子移动发布。目标名由 UUID 生成，若已存在则重新取名，绝不覆盖已有文件。
     */
    private static Path publish(Path monthDirectory, Path temp, String extension) throws IOException {
        for (int attempt = 0; attempt < PUBLISH_ATTEMPTS; attempt++) {
            String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            Path target = monthDirectory.resolve(name);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                return target;
            } catch (FileAlreadyExistsException exception) {
                // A concurrent upload took this name; retry with a fresh random name.
            }
        }
        throw storageFailure();
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static ApiException tooLarge() {
        return new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "图片大小不能超过 5MB");
    }

    private static ApiException invalidDimensions() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "图片尺寸超出限制，请压缩后重试");
    }

    private static ApiException unsupported() {
        return new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "仅支持 JPG、PNG、WebP 格式的图片");
    }

    private static ApiException storageFailure() {
        return new ApiException(ErrorCode.INTERNAL_ERROR, "图片存储暂不可用，请稍后重试");
    }

    /**
     * 扩展名、对外 MIME 与 ImageIO 格式名三者一一对应；jpeg 统一规范为 jpg。
     */
    private record Format(String mime, String extension, String imageIoFormat) {

        private static final Map<String, Format> BY_EXTENSION = Map.of(
                "jpg", new Format("image/jpeg", "jpg", "jpeg"),
                "jpeg", new Format("image/jpeg", "jpg", "jpeg"),
                "png", new Format("image/png", "png", "png"),
                "webp", new Format("image/webp", "webp", "webp"));

        private static Format of(String originalFilename) {
            if (originalFilename == null) {
                return null;
            }
            int dot = originalFilename.lastIndexOf('.');
            if (dot < 0 || dot == originalFilename.length() - 1) {
                return null;
            }
            return BY_EXTENSION.get(originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT));
        }
    }
}
