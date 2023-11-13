package ibb.api.imageservice.model;


import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ImageHolder {
    
    private String id;
    private String filename;
    private String watermark;
    
    public static class Builder { 
        private String id;
        private String filename;
        private String watermark;
        
        public Builder(String id, String filename) {
            this.id = id;
            this.filename = filename;
        }
        
        public Builder watermark(String val) {
            watermark = val;
            return this;
        }
        
        public ImageHolder build() {
            return new ImageHolder(this);
        }
    }
    
    public ImageHolder(Builder builder) {
        id = builder.id;
        filename = builder.filename;
        watermark = builder.watermark;
    }
    
    public String getId() {
        return id;
    }
    
    public String getWatermark() {
        return watermark;
    }
    
    public String getFilename() {
        return filename;
    }
}
