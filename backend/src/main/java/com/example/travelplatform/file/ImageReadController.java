package com.example.travelplatform.file;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.nio.file.Path;
import java.util.Locale;

@RestController
@SecurityRequirements
public class ImageReadController {
    private final ManagedImagePaths paths;
    private final ImageVariantService variants;
    public ImageReadController(ManagedImagePaths paths, ImageVariantService variants) { this.paths = paths; this.variants = variants; }

    @GetMapping("/uploads/{*path}")
    public ResponseEntity<Resource> read(HttpServletRequest request, @RequestParam(required = false) String width) {
        Path real;
        try { real = paths.resolve(request.getRequestURI()); }
        catch (ApiException exception) { throw new ApiException(ErrorCode.NOT_FOUND); }
        Path original = real;
        if (width != null) {
            if (!java.util.Set.of("320", "640", "960").contains(width)) throw new ApiException(ErrorCode.VALIDATION_FAILED, "图片宽度仅支持320、640或960");
            real = variants.variant(real, Integer.parseInt(width));
        }
        String filename = real.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType type = filename.endsWith(".png") ? MediaType.IMAGE_PNG
                : filename.endsWith(".jpg") || filename.endsWith(".jpeg") ? MediaType.IMAGE_JPEG : MediaType.valueOf("image/webp");
        return ResponseEntity.ok().contentType(type).header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", width != null && real.equals(original)
                        ? "public, max-age=60" : "public, max-age=31536000, immutable")
                .body(new FileSystemResource(real));
    }
}
