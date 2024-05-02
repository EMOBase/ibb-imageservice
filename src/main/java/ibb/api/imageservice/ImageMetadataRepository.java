package ibb.api.imageservice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ImageMetadataRepository {

    @ConfigProperty(name = "imageservice.elasticsearch.index-prefix")
    String indexPrefix;

    @ConfigProperty(name = "imageservice.elasticsearch.delete-index-on-start", defaultValue = "false")
    boolean deleteIndexOnStart;

    @ConfigProperty(name = "data.load-on-start", defaultValue = "true")
    boolean loadOnStart;

    @ConfigProperty(name = "data.dir")
    String dataDir;

    @Inject
    ElasticsearchClient esClient;

    @Inject
    ImageStorage storage;

    void onStart(@Observes StartupEvent ev) throws IOException {
        if (deleteIndexOnStart) {
            esClient.indices().delete(b -> b.index(
                getCommunityIndexName(),
                getIBeetleIndexName()
            ).ignoreUnavailable(true));
        }

        if (loadOnStart) {
            if (esClient.indices().exists(b -> b.index(getIBeetleIndexName())).value()) {
                Log.infov("Index {0} already exists, skipping loading data", getIBeetleIndexName());
            } else {
                Path path = Path.of(dataDir, "ibeetle_images.tsv");
                if (path.toFile().canRead()) {
                    Log.infov("Loading iBeetle images from {0}", path);
                    loadIBeetleImages(path);
                } else {
                    Log.infov("iBeetle images file {0} not found, skipping loading data", path);
                }
            }
        }
    }

    private void loadIBeetleImages(Path path) throws IOException {
        AtomicLong count = new AtomicLong();
        try (
            var ingester = BulkIngester.<Void>of(b -> b.client(esClient));
            var stream = Files.lines(path)
        ) {
            stream.filter(line -> !line.startsWith("#"))
                .map(line -> line.split("\t"))
                .map(cols -> {
                    ImageMetadata metadata = new ImageMetadata();
                    metadata.id = cols[0].trim();
                    metadata.filepath = cols[1].trim();
                    if (cols.length > 2) {
                        metadata.watermark = cols[2].trim();
                    }
                    metadata.status = ImageMetadata.Status.APPROVED;
                    return metadata;
                })
                .forEach(metadata -> {
                    count.incrementAndGet();
                    ingester.add(b -> b
                        .index(idx -> idx
                            .index(getIBeetleIndexName())
                            .id(metadata.id)
                            .document(metadata)
                        )
                    );
                });
        }
        esClient.indices().refresh(b -> b.index(getIBeetleIndexName()).ignoreUnavailable(true));
        // long count = esClient.count(b -> b.index(getIBeetleIndexName()).ignoreUnavailable(true)).count();
        Log.infov("Loaded {0} iBeetle images", count.get());
    }

    public ImageMetadata findById(String id) throws IOException {
        return findByIds(List.of(id)).stream().findFirst().orElse(null);
    }

    public List<ImageMetadata> findByIds(List<String> ids) throws IOException {
        return esClient.search(s -> s
            .index(getReadIndexName())
            .allowNoIndices(true)
            .size(10000)
            .query(q -> q.ids(i -> i.values(ids)))
        , ImageMetadata.class).hits().hits().stream().map(s -> s.source()).toList();
    }

    public List<ImageMetadata> findPending(int from, int size) throws IOException {
        return esClient.search(s -> s
            .index(getCommunityIndexName())
            .allowNoIndices(true)
            .from(from)
            .size(size)
            .query(q -> q.term(t -> t.field("status.keyword").value("PENDING")))
        , ImageMetadata.class).hits().hits().stream().map(s -> s.source()).toList();
    }

    public List<ImageMetadata> findCommunityImagesByIds(List<String> ids) throws IOException {
        return esClient.search(s -> s
            .index(getCommunityIndexName())
            .allowNoIndices(true)
            .size(10000)
            .query(q -> q.ids(i -> i.values(ids)))
        , ImageMetadata.class).hits().hits().stream().map(s -> s.source()).toList();
    }

    public List<ImageMetadata> create(List<FileUpload> uploadedImages) throws IOException {
        List<ImageMetadata> metadatas = new ArrayList<>();
        for (int i = 0; i < uploadedImages.size(); i ++) {
            String imageId = UUID.randomUUID().toString();
            String imagePath = storage.save(imageId, uploadedImages.get(i).filePath());
            ImageMetadata metadata = new ImageMetadata();
            metadata.id = imageId;
            metadata.filepath = imagePath;
            metadatas.add(metadata);
        }
        try (BulkIngester<Void> ingester = BulkIngester.of(b -> b.client(esClient))) {
            for (var metadata : metadatas) {
                ingester.add(b -> b
                    .index(idx -> idx
                        .index(getCommunityIndexName())
                        .id(metadata.id)
                        .document(metadata)
                    )
                );
            }
        }
        esClient.indices().refresh(b -> b.index(getCommunityIndexName()).ignoreUnavailable(true));
        return metadatas;
    }

    public void approve(List<String> ids) throws IOException {
        esClient.updateByQuery(b -> b
            .index(getCommunityIndexName())
            .ignoreUnavailable(true)
            .query(q -> q.ids(i -> i.values(ids)))
            .script(s -> s.inline(i -> i.source("ctx._source.status = 'APPROVED'")))
            .refresh(true)
        );
    }

    public void reject(List<String> ids) throws IOException {
        findCommunityImagesByIds(ids).forEach(m -> {
            try {
                storage.delete(m);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete image " + m.id, e);
            }
        });
        esClient.deleteByQuery(b -> b
            .index(getCommunityIndexName())
            .ignoreUnavailable(true)
            .query(q -> q.ids(i -> i.values(ids)))
            .refresh(true)
        );
    }

    private String getReadIndexName() {
        return indexPrefix + "-metadata-*";
    }

    private String getCommunityIndexName() {
        return indexPrefix + "-metadata-community";
    }

    private String getIBeetleIndexName() {
        return indexPrefix + "-metadata-ibeetle";
    }
}
