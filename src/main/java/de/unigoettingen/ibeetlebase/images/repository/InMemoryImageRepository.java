package de.unigoettingen.ibeetlebase.images.repository;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

import org.apache.commons.csv.CSVRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import de.unigoettingen.ibeetlebase.images.model.ImageHolder;
import de.unigoettingen.ibeetlebase.images.util.CSVUtil;

@ApplicationScoped
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
