package ibb.api.imageservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.logging.Log;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

@QuarkusTest
@TestHTTPEndpoint(ImageEndpoint.class)
@TestProfile(ImageEndpointTest.TemporaryImageStorageTestProfile.class)
public class ImageEndpointTest {

    @TempDir
    static Path tempDir;

    public static class TemporaryImageStorageTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Log.infov("Using temporary directory to store images: {0}", tempDir);
            return Map.of("data.image.dir", tempDir.toString());
        }
    }

    @Test
    public void testCRUD() throws IOException {
        Long[] imageIds = testPostImages();

        for (Long imageId : imageIds) {
            testGetImage(imageId);
            testGetImageWithSpecifiedHeight(imageId, 250);
        };
    }

    private Long[] testPostImages() {
        final File[] files = Stream.of("20608.jpeg", "20609.jpeg", "20610.jpeg")
            .map(filename -> getClass().getClassLoader().getResource(filename).getFile())
            .map(File::new)
            .toArray(File[]::new);

        return RestAssured
            .given()
                .multiPart("images", files[0], "image/jpeg")
                .multiPart("images", files[1], "image/jpeg")
                .multiPart("images", files[2], "image/jpeg")
            .when().post()
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("size()", equalTo(3))
            .extract()
                .response()
                .body().as(Long[].class);
    }

    private void testGetImage(final Long imageId) throws IOException {
        testGetImageWithSpecifiedHeight(imageId, null);
    }
    
    private void testGetImageWithSpecifiedHeight(final Long imageId, final Integer height) throws IOException {
        String url = "/" + imageId;
        if (height != null) {
            url += "?h=" + height;
        }
        final byte[] imageBytes = RestAssured.given()
            .when().get(url)
            .then()
                .statusCode(200)
                .contentType("image/jpeg")
            .extract()
                .response()
                .body().asByteArray();
        
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertNotNull(image);
        assert(image.getWidth() > 0);
        if (height != null) {
            assertEquals(height, image.getHeight());
        } else {
            assert(image.getHeight() > 0);
        }
    }
}
