package ibb.api.imageservice;

import java.io.IOException;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
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
    }

    @Inject
    ElasticsearchClient esClient;

    public void deleteIndex() {
        try {
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

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (var metadata : metadatas) {
            br.operations(op -> op
                .index(idx -> idx
                    .index(indexName)
                    .id(metadata.id)
                    .document(metadata)
                ));
        }
        try {
            BulkResponse response = esClient.bulk(br.build());
            if (response.errors()) {
                for (BulkResponseItem item: response.items()) {
                    ErrorCause error = item.error();
                    if (error != null) {
                        Log.error(error.reason());
                    }
                }
                throw new RuntimeException("Bulk had errors");
            }
            if (refreshAfterWrite) {
                refreshIndex();
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
