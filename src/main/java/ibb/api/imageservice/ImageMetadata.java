package ibb.api.imageservice;

import com.fasterxml.jackson.annotation.JsonView;

public class ImageMetadata {
    public static enum Status {
        APPROVED,
        PENDING,
        REJECTED
    };

    public static class Views {
        public static class Public {}
        public static class Private extends Public {}
    }

    @JsonView(Views.Public.class)
    public String id;

    @JsonView(Views.Public.class)
    public Status status = Status.PENDING;

    @JsonView(Views.Public.class)
    public String comment;

    @JsonView(Views.Private.class)
    public String filepath;

    @JsonView(Views.Private.class)
    public String watermark;
}
