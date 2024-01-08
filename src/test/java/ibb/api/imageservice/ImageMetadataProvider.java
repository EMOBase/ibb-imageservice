package ibb.api.imageservice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ImageMetadataProvider {
    private List<ImageMetadata> metadatas = new ArrayList<>();

    public List<String> getAllImageIds() {
        Objects.requireNonNull(metadatas, "Metadatas are not stored yet!");
        return metadatas.stream().map(m -> m.id).toList();
    }

    public String getOneImageId() {
        if (metadatas.size() == 0) {
            throw new IllegalStateException("No image metadata is stored yet!");
        }
        return metadatas.get(0).id;
    }

    public String getImageIdForApproval() {
        if (metadatas.size() < 2) {
            throw new IllegalStateException("Not enough image metadata is stored yet!");
        }
        return metadatas.get(0).id;
    }

    public String getImageIdForRejection() {
        if (metadatas.size() < 2) {
            throw new IllegalStateException("Not enough image metadata is stored yet!");
        }
        return metadatas.get(1).id;
    }

    public void addMetadatas(List<ImageMetadata> metadatas) {
        this.metadatas.addAll(metadatas);
    }
}
