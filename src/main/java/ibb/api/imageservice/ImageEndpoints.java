package ibb.api.imageservice;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/")
public class ImageEndpoints {

    @Inject
    ImageMetadataRepository metadataRepository;

    @Inject
    ImageStorage storage;

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
    ) throws IOException {

        ImageMetadata metadata = metadataRepository.findById(id);
        if (metadata == null) {
            return Response.status(Status.NOT_FOUND).build();
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
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @JsonView(ImageMetadata.Views.Public.class)
    public List<ImageMetadata> upload(@Valid MultipartBody body) throws IOException {
        List<FileUpload> uploadedImages = body.images;
        return metadataRepository.create(uploadedImages);
    }

    @POST
    @Path("/image-metadata/get")
    @JsonView(ImageMetadata.Views.Public.class)
    public List<ImageMetadata> list(@NotEmpty @Size(max = 10000) List<@NotBlank String> ids) throws IOException {
        return metadataRepository.findByIds(ids);
    }

    @POST
    @Path("/image-metadata/reject")
    @Operation(summary = "Delete images")
    public void reject(@NotEmpty @Size(max = 10000) List<@NotBlank String> ids) throws IOException {
        metadataRepository.reject(ids);
    }

    @POST
    @Path("/image-metadata/approve")
    @Operation(summary = "Approve images")
    public void approve(@NotEmpty @Size(max = 10000) List<@NotBlank String> ids) throws IOException {
        metadataRepository.approve(ids);
    }

    @Schema(type = SchemaType.STRING, format = "binary")
    public static interface UploadItemSchema {}

    public static class UploadFormSchema {
        public List<UploadItemSchema> images;
    }

    @Schema(implementation = UploadFormSchema.class)
    public static class MultipartBody {
        @RestForm("images")
        public @NotEmpty @Size(max = 10) List<FileUpload> images;
    }
}
