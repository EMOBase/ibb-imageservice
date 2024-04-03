package ibb.api.imageservice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class ImageMetadata {
    public static enum Status {
        PENDING,
        APPROVED
    };

    public static class Views {
        public static class Public {}
        public static class Private extends Public {}
    }

    @JsonView(Views.Public.class)
    public String id;

    @JsonView(Views.Public.class)
    public Status status = Status.PENDING;

    @JsonView(Views.Private.class)
    public String filepath;

    @JsonView(Views.Private.class)
    public String watermark;
}
