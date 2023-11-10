package ibb.api.image.repo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ibb.api.image.model.ImageHolder;
import ibb.api.image.util.CSVUtil;
import io.quarkus.runtime.Startup;

@ApplicationScoped
@Startup
public class InMemoryImageRepository implements ImageRepository {
    
    private static final Logger LOG = Logger.getLogger(InMemoryImageRepository.class);

    @ConfigProperty(name = "data.image-info.uri")
    String infoURI;
    
    private Map<String, ImageHolder> mapFromId;
    
    @Override
    public ImageHolder get(String id) {
        return mapFromId.get(id);
    }

    @PostConstruct
    void init() {
        loadData();
    }
    
    private void loadData() {
        mapFromId = loadImageHoldersFromFile(infoURI);
    }
    
    private Map<String, ImageHolder> loadImageHoldersFromFile(String uri) {
        try (Stream<CSVRecord> records = CSVUtil.parseCSVFile(uri, "id", "filename", "watermark")) {
            LOG.infov("Loading image info data from {0}.", uri);
            
            return records
                    .map(r -> new ImageHolder.Builder(r.get("id"), r.get("filename"))
                        .watermark(r.get("watermark"))
                        .build())
                    .collect(Collectors.toMap(ImageHolder::getId, Function.identity()));
            
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            LOG.errorv("Fail to parse image info file from {0}: {1}.", uri, e.getMessage());
        }
        return new HashMap<>();
    }
}
