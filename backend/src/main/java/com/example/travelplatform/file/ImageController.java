package com.example.travelplatform.file;

import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files/images")
public class ImageController {
    private final ImageStorageService storage;
    public ImageController(ImageStorageService storage) { this.storage = storage; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "上传一张JPEG、PNG或WebP图片，最多5MiB")
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                                  MultipartHttpServletRequest request) {
        var parts = request.getMultiFileMap();
        if (parts.size() != 1 || !parts.containsKey("file") || parts.get("file").size() != 1
                || !request.getParameterMap().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "请只提交一张图片");
        }
        return ApiResponse.success(Map.of("url", storage.upload(file)));
    }
}
