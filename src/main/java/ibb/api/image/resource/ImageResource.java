package ibb.api.image.resource;


import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Set;

import javax.imageio.ImageIO;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

import ibb.api.image.model.ImageHolder;
import ibb.api.image.repo.ImageRepository;
import ibb.api.image.util.ImageUtil;

@Path("/images")
@Produces("image/jpeg")
public class ImageResource {

    @ConfigProperty(name = "data.image.dir")
    String imageDir;
    
    @Inject
    ImageRepository repo;
    
    private static final Logger LOG = Logger.getLogger(ImageResource.class);
    
    // only some sizes for the images are allowed to avoid DoS attacks
    private static final Collection<Integer> ALLOWED_SIZES = Set.of(25, 50, 75, 100, 150, 200, 250, 300);
    
    @GET
    @Path("/{id}")
    @Operation(summary = "Get an image in bytes")
    @APIResponse(responseCode = "200", description = "Image successfully returned.")
    @APIResponse(responseCode = "400", description = "Height value not allowed.")
    @APIResponse(responseCode = "404", description = "No image found with the provided id.")
    public Response get(
            @Parameter(
                    description = "id of the image",
                    example = "12679")
            @PathParam("id") String id,
            
            @Parameter(
                    in = ParameterIn.QUERY,
                    description = "height of the scaled image. Only allow: 25, 50, 75, 100, 150, 200, 250, 300", 
                    example = "250")
            @QueryParam("h") Integer height) {

        ImageHolder imageHolder = repo.get(id);
        
        if (imageHolder == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        
        if (height != null && !ALLOWED_SIZES.contains(height)) {
            return Response.status(Status.BAD_REQUEST).build();
        }
            
        StreamingOutput stream = os -> {

            java.nio.file.Path path = java.nio.file.Path.of(imageDir, imageHolder.getFilename());
            try {
                BufferedImage image = ImageIO.read(Files.newInputStream(path));
                
                if (height != null) {
                    image = ImageUtil.scaleImage(image, height);
                }
                
                if (image.getHeight() > 200) {
                    image = ImageUtil.addWatermark(image, imageHolder.getWatermark());
                }

                ImageIO.write(image, "jpeg", os);
                
            } catch (IOException e) {
                LOG.errorv("Image with path {0} cannot be opened.", path);
            }
        };

        return Response
                .ok(stream)
                .header("Cache-Control", "max-age=864000")
                .build();
    }
}
