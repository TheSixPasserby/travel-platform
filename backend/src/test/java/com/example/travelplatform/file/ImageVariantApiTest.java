package com.example.travelplatform.file;

import com.example.travelplatform.testsupport.HttpTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImageVariantApiTest {
    static final Path ROOT;
    static { try { ROOT = Files.createTempDirectory("m16-image-variants-"); Files.createDirectory(ROOT.resolve("uploads")); }
        catch (IOException exception) { throw new UncheckedIOException(exception); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("travel.storage.upload-dir", () -> ROOT.resolve("uploads").toString());
        registry.add("travel.storage.thumbnail-dir", () -> ROOT.resolve("cache").toString());
    }
    @LocalServerPort int port;
    @Autowired DataSource source;
    HttpTestSupport h;
    @BeforeEach void setup() { h = new HttpTestSupport(source, port, "m2i"); }
    @AfterAll static void cleanup() throws IOException {
        try (var paths = Files.walk(ROOT)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
    byte[] image(String filename, boolean alpha) throws IOException {
        var image = new BufferedImage(1280, 800, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var random = new Random(16);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, (alpha ? 0x80000000 : 0xff000000) | random.nextInt(0xffffff));
        ImageIO.write(image, "png", ROOT.resolve("uploads").resolve(filename).toFile());
        return Files.readAllBytes(ROOT.resolve("uploads").resolve(filename));
    }
    HttpTestSupport.Result get(String path) { return h.json("GET", path, null, null); }
    @Test void managedCardVariantShrinksBytesCachesAndPreservesOriginalDownload() throws IOException {
        byte[] original = image("photo.png", false);
        var response = get("/uploads/photo.png?width=320");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("Content-Type")).startsWith("image/jpeg");
        var resized = ImageIO.read(new ByteArrayInputStream(response.bytes()));
        assertThat(resized.getWidth()).isEqualTo(320); assertThat(resized.getHeight()).isEqualTo(200);
        assertThat(response.bytes().length).isLessThan(original.length / 4);
        assertThat(response.header("Cache-Control")).contains("immutable");
        assertThat(get("/uploads/photo.png?width=320").bytes()).isEqualTo(response.bytes());
        assertThat(get("/uploads/photo.png").bytes()).isEqualTo(original);
    }
    @Test void transparencyIsPreservedAndUnapprovedWidthsAreRejected() throws IOException {
        image("alpha.png", true);
        var response = get("/uploads/alpha.png?width=640");
        assertThat(response.status()).isEqualTo(200);
        var image = ImageIO.read(new ByteArrayInputStream(response.bytes()));
        assertThat(image.getWidth()).isEqualTo(640); assertThat(image.getColorModel().hasAlpha()).isTrue();
        for (String width : List.of("0", "480", "10000", "hello")) assertThat(get("/uploads/alpha.png?width=" + width).status()).isEqualTo(400);
        assertThat(get("/uploads/missing.png?width=320").status()).isEqualTo(404);
    }
    @Test void externalSymlinksAreRejectedBeforeTransforming() throws IOException {
        Path external = ROOT.resolve("outside.png"); Files.write(external, new byte[]{1, 2, 3});
        Files.createSymbolicLink(ROOT.resolve("uploads/escape.png"), external);
        assertThat(get("/uploads/escape.png?width=320").status()).isEqualTo(404);
        assertThat(Files.exists(ROOT.resolve("cache/escape.png"))).isFalse();
    }
    @Test void originalFallbackCanBeRevalidatedAndExifPhoneOrientationIsApplied() throws IOException {
        var small = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(small, "png", ROOT.resolve("uploads/small.png").toFile());
        assertThat(get("/uploads/small.png?width=320").header("Cache-Control")).doesNotContain("immutable");
        var photo = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
        var graphics = photo.createGraphics();
        graphics.setColor(java.awt.Color.RED); graphics.fillRect(0, 0, 640, 400);
        graphics.setColor(java.awt.Color.GREEN); graphics.fillRect(640, 0, 640, 400);
        graphics.setColor(java.awt.Color.BLUE); graphics.fillRect(0, 400, 640, 400);
        graphics.setColor(java.awt.Color.YELLOW); graphics.fillRect(640, 400, 640, 400); graphics.dispose();
        var encoded = new ByteArrayOutputStream(); ImageIO.write(photo, "jpg", encoded);
        byte[] jpeg = encoded.toByteArray();
        int[] expectedTopLeft = {0, 0xff0000, 0x00ff00, 0xffff00, 0x0000ff, 0xff0000, 0x0000ff, 0xffff00, 0x00ff00};
        for (int orientation = 1; orientation <= 8; orientation++) {
            byte[] exif = {69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,(byte)orientation,0,0,0,0,0,0,0};
            var file = new ByteArrayOutputStream(); file.write(jpeg, 0, 2); file.write(255); file.write(225); file.write(0); file.write(exif.length + 2); file.write(exif); file.write(jpeg, 2, jpeg.length - 2);
            String name = "phone-" + orientation + ".jpg"; Files.write(ROOT.resolve("uploads").resolve(name), file.toByteArray());
            var response = get("/uploads/" + name + "?width=320");
            assertThat(response.status()).isEqualTo(200);
            var output = ImageIO.read(new ByteArrayInputStream(response.bytes()));
            assertThat(output.getWidth()).isEqualTo(320);
            assertThat(output.getHeight()).isEqualTo(orientation >= 5 ? 512 : 200);
            var actual = new java.awt.Color(output.getRGB(30, 30)); var expected = new java.awt.Color(expectedTopLeft[orientation]);
            assertThat(Math.abs(actual.getRed() - expected.getRed()) + Math.abs(actual.getGreen() - expected.getGreen()) + Math.abs(actual.getBlue() - expected.getBlue())).isLessThan(30);
            assertThat(get("/uploads/" + name).bytes()).isEqualTo(file.toByteArray());
        }
    }
}
