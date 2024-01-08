package ibb.api.imageservice;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/")
public class ImageService {

    @Inject
    ImageMetadataRepository metadataRepository;

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

        final ImageMetadata metadata = metadataRepository.findById(id);
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
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @JsonView(ImageMetadata.Views.Public.class)
    public List<ImageMetadata> upload(MultipartBody body) throws IOException {
        final List<FileUpload> uploadedImages = body.images;
        if (uploadedImages.size() == 0) {
            throw new BadRequestException();
        }
        final List<ImageMetadata> metadatas = new ArrayList<>();
        for (int i = 0; i < uploadedImages.size(); i ++) {
            final String imageId = UUID.randomUUID().toString();
            final String imagePath = storage.save(imageId, uploadedImages.get(i).filePath());
            ImageMetadata metadata = new ImageMetadata();
            metadata.id = imageId;
            metadata.filepath = imagePath;
            metadatas.add(metadata);
            metadataRepository.index(metadatas);
        }
        return metadatas;
    }

    @GET
    @Path("/image-metadata")
    @JsonView(ImageMetadata.Views.Public.class)
    public List<ImageMetadata> list(@RestQuery("ids") List<String> ids) {
        List<String> filteredIds = Arrays.stream(String.join(",", ids).split(","))
            .filter(id -> id != null && !id.isEmpty())
            .toList();
        if (filteredIds.size() == 0) {
            throw new BadRequestException("Ids cannot be empty");
        }

        return metadataRepository.listByIds(filteredIds);
    }

    @GET
    @Path("/image-metadata/pending")
    @JsonView(ImageMetadata.Views.Public.class)
    public List<ImageMetadata> listPending() {
        return metadataRepository.listByStatus(ImageMetadata.Status.PENDING);
    }

    @PUT
    @Path("/image-metadata")
    @Operation(summary = "Approving or rejecting images")
    @JsonView(ImageMetadata.Views.Public.class)
    public List<ImageMetadata> update(List<ImageMetadata> updatingMetadatas) throws IOException {
        ensureNotEmpty(updatingMetadatas);
        ensureApprovingOrRejecting(updatingMetadatas);

        Map<String, ImageMetadata.Status> idToStatus = updatingMetadatas.stream()
            .collect(Collectors.toMap(m -> m.id, m -> m.status));
        List<String> updatingIds = new ArrayList<>(idToStatus.keySet());

        List<ImageMetadata> existingMetadatas = metadataRepository.listByIds(updatingIds);
        ensureNotEmpty(existingMetadatas);
        ensurePending(existingMetadatas);
        
        for (ImageMetadata existing : existingMetadatas) {
            ImageMetadata.Status newStatus = idToStatus.get(existing.id);
            existing.status = newStatus;
        };
        metadataRepository.index(existingMetadatas);

        existingMetadatas.stream()
            .filter(m -> m.status == ImageMetadata.Status.REJECTED)
            .forEach(m -> {
                try {
                    storage.delete(m);
                } catch (IOException e) {
                    Log.errorf("Failed to delete image %s", m.id, e);
                }
            });
        return existingMetadatas;
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

    private void ensurePending(List<ImageMetadata> metadatas) {
        List<String> nonUpdatableIds = metadatas.stream()
            .filter(m -> m.status != ImageMetadata.Status.PENDING)
            .map(m -> m.id)
            .toList();
        
        if (nonUpdatableIds.size() > 0) {
            throw new BadRequestException("Cannot update non-pending images: " + String.join(",", nonUpdatableIds));
        }
    }

    private void ensureApprovingOrRejecting(List<ImageMetadata> metadatas) {
        List<String> notApprovingOrRejecting = metadatas.stream()
            .filter(m -> m.status != ImageMetadata.Status.APPROVED && m.status != ImageMetadata.Status.REJECTED)
            .map(m -> m.id)
            .toList();
        
        if (notApprovingOrRejecting.size() > 0) {
            throw new BadRequestException("Must be approving or rejecting images: " + String.join(",", notApprovingOrRejecting));
        }
    }

    private void ensureNotEmpty(List<ImageMetadata> metadatas) {
        if (metadatas == null || metadatas.size() == 0) {
            throw new BadRequestException("Metadatas cannot be empty");
        }
    } 
}
