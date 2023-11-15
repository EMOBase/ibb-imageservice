package ibb.api.imageservice;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/")
public class ImageEndpoint {

    @Inject
    ImageStorage storage;

    // only some sizes for the images are allowed to avoid DoS attacks
    private static final Collection<Integer> ALLOWED_SIZES = Set.of(25, 50, 75, 100, 150, 200, 250, 300);

    @GET
    @Path("/images/{id}")
    @Produces("image/jpeg")
    @Operation(summary = "Get an image in bytes")
    public Response get(
        @Parameter(
            description = "id of the image",
            example = "12679")
        @RestPath String id,
        
        @Parameter(
            in = ParameterIn.QUERY,
            description = "height of the scaled image. Only allow: 25, 50, 75, 100, 150, 200, 250, 300", 
            example = "250")
        @RestQuery("h") Integer height
    ) {

        final ImageMetadata metadata = ImageMetadata.findById(id);
        if (metadata == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        if (metadata.status == ImageMetadata.Status.REJECTED) {
            return Response.status(Status.FORBIDDEN).build();
        }
        if (height != null && !ALLOWED_SIZES.contains(height)) {
            return Response.status(Status.BAD_REQUEST).build();
        }
            
        StreamingOutput stream = outputStream -> {
            BufferedImage image = storage.get(metadata, height);
            ImageIO.write(image, "jpeg", outputStream);
        };

        return Response
                .ok(stream)
                .header("Cache-Control", "max-age=864000")
                .build();
    }


    @POST
    @Path("/images")
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public List<ImageMetadata> upload(MultipartBody body) {
        final List<FileUpload> uploadedImages = body.images;
        if (uploadedImages.size() == 0) {
            throw new BadRequestException();
        }
        final List<ImageMetadata> metadatas = uploadedImages.stream().map(image -> new ImageMetadata()).toList();
        ImageMetadata.persist(metadatas);

        try {
            for (int i = 0; i < uploadedImages.size(); i ++) {
                storage.save(metadatas.get(i), uploadedImages.get(i).filePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new InternalServerErrorException();
        }
        return metadatas;
    }

    @GET
    @Path("/image-metadata")
    public List<ImageMetadata> list(@RestQuery("ids") List<Long> ids) {
        return ImageMetadata.list("id in ?1", ids);
    }

    @GET
    @Path("/image-metadata/pending")
    public List<ImageMetadata> listPending() {
        return ImageMetadata.list("status", ImageMetadata.Status.PENDING);
    }

    @PUT
    @Path("/image-metadata")
    @Transactional
    public void update(List<ImageMetadata> metadatas) throws IOException {
        for (ImageMetadata metadata : metadatas) {
            final ImageMetadata existing = ImageMetadata.findById(metadata.id);
            if (existing == null || existing.status != ImageMetadata.Status.PENDING) {
                return;
            }
            existing.status = metadata.status;
            if (metadata.status == ImageMetadata.Status.REJECTED) {
                storage.delete(existing);
            }
        };
    }

    @Schema(type = SchemaType.STRING, format = "binary")
    public interface UploadItemSchema {}

    public class UploadFormSchema {
        public List<UploadItemSchema> images;
    }

    @Schema(implementation = UploadFormSchema.class)
    public static class MultipartBody {
        @RestForm("images")
        public List<FileUpload> images;
    }
}
