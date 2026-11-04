package com.example.travelplatform.file;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class ManagedImagePaths {
    private static final String PREFIX = "/uploads/";
    private static final Pattern MANAGED_URL = Pattern.compile(
            "^/uploads/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9][A-Za-z0-9._-]*\\.(?i:jpg|jpeg|png|webp)$");
    private final Path root;

    public ManagedImagePaths(@Value("${travel.storage.upload-dir}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String validate(String url) {
        if (url == null) return null;
        resolve(url);
        return url;
    }

    public Path resolve(String url) {
        if (url == null || url.length() > 500 || !MANAGED_URL.matcher(url).matches()) throw invalid();
        try {
            Path realRoot = root.toRealPath();
            Path candidate = root.resolve(url.substring(PREFIX.length())).normalize();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) throw invalid();
            Path real = candidate.toRealPath();
            if (!real.startsWith(realRoot) || !real.getFileName().toString().matches("(?i).+\\.(jpg|jpeg|png|webp)")) throw invalid();
            return real;
        } catch (IOException | InvalidPathException | SecurityException exception) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "图片路径无效，请重新选择已上传的图片");
    }
}
