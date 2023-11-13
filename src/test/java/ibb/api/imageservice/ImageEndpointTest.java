package ibb.api.imageservice;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

@QuarkusTest
@TestHTTPEndpoint(ImageEndpoint.class)
public class ImageEndpointTest {
    
    @Test
    public void testGetImage() throws IOException {
        byte[] imageBytes = RestAssured.given()
            .when().get("/12679")
            .then()
                .statusCode(200)
                .contentType("image/jpeg")
            .extract()
                .response()
                .body().asByteArray();
        
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertNotNull(image);
        assert(image.getWidth() > 0);
        assert(image.getHeight() > 0);
    }

    @Test
    public void testGetImageWithHeightSpecified() throws IOException {
        byte[] imageBytes = RestAssured.given()
            .when().get("/12679?h=250")
            .then()
                .statusCode(200)
                .contentType("image/jpeg")
            .extract()
                .response()
                .body().asByteArray();
        
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertNotNull(image);
        assert(image.getWidth() > 0);
        assertEquals(250, image.getHeight());
    }
}
