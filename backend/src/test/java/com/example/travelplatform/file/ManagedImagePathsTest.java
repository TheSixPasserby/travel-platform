package com.example.travelplatform.file;

import com.example.travelplatform.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedImagePathsTest {
    @TempDir Path temporary;

    @Test
    void existingManagedImageAndNullAreAccepted() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("uploads"));
        Path nested = Files.createDirectory(root.resolve("2026"));
        Files.write(nested.resolve("image.png"), new byte[]{1});
        ManagedImagePaths paths = new ManagedImagePaths(root.toString());
        assertThat(paths.validate("/uploads/2026/image.png")).isEqualTo("/uploads/2026/image.png");
        assertThat(paths.validate(null)).isNull();
    }

    @Test
    void untrustedAndMissingPathsAreRejectedWithoutLeakingDiskPath() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("uploads"));
        Files.write(root.resolve("image.txt"), new byte[]{1});
        ManagedImagePaths paths = new ManagedImagePaths(root.toString());
        for (String value : List.of("https://example.com/a.png", "uploads/a.png", "/uploads/../a.png",
                "/uploads/%2e%2e/a.png", "/uploads/a.png?x=1", "/uploads/a.png#x", "/uploads/a\\b.png",
                "/uploads/image.txt", "/uploads/missing.png", "/uploads//a.png")) {
            assertThatThrownBy(() -> paths.validate(value)).isInstanceOf(ApiException.class)
                    .hasMessageNotContaining(root.toString());
        }
    }

    @Test
    void symlinkOutsideManagedRootIsRejected() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("uploads"));
        Path outside = Files.write(temporary.resolve("outside.png"), new byte[]{1});
        Files.createSymbolicLink(root.resolve("escape.png"), outside);
        ManagedImagePaths paths = new ManagedImagePaths(root.toString());
        assertThatThrownBy(() -> paths.validate("/uploads/escape.png")).isInstanceOf(ApiException.class);
    }

    @Test
    void directoryWithImageSuffixIsNotAnImageFile() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("uploads"));
        Files.createDirectory(root.resolve("directory.png"));
        assertThatThrownBy(() -> new ManagedImagePaths(root.toString()).validate("/uploads/directory.png"))
                .isInstanceOf(ApiException.class);
    }
}
