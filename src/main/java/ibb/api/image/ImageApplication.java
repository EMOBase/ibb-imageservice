package ibb.api.image;

import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(
        info = @Info(title = "Image Service",
                description = "Get the original or scaled image",
                version = "1.0")
)
public class ImageApplication extends Application {

}
