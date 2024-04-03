package ibb.api.imageservice;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.jboss.resteasy.reactive.RestResponse.StatusCode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.logging.Log;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestHTTPEndpoint(ImageEndpoints.class)
@TestProfile(ImageServiceCRUDWorkflowTest.TemporaryImageStorageTestProfile.class)
public class ImageServiceCRUDWorkflowTest {

    private static class Timeline {
        public static final int BEFORE_THREE_IMAGES_UPLOADED = 2;
        public static final int AFTER_THREE_IMAGES_UPLOADED = 3;
        public static final int BEFORE_ONE_IMAGE_APPROVED = 4;
        public static final int AFTER_ONE_IMAGE_APPROVED = 5;
        public static final int BEFORE_ONE_IMAGE_REJECTED = 6;
        public static final int AFTER_ONE_IMAGE_REJECTED = 7;
    }

    @TempDir
    static Path tempDir;

    public static class TemporaryImageStorageTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Objects.requireNonNull(tempDir, "tempDir is not set!");
            Log.infov("Using temporary directory to store images: {0}", tempDir);
            return Map.of("data.image.dir", tempDir.toString());
        }
    }

    private static ImageMetadataProvider imageMetadataProvider = new ImageMetadataProvider();

    @Test
    @Order(Timeline.BEFORE_THREE_IMAGES_UPLOADED)
    public void uploadMultipleImages_ShouldSucceed() {
        final File[] files = Stream.of("20608.jpeg", "20609.jpeg")
            .map(filename -> getClass().getClassLoader().getResource(filename).getFile())
            .map(File::new)
            .toArray(File[]::new);

        final ImageMetadata[] metadatas = RestAssured
            .given()
                .multiPart("images", files[0], "image/jpeg")
                .multiPart("images", files[1], "image/jpeg")
            .when().post("/images")
            .then()
                .statusCode(StatusCode.OK)
                .contentType("application/json")
                .body("size()", equalTo(2))
                .body("id", everyItem(notNullValue()))
                .body("status", everyItem(equalTo("PENDING")))
                .body("filepath", everyItem(nullValue()))
                .body("watermark", everyItem(nullValue()))
            .extract()
                .response()
                .body().as(ImageMetadata[].class);
        
        imageMetadataProvider.addMetadatas(List.of(metadatas));
    }

    @Test
    @Order(Timeline.BEFORE_THREE_IMAGES_UPLOADED)
    public void uploadOneImage_ShouldSucceed() {
        final File[] files = Stream.of("20610.jpeg")
            .map(filename -> getClass().getClassLoader().getResource(filename).getFile())
            .map(File::new)
            .toArray(File[]::new);

        final ImageMetadata[] metadatas = RestAssured
            .given()
                .multiPart("images", files[0], "image/jpeg")
            .when().post("/images")
            .then()
                .statusCode(StatusCode.OK)
                .contentType("application/json")
                .body("size()", equalTo(1))
                .body("id", everyItem(notNullValue()))
                .body("status", everyItem(equalTo("PENDING")))
                .body("filepath", everyItem(nullValue()))
                .body("watermark", everyItem(nullValue()))
            .extract()
                .response()
                .body().as(ImageMetadata[].class);
        
        imageMetadataProvider.addMetadatas(List.of(metadatas));
    }
    
    @Test
    @Order(Timeline.BEFORE_THREE_IMAGES_UPLOADED)
    public void uploadImages_WithoutMultipartRequiredKeys_ShouldFail() {
        RestAssured
            .given()
                .multiPart("hello", "world")
            .when().post("/images")
            .then()
                .statusCode(StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(Timeline.BEFORE_THREE_IMAGES_UPLOADED)
    public void uploadZeroImage_ShouldFail() {
        RestAssured
            .given()
                .multiPart("images", "")
            .when().post("/images")
            .then()
                .statusCode(StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(Timeline.AFTER_THREE_IMAGES_UPLOADED)
    public void actualImages_ShouldBeFetchable_AfterUploaded() throws IOException {
        assertEquals(3, imageMetadataProvider.getAllImageIds().size());
        for (String imageId : imageMetadataProvider.getAllImageIds()) {
            final String url = "/images/" + imageId;
            final byte[] imageBytes = RestAssured.given()
                .when().get(url)
                .then()
                    .statusCode(StatusCode.OK)
                    .contentType("image/jpeg")
                .extract()
                    .response()
                    .body().asByteArray();
            
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            assertNotNull(image);
            assert(image.getWidth() > 0);
            assert(image.getHeight() > 0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {25, 100, 250, 300})
    @Order(Timeline.AFTER_THREE_IMAGES_UPLOADED)
    public void actualImages_ShouldBeFetchable_WhenSpecifiedHeightIsAllowed(int height) throws IOException {
        final String imageId = imageMetadataProvider.getOneImageId();
        final String url = "/images/" + imageId + "?h=" + height;
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
        assertEquals(height, image.getHeight());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 17, 20000000})
    @Order(Timeline.AFTER_THREE_IMAGES_UPLOADED)
    public void actualImages_ShoudNotBeFetched_WhenSpecifiedHeightIsNotAllowed(int height) throws IOException {
        final String imageId = imageMetadataProvider.getOneImageId();
        final String url = "/images/" + imageId + "?h=" + height;

        RestAssured.given()
            .when().get(url)
            .then()
                .statusCode(StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(Timeline.AFTER_THREE_IMAGES_UPLOADED)
    public void actualImages_ShouldNotBeFetched_WhenNotExist() throws IOException {
        final String imageId = "not-exists";
        final String url = "/images/" + imageId;

        RestAssured.given()
            .when().get(url)
            .then()
                .statusCode(StatusCode.NOT_FOUND);
    }

    @Test
    @Order(Timeline.AFTER_THREE_IMAGES_UPLOADED)
    public void fetchOneImageMetadata_ShouldReturnAppropriateFields_AfterActualImagesAreUploaded() {
        final String imageId = imageMetadataProvider.getOneImageId();
        final String url = "/image-metadata/get";
        RestAssured
            .given()
                .contentType(ContentType.JSON)
                .body(List.of(imageId))
            .when().post(url)
            .then()
                .statusCode(StatusCode.OK)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("id", everyItem(equalTo(imageId)))
                .body("filepath", everyItem(nullValue()))
                .body("watermark", everyItem(nullValue()))
                .body("status", everyItem(equalTo("PENDING")));
    }

    @Test
    @Order(Timeline.AFTER_THREE_IMAGES_UPLOADED)
    public void fetchMultipleImageMetadatas_ShouldReturnAppropriateFields_AfterActualImagesAreUploaded() {
        final List<String> imageIds = imageMetadataProvider.getAllImageIds();
        final String url = "/image-metadata/get";
        RestAssured
            .given()
                .contentType(ContentType.JSON)
                .body(imageIds)
            .when().post(url)
            .then()
                .statusCode(StatusCode.OK)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(imageIds.size()))
                .body("id", equalTo(imageIds))
                .body("filepath", everyItem(nullValue()))
                .body("watermark", everyItem(nullValue()))
                .body("status", everyItem(equalTo("PENDING")));
    }


    @Test
    @Order(Timeline.BEFORE_ONE_IMAGE_APPROVED)
    public void approveImage_ShouldSucceed() {
        final String approvingImageId = imageMetadataProvider.getImageIdForApproval();
        final String url = "/image-metadata/approve";
        RestAssured
            .given()
                .contentType(ContentType.JSON)
                .body(List.of(approvingImageId))
            .when().post(url)
            .then()
                .statusCode(StatusCode.NO_CONTENT);
    }

    @Test
    @Order(Timeline.AFTER_ONE_IMAGE_APPROVED)
    public void fetchApprovedImage_ShouldSucceed() {
        final String approvedImageId = imageMetadataProvider.getImageIdForApproval();
        final String url = "/images/" + approvedImageId;
        RestAssured
            .when().get(url)
            .then()
                .statusCode(StatusCode.OK)
                .contentType("image/jpeg");
    }

    @Test
    @Order(Timeline.AFTER_ONE_IMAGE_APPROVED)
    public void fetchMetadataOfApprovedImage_ShouldSucceed() {
        final String approvedImageId = imageMetadataProvider.getImageIdForApproval();
        final String url = "/image-metadata/get";
        RestAssured
            .given()
                .contentType(ContentType.JSON)
                .body(List.of(approvedImageId))
            .when().post(url)
            .then()
                .statusCode(StatusCode.OK)
                .contentType("application/json")
                .body("size()", equalTo(1))
                .body("id", everyItem(notNullValue()))
                .body("status", everyItem(equalTo("APPROVED")));
    }

    @Test
    @Order(Timeline.BEFORE_ONE_IMAGE_REJECTED)
    public void rejectImage_ShouldSucceed() {
        final String rejectingImageId = imageMetadataProvider.getImageIdForRejection();
        final String url = "/image-metadata/reject";
        RestAssured
            .given()
                .contentType(ContentType.JSON)
                .body(List.of(rejectingImageId))
            .when().post(url)
            .then()
                .statusCode(StatusCode.NO_CONTENT);
    }

    @Test
    @Order(Timeline.AFTER_ONE_IMAGE_REJECTED)
    public void fetchRejectedImage_ShouldFail() {
        final String rejectedImageId = imageMetadataProvider.getImageIdForRejection();
        final String url = "/images/" + rejectedImageId;
        RestAssured
            .when().get(url)
            .then()
                .statusCode(StatusCode.NOT_FOUND);
    }

    @Test
    @Order(Timeline.AFTER_ONE_IMAGE_REJECTED)
    public void fetchMetadataOfRejectedImage_ShouldSucceed() {
        final String approvedImageId = imageMetadataProvider.getImageIdForApproval();
        final String url = "/image-metadata/get";
        RestAssured
            .given()
                .contentType(ContentType.JSON)
                .body(List.of(approvedImageId))
            .when().post(url)
            .then()
                .statusCode(StatusCode.OK)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("id", everyItem(notNullValue()))
                .body("status", everyItem(equalTo("APPROVED")));
    }
}
