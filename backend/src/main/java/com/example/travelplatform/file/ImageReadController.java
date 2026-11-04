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
import java.nio.file.Path;
import java.util.Locale;

@RestController
@SecurityRequirements
public class ImageReadController {
    private final ManagedImagePaths paths;
    public ImageReadController(ManagedImagePaths paths) { this.paths = paths; }

    @GetMapping("/uploads/{*path}")
    public ResponseEntity<Resource> read(HttpServletRequest request) {
        Path real;
        try { real = paths.resolve(request.getRequestURI()); }
        catch (ApiException exception) { throw new ApiException(ErrorCode.NOT_FOUND); }
        String filename = real.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType type = filename.endsWith(".png") ? MediaType.IMAGE_PNG
                : filename.endsWith(".jpg") || filename.endsWith(".jpeg") ? MediaType.IMAGE_JPEG : MediaType.valueOf("image/webp");
        return ResponseEntity.ok().contentType(type).header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(new FileSystemResource(real));
    }
}
