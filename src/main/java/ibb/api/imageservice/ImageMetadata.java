package ibb.api.imageservice;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class ImageMetadata extends PanacheEntity {
    public String filepath;
    public String watermark;
}
