package ibb.api.imageservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.logging.Log;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
    public void testCRUDWorkflow() throws IOException {
        ImageMetadata[] metadatas = testPostImages();

        for (ImageMetadata metadata : metadatas) {
            testGetImage(metadata.id);
            testGetImageWithSpecifiedHeight(metadata.id, 250);
        };

        testGetImageMetadata(List.of(metadatas[0].id, metadatas[1].id), List.of("PENDING", "PENDING"));
        testGetImageMetadata(List.of(metadatas[2].id), List.of("PENDING"));
        testGetImageMetadata(List.of(metadatas[0].id, metadatas[1].id, metadatas[2].id), List.of("PENDING", "PENDING", "PENDING"));

        testGetPendingImages(3);
        testApproveImages(List.of(metadatas[0], metadatas[1]));
        testGetPendingImages(1);
        testRejectImages(List.of(metadatas[2]));
        testGetPendingImages(0);

        testGetImageMetadata(List.of(
            metadatas[0].id,
            metadatas[1].id,
            metadatas[2].id
        ), List.of(
            "APPROVED",
            "APPROVED",
            "REJECTED"
        ));

        testGetImage(metadatas[0].id);
        testGetImage(metadatas[1].id);
        testGetImageForbidden(metadatas[2].id);
    }

    private ImageMetadata[] testPostImages() {
        final File[] files = Stream.of("20608.jpeg", "20609.jpeg", "20610.jpeg")
            .map(filename -> getClass().getClassLoader().getResource(filename).getFile())
            .map(File::new)
            .toArray(File[]::new);

        return RestAssured
            .given()
                .multiPart("images", files[0], "image/jpeg")
                .multiPart("images", files[1], "image/jpeg")
                .multiPart("images", files[2], "image/jpeg")
            .when().post("/images")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("size()", equalTo(3))
                .body("id", everyItem(notNullValue()))
                .body("status", everyItem(equalTo("PENDING")))
            .extract()
                .response()
                .body().as(ImageMetadata[].class);
    }

    private void testGetImage(final Long imageId) throws IOException {
        testGetImageWithSpecifiedHeight(imageId, null);
    }
    
    private void testGetImageWithSpecifiedHeight(final Long imageId, final Integer height) throws IOException {
        String url = "/images/" + imageId;
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

    private void testGetImageForbidden(long imageId) {
        RestAssured
            .when().get("/images/" + imageId)
            .then()
                .statusCode(403);
    }

    private void testGetImageMetadata(final List<Long> imageIds, final List<String> expectedStatuses) {
        RestAssured
            .given().param("ids", imageIds)
            .when().get("/image-metadata")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(imageIds.size()))
                .body("id", everyItem(notNullValue()))
                .body("filepath", everyItem(nullValue()))
                .body("watermark", everyItem(nullValue()))
                .body("status", is(expectedStatuses));
    }

    private void testGetPendingImages(int expectedSize) {
        RestAssured
            .when().get("/image-metadata/pending")
            .then()
                .contentType(ContentType.JSON)
                .body("size()", equalTo(expectedSize));
    }

    private void testApproveImages(List<ImageMetadata> metadatas) {
        metadatas.forEach(metadata -> metadata.status = ImageMetadata.Status.APPROVED);
        RestAssured
            .given().contentType(ContentType.JSON).body(metadatas)
            .when().put("/image-metadata")
            .then()
                .statusCode(204);
    }

    private void testRejectImages(List<ImageMetadata> metadatas) {
        metadatas.forEach(metadata -> metadata.status = ImageMetadata.Status.REJECTED);
        RestAssured
            .given().contentType(ContentType.JSON).body(metadatas)
            .when().put("/image-metadata")
            .then()
                .statusCode(204);
    }
}
