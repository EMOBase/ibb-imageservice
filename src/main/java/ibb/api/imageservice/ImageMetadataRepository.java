package ibb.api.imageservice;

import java.io.IOException;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ImageMetadataRepository {

    @ConfigProperty(name = "imageservice.elasticsearch.index-name")
    String indexName;

    @ConfigProperty(name = "imageservice.elasticsearch.refresh-after-write", defaultValue = "false")
    boolean refreshAfterWrite;

    @ConfigProperty(name = "imageservice.elasticsearch.delete-index-on-start", defaultValue = "false")
    boolean deleteIndexOnStart;

    void onStart(@Observes StartupEvent ev) {
        if (deleteIndexOnStart) {
            deleteIndex();
        }
        createIndexIfNotExists();
        Log.infof("Elasticsearch index name: %s", indexName);
    }

    @Inject
    ElasticsearchClient esClient;

    public void deleteIndex() {
        try {
            Log.debug("Deleting index: " + indexName);
            esClient.indices().delete(b -> b.index(indexName).ignoreUnavailable(true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void refreshIndex() {
        try {
            esClient.indices().refresh(b -> b.index(indexName).ignoreUnavailable(true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ImageMetadata findById(String id) {
        try {
            return esClient.get(b -> b.index(indexName).id(id), ImageMetadata.class).source();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ImageMetadata> listByIds(List<String> ids) {
        try {
            IdsQuery.Builder queryBuilder = new IdsQuery.Builder();
            queryBuilder.values(ids);

            return esClient.search(s -> s
                    .index(indexName)
                    .query(q -> q
                        .ids(queryBuilder.build())),
                         ImageMetadata.class
                    ).hits().hits().stream().map(h -> h.source()).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ImageMetadata> listByStatus(ImageMetadata.Status status) {
        try {
            return esClient.search(s -> s
                    .index(indexName)
                    .query(q -> q
                        .term(t -> t.field("status.keyword").value(status.name()))),
                         ImageMetadata.class
                    ).hits().hits().stream().map(h -> h.source()).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void index(List<ImageMetadata> metadatas) {
        ensureValidMetadatas(metadatas);

        try (BulkIngester<Void> ingester = BulkIngester.of(b -> b.client(esClient))) {
            for (var metadata : metadatas) {
                ingester.add(b -> b
                    .index(idx -> idx
                        .index(indexName)
                        .id(metadata.id)
                        .document(metadata)
                    )
                );
            }
        }
        if (refreshAfterWrite) {
            refreshIndex();
        }
    }

    public void delete(List<String> ids) {
        try (BulkIngester<Void> ingester = BulkIngester.of(b -> b.client(esClient))) {
            for (var id : ids) {
                ingester.add(b -> b
                    .delete(d -> d
                        .index(indexName)
                        .id(id)
                    )
                );
            }
        }
        if (refreshAfterWrite) {
            refreshIndex();
        }
    }

    public void createIndexIfNotExists() {
        try {
            if (!esClient.indices().exists(b -> b.index(indexName)).value()) {
                esClient.indices().create(b -> b.index(indexName));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void ensureValidMetadatas(List<ImageMetadata> metadatas) {
        for (var metadata : metadatas) {
            if (metadata.id == null || metadata.id.isEmpty()) {
                throw new IllegalArgumentException("Metadata id cannot be empty");
            }
            if (metadata.filepath == null || metadata.filepath.isEmpty()) {
                throw new IllegalArgumentException("Metadata filepath cannot be empty");
            }
        }
    }
}
