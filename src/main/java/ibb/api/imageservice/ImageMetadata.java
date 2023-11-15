package ibb.api.imageservice;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class ImageMetadata extends PanacheEntity {

    public static enum Status {
        APPROVED,
        PENDING,
        REJECTED
    };

    @JsonIgnore
    public String filepath;

    @JsonIgnore
    public String watermark;

    public Status status = Status.PENDING;
}
