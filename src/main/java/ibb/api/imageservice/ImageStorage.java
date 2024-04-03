package ibb.api.imageservice;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Handle the actual storage of images.
 * Current implementation: store the images in a local directory.
 */
@ApplicationScoped
public class ImageStorage {

    @ConfigProperty(name = "data.image.dir")
    String imageDir;

    @ConfigProperty(name = "data.image.community-subdir")
    String communitySubdir;

    void createCommunitySubdir(@Observes StartupEvent ev) throws IOException {
        Path path = Path.of(imageDir, communitySubdir);
        Files.createDirectories(path);
        if (!Files.isWritable(path)) {
            throw new IOException("Directory " + path + " is not writeable");
        }
    }

    public void delete(ImageMetadata metadata) throws IOException {
        Files.delete(Path.of(imageDir, metadata.filepath));
    }

    public String save(final String imageId, final Path sourceImage) throws IOException {
        final String relativeTargetImage = Path.of(communitySubdir, imageId).toString();
        final Path targetImage = Path.of(imageDir, relativeTargetImage);
        Files.move(sourceImage, targetImage);
        return relativeTargetImage;
    }

    public BufferedImage get(final ImageMetadata metadata, final Integer height) throws IOException {
        final Path path = Path.of(imageDir, metadata.filepath);
        BufferedImage image = ImageIO.read(Files.newInputStream(path));
        
        if (height != null) {
            image = ImageUtil.scaleImage(image, height);
        }
        
        if (image.getHeight() > 200 && metadata.watermark != null) {
            image = ImageUtil.addWatermark(image, metadata.watermark);
        }
        return image;
    }
}
