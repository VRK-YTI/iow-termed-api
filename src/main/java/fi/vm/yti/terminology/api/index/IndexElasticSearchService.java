package fi.vm.yti.terminology.api.index;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.exception.ElasticEndpointException;
import fi.vm.yti.terminology.api.util.Parameters;
import static fi.vm.yti.terminology.api.util.ElasticRequestUtils.responseContentAsJson;
import static fi.vm.yti.terminology.api.util.ElasticRequestUtils.responseContentAsString;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;

@Service
public class IndexElasticSearchService {

    private static final Logger log = LoggerFactory.getLogger(IndexElasticSearchService.class);

    private final RestClient esRestClient;
    private final RestHighLevelClient esHiLvClient;

    public final String conceptIndexName;
    private final String conceptIndexConfig;
    private final String conceptIndexMapping;
    public final String terminologyIndexName;
    private final String terminologyIndexConfig;
    private final String terminologyIndexMapping;

    private final boolean deleteIndexOnAppRestart;
    private final String[] indexesToDelete;

    private final IndexTermedService termedApiService;
    private final ObjectMapper objectMapper;

    @Autowired
    public IndexElasticSearchService(@Value("${search.host.url}") String searchHostUrl,
                                     @Value("${search.host.port}") int searchHostPort,
                                     @Value("${search.host.scheme}") String searchHostScheme,
                                     @Value("${search.concept.index.name}") String conceptIndexName,
                                     @Value("${search.concept.index.config}") String conceptIndexConfig,
                                     @Value("${search.concept.index.mapping}") String conceptIndexMapping,
                                     @Value("${search.terminology.index.name}") String terminologyIndexName,
                                     @Value("${search.terminology.index.config}") String terminologyIndexConfig,
                                     @Value("${search.terminology.index.mapping}") String terminologyIndexMapping,
                                     @Value("${search.index.deleteIndexOnAppRestart}") boolean deleteIndexOnAppRestart,
                                     @Value("${search.index.deleteIndexOnAppRestart.indexes: #{null}}") @Nullable String indexesToDelete,
                                     final IndexTermedService termedApiService,
                                     final ObjectMapper objectMapper,
                                     @Qualifier("elasticSearchRestHighLevelClient") final RestHighLevelClient esHiLvClient) {

        this.conceptIndexName = conceptIndexName;
        this.conceptIndexConfig = conceptIndexConfig;
        this.conceptIndexMapping = conceptIndexMapping;
        this.terminologyIndexName = terminologyIndexName;
        this.terminologyIndexConfig = terminologyIndexConfig;
        this.terminologyIndexMapping = terminologyIndexMapping;
        this.deleteIndexOnAppRestart = deleteIndexOnAppRestart;

        if (indexesToDelete != null) {
            this.indexesToDelete = Arrays.stream(indexesToDelete.trim().split("\\s*,\\s*"))
                .filter((String index) -> index != null && !index.isEmpty())
                .toArray(String[]::new);
        } else {
            this.indexesToDelete = new String[]{ this.conceptIndexName, this.terminologyIndexName };
        }

        this.termedApiService = termedApiService;
        this.objectMapper = objectMapper;
        this.esRestClient = RestClient.builder(new HttpHost(searchHostUrl, searchHostPort, searchHostScheme)).build();
        this.esHiLvClient = esHiLvClient; // Use that for resource api
    }

    public void initIndexes() {
        if (deleteIndexOnAppRestart && indexesToDelete.length > 0) {
            log.info("Cleaning indexes: " + String.join(", ", indexesToDelete));
            for (String index : indexesToDelete) {
                deleteIndex(index);
            }
        } else {
            log.warn("Possibly reusing old indexes (if those exist)");
        }

        log.info("Initializing indexes");
        initIndex(terminologyIndexName, terminologyIndexConfig, terminologyIndexMapping);
        initIndex(conceptIndexName, conceptIndexConfig, conceptIndexMapping);

        log.info("Attempting full reindex");
        reindex();
    }

    private void initIndex(String indexName,
                           String configFile,
                           String mappingFile) {

        if (!indexExists(indexName)) {
            log.info("Creating index " + indexName + " with config from " + configFile + " and mapping from " + mappingFile);
            if (createIndex(indexName, configFile) && createMapping(indexName, mappingFile)) {
                log.info("Index " + indexName + " created and configured");
            } else {
                log.error("Could not create index " + indexName);
                throw new RuntimeException("Could not create index " + indexName);
            }
        } else {
            log.warn("Skipping initialization of already existing index " + indexName);
        }
    }

    public void reindex() {
        log.info("Starting reindexing task..");
        deleteAllDocumentsFromNamedIndex(conceptIndexName);
        deleteAllDocumentsFromNamedIndex(terminologyIndexName);
        doFullIndexing();
        log.info("Finished reindexing!");
    }

    private void doFullIndexing() {
        indexTerminologies();
        indexConcepts();
    }

    private void indexConcepts() {
        termedApiService.fetchAllAvailableGraphIds().forEach(graphId -> reindexGraph(graphId, false));
    }

    private void indexTerminologies() {
        // Index terminologies
        long start = System.currentTimeMillis();
        // index also all vocabulary-objects
        List<JsonNode> vocabularies = new ArrayList<>();
        // Get terminology IDs
        List<UUID> terminologies = termedApiService.fetchAllAvailableTerminologyIds();
        log.info("Indexing " + terminologies.size() + " terminologies");
        // Fetch corresponding terminology nodes
        terminologies.forEach(o -> {
            JsonNode jn = termedApiService.getTerminologyVocabularyNodeAsJsonNode(o);

            // resolve organization info from references.contributor
            if (jn != null) {
                vocabularies.add(jn);
            }
        });
        if (vocabularies.isEmpty()) {
            return; // Nothing to do
        }
        ObjectMapper mapper = new ObjectMapper();
        List<String> indexLines = new ArrayList<>();
        vocabularies.forEach(o -> {
            try {
                String line = "{\"index\":{\"_index\": \"vocabularies\", \"_type\": \"vocabulary" + "\", \"_id\":"
                    + o.get("id") + "}}\n" + mapper.writeValueAsString(o) + "\n";
                indexLines.add(line);
                if (log.isDebugEnabled()) {
                    log.debug("reindex line:" + line);
                }
            } catch (JsonProcessingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        String index = indexLines.stream().collect(Collectors.joining("\n"));
        log.debug("Handle terminology, index line: " + index);

        String delete = "";
        // Content type changed for elastic search 6.x
        HttpEntity entity = new NStringEntity(index + delete,
            ContentType.create("application/json", StandardCharsets.UTF_8));
        Map<String, String> params = new HashMap<>();
        params.put("pretty", "true");
        params.put("refresh", "wait_for");
        if (log.isDebugEnabled()) {
            log.debug("Request:" + entity);
        }
        Response response = alsoUnsuccessful(() -> esRestClient.performRequest("POST", "/_bulk", params, entity));
        long end = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("Response:" + response + "\n Response status line" + response.getStatusLine());
        }
        if (isSuccess(response)) {
            log.info("Successfully indexed " + vocabularies.size() + " terminologies in " + (end - start) + "ms");
        } else {
            log.warn("Unable to add or update document to elasticsearch index: " + vocabularies.size() + " took " + (end - start) + "ms");
            log.info(responseContentAsString(response));
        }
    }

    private boolean reindexGivenVocabulary(UUID vocId) {
        boolean rv = true;
        long start = System.currentTimeMillis();
        // Get terminology
        JsonNode jn = termedApiService.getTerminologyVocabularyNodeAsJsonNode(vocId);
        if (jn == null) {
            log.warn("Missing vocabulary during elasticsearch reindexing  :" + vocId.toString());
            return false;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            String index = "{\"index\":{\"_index\": \"vocabularies\", \"_type\": \"" + "vocabulary" + "\", \"_id\":"
                + jn.get("id") + "}}\n" + mapper.writeValueAsString(jn) + "\n";
            String delete = "";
            // CHANGED CONTENT TYPE FOR ELASTIC 6.X
            HttpEntity entity = new NStringEntity(index + delete,
                ContentType.create("application/json", StandardCharsets.UTF_8));
            Map<String, String> params = new HashMap<>();

            params.put("pretty", "true");
            params.put("refresh", "wait_for");

            Response response = alsoUnsuccessful(() -> esRestClient.performRequest("POST", "/_bulk", params, entity));

            long end = System.currentTimeMillis();
            if (isSuccess(response)) {
                log.info("Successfully added/updated documents to elasticsearch index: " + vocId.toString() + " in " + (end - start) + "ms");
            } else {
                log.warn("Unable to add or update document to elasticsearch index: " + vocId.toString() + " in " + (end - start) + "ms");
                log.info(responseContentAsString(response));
                rv = false;
            }
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return rv;
    }

    void updateIndexAfterUpdate(@NotNull AffectedNodes nodes) {

        int fullReindexNodeCountThreshold = 20;
        if (log.isDebugEnabled()) {
            log.debug("updateIndexAfterUpdate() " + nodes.toString() + " hasVocabulary:" + nodes.hasVocabulary());
        }
        UUID voc = nodes.getGraphId();
        if (log.isDebugEnabled()) {
            log.debug("Vocabulary=" + voc + " vocabulary count=" + nodes.getVocabularyIds().size());
        }
        // if treshold is , make full reindex
        if (nodes.hasVocabulary() && nodes.getVocabularyIds().size() > fullReindexNodeCountThreshold) {
            indexTerminologies();
        } else {
            if (log.isDebugEnabled()) {
                log.debug("partial update!");
            }
            if (nodes.getVocabularyIds() != null && nodes.getVocabularyIds().size() > 0) {
                // Update vocabulary index
                // reindexVocabularies();
                nodes.getVocabularyIds().forEach(id -> {
                    log.info("reindexVocabulary:" + id);
                    reindexGivenVocabulary(voc);
                });
            }
        }
        if (nodes.hasVocabulary() || nodes.getConceptsIds().size() > fullReindexNodeCountThreshold) {
            reindexGraph(nodes.getGraphId(), true);
        } else {
            List<Concept> updatedConcepts = termedApiService.getConcepts(nodes.getGraphId(), nodes.getConceptsIds());
            List<Concept> conceptsBeforeUpdate = getConceptsFromIndex(nodes.getGraphId(), nodes.getConceptsIds());
            List<Concept> possiblyUpdatedConcepts = termedApiService.getConcepts(nodes.getGraphId(),
                broaderAndNarrowerIds(asList(updatedConcepts, conceptsBeforeUpdate)));
            List<Concept> updateToIndex = Stream.concat(updatedConcepts.stream(), possiblyUpdatedConcepts.stream())
                .collect(toList());

            bulkUpdateAndDeleteDocumentsToIndex(nodes.getGraphId(), updateToIndex, emptyList(), true);
        }
    }

    void updateIndexAfterDelete(@NotNull AffectedNodes nodes) {

        int fullReindexNodeCountThreshold = 20;

        if (nodes.hasVocabulary()) {
            // First delete concepts and then
            deleteDocumentsFromIndexByGraphId(nodes.getGraphId());
            // In case of treshold overcome, make full reindex
            if (nodes.hasVocabulary()) {
                nodes.getVocabularyIds().forEach(id -> {
                    // Delete actual vocabulary-object
                    deleteDocumentsFromNamedIndexByGraphId(id, "vocabularies");
                });
            }
        } else {
            List<Concept> conceptsBeforeDelete = getConceptsFromIndex(nodes.getGraphId(), nodes.getConceptsIds());
            List<Concept> possiblyUpdatedConcepts = termedApiService.getConcepts(nodes.getGraphId(),
                broaderAndNarrowerIds(singletonList(conceptsBeforeDelete)));

            bulkUpdateAndDeleteDocumentsToIndex(nodes.getGraphId(), possiblyUpdatedConcepts, nodes.getConceptsIds(),
                true);
            nodes.getConceptsIds().forEach(id -> {
                deleteDocumentsFromNamedIndexByGraphId(id, "concepts");
            });
        }
    }

    private static @NotNull Set<UUID> broaderAndNarrowerIds(@NotNull List<List<Concept>> concepts) {

        return concepts.stream().flatMap(Collection::stream)
            .flatMap(concept -> Stream.concat(concept.getBroaderIds().stream(), concept.getNarrowerIds().stream()))
            .collect(Collectors.toSet());
    }

    private void reindexGraph(@NotNull UUID graphId,
                              boolean waitForRefresh) {
        List<Concept> concepts = termedApiService.getAllConceptsForGraph(graphId);
        // update if concepts exist
        long start = System.currentTimeMillis();
        if (concepts != null && !concepts.isEmpty()) {
            bulkUpdateAndDeleteDocumentsToIndex(graphId, concepts, emptyList(), waitForRefresh);
            long end = System.currentTimeMillis();

            log.info("Graph:" + graphId + " Indexed " + concepts.size() + " concepts in " + (end - start) + "ms");
        }
    }

    private void deleteIndex(final String index) {
        Response response = alsoUnsuccessful(() -> esRestClient.performRequest(new Request("DELETE", "/" + index)));
        if (isSuccess(response)) {
            log.info("Elasticsearch index deleted: " + index);
        } else {
            log.info("Elasticsearch index:" + index + " not deleted. Maybe because it did not exist?");
        }
    }

    private boolean indexExists(String index) {

        log.debug("Checking if elasticsearch index exists: " + index);
        Response response = alsoUnsuccessful(() -> esRestClient.performRequest(new Request("HEAD", "/" + index)));
        if (response.getStatusLine().getStatusCode() == 404) {
            log.debug("Elasticsearch index does not exist: " + index);
            return false;
        } else {
            log.debug("Elasticsearch index exists: " + index);
            return true;
        }
    }

    private boolean createIndex(String indexName,
                                String configFile) {

        log.debug("Creating index " + indexName + " with config file " + configFile);
        HttpEntity entity = createHttpEntity(configFile);
        Response response = alsoUnsuccessful(() -> {
            Request request = new Request("PUT", "/" + indexName);
            request.addParameter("pretty", "true");
            request.setEntity(entity);
            return esRestClient.performRequest(request);
        });

        if (isSuccess(response)) {
            log.debug("Created index " + indexName);
            return true;
        } else {
            log.error("Failed creating index " + indexName + ": " + response.getStatusLine());
            return false;
        }
    }

    private boolean createMapping(String indexName,
                                  String mappingFile) {

        log.debug("Creating mapping for index " + indexName + " from file " + mappingFile);
        HttpEntity entity = createHttpEntity(mappingFile);
        Response response = alsoUnsuccessful(() -> {
            Request request = new Request("PUT", "/" + indexName + "/_mappings/_doc");
            request.addParameter("pretty", "true");
            request.setEntity(entity);
            return esRestClient.performRequest(request);
        });

        if (isSuccess(response)) {
            log.debug("Configured mapping for index " + indexName);
            return true;
        } else {
            log.error("Failed configuring mapping for index " + indexName + ": " + response.getStatusLine());
            return false;
        }
    }

    private @NotNull String createBulkIndexMetaAndSource(@NotNull Concept concept) {
        return createBulkIndexMetaAndSource(concept, conceptIndexName);
    }

    private @NotNull String createBulkIndexMetaAndSource(@NotNull Concept concept,
                                                         String index) {
        return "{\"index\":{\"_index\": \"" + index + "\", \"_id\":\"" + concept.getDocumentId()
            + "\"}}\n" + concept.toElasticSearchDocument(objectMapper) + "\n";
    }

    private @NotNull String createBulkDeleteMeta(@NotNull UUID graphId,
                                                 @NotNull UUID conceptId) {
        return "{\"delete\":{\"_index\": \"" + conceptIndexName + "\", \"_id\":\"" + Concept.formDocumentId(graphId, conceptId) + "\"}}\n";
    }

    private void bulkUpdateAndDeleteDocumentsToIndex(@NotNull UUID graphId,
                                                     @NotNull List<Concept> updateConcepts,
                                                     @NotNull List<UUID> deleteConceptsIds,
                                                     boolean waitForRefresh) {

        if (updateConcepts.size() == 0 && deleteConceptsIds.size() == 0) {
            return; // nothing to do
        }

        // https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html

        String index = updateConcepts.stream().map(this::createBulkIndexMetaAndSource)
            .collect(Collectors.joining("\n"));
        String delete = deleteConceptsIds.stream().map(id -> createBulkDeleteMeta(graphId, id))
            .collect(Collectors.joining("\n"));
        // Changed content type for elastic search 6.x
        HttpEntity entity = new NStringEntity(index + delete,
            // ContentType.create("application/x-ndjson"));
            ContentType.create("application/json", StandardCharsets.UTF_8));
        // ContentType.create("application/x-ndjson", StandardCharsets.UTF_8));
        Map<String, String> params = new HashMap<>();

        params.put("pretty", "true");

        if (waitForRefresh) {
            params.put("refresh", "wait_for");
        }

        Response response = alsoUnsuccessful(() -> esRestClient.performRequest("POST", "/_bulk", params, entity));

        if (isSuccess(response)) {
            if (updateConcepts.size() > 0 && log.isDebugEnabled()) {
                log.debug("Successfully added/updated concepts documents to elasticsearch index: "
                    + updateConcepts.size());
            }
            if (deleteConceptsIds.size() > 0 && log.isDebugEnabled()) {
                log.debug("Successfully deleted concepts  documents from elasticsearch index: "
                    + deleteConceptsIds.size());
            }
        } else {
            // Failed
            log.warn("Unable to add or update concepts document to elasticsearch index: " + updateConcepts.size());
            log.warn("Unable to delete concepts document from elasticsearch index: " + deleteConceptsIds.size());
        }
    }

    private void deleteDocumentsFromNamedIndexByGraphId(@NotNull UUID graphId,
                                                        @NotNull String index) {

        HttpEntity body = new NStringEntity("{\"query\": { \"match\": {\"id\": \"" + graphId + "\"}}}",
            ContentType.APPLICATION_JSON);
        Response response = alsoUnsuccessful(
            () -> esRestClient.performRequest("POST", "/" + index + "/_delete_by_query", emptyMap(), body));

        if (isSuccess(response)) {
            log.info(responseContentAsString(response));
            log.info("Successfully deleted documents from elasticsearch index from graph: " + graphId);
        } else {
            log.warn("Unable to delete documents from elasticsearch index");
        }
    }

    private void deleteDocumentsFromIndexByGraphId(@NotNull UUID graphId) {

        HttpEntity body = new NStringEntity("{\"query\": { \"match\": {\"vocabulary.id\": \"" + graphId + "\"}}}",
            ContentType.APPLICATION_JSON);
        Response response = alsoUnsuccessful(
            () -> esRestClient.performRequest("POST", "/" + conceptIndexName + "/_delete_by_query", emptyMap(), body));

        if (isSuccess(response)) {
            log.info(responseContentAsString(response));
            log.info("Successfully deleted vocabulary documents from elasticsearch index from graph: " + graphId);
        } else {
            log.warn("Unable to delete vocabulary documents from elasticsearch index");
        }
    }

    private void deleteAllDocumentsFromNamedIndex(String indexName) {

        HttpEntity body = new NStringEntity("{\"query\": { \"match_all\": {}}}", ContentType.APPLICATION_JSON);
        Response response = alsoUnsuccessful(() -> {
            Request request = new Request("POST", "/" + indexName + "/_delete_by_query");
            request.setEntity(body);
            return esRestClient.performRequest(request);
        });

        if (isSuccess(response)) {
            log.debug("Successfully deleted all documents from index " + indexName);
        } else {
            log.error("Unable to delete documents from index " + indexName + ": " + response.getStatusLine());
        }
    }

    public @Nullable JsonNode freeSearchFromIndex(String query) {
        Parameters params = new Parameters();
        params.add("source", query.toString());
        params.add("source_content_type", "application/json");
        String endpoint = "/" + indexName + "/_search";

        HttpEntity body = new NStringEntity(query, ContentType.APPLICATION_JSON);
        try {
            Response response = esRestClient.performRequest("GET", endpoint, emptyMap(), body);
            String resp = responseContentAsString(response);
            if (isSuccess(response)) {
                if (log.isDebugEnabled()) {
                    log.debug(resp);
                    log.debug("Index query successfull");
                }
            } else {
                log.warn("Unable to query documents from elasticsearch index");
            }
            // String -> JSON
            JsonNode obj = objectMapper.readTree(resp);
            return obj;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public @Nullable JsonNode freeSearchFromIndex(SearchRequest sr) {
        final SearchResponse response;
        JsonNode obj = null;
        try {
            response = esHiLvClient.search(sr, RequestOptions.DEFAULT);
            if (log.isDebugEnabled()) {
                log.debug("Search result count=" + response.getHits().getTotalHits());
            }
            // setResultCounts(meta, response);
            // String -> JSON
            obj = objectMapper.readTree(response.toString());
            /*
             * response.getHits().forEach(hit -> { try { String resp =
             * hit.getSourceAsString(); // String -> JSON obj = objectMapper.readTree(resp);
             * } catch (final IOException e) {
             * log.error("getContainers reading value from JSON string failed: " +
             * hit.getSourceAsString(), e); throw new RuntimeException(e); } });
             */
        } catch (final IOException e) {
            log.error("SearchRequest failed!", e);
            throw new RuntimeException(e);
        }
        return obj;
    }

    public @Nullable JsonNode freeSearchFromIndex(String query,
                                                  String indexName) {
        Parameters params = new Parameters();
        params.add("source", query.toString());
        params.add("source_content_type", "application/json");
        String endpoint = "/" + indexName + "/_search";

        HttpEntity body = new NStringEntity(query, ContentType.APPLICATION_JSON);
        try {
            Response response = esRestClient.performRequest("GET", endpoint, emptyMap(), body);
            String resp = responseContentAsString(response);
            if (isSuccess(response)) {
                if (log.isDebugEnabled()) {
                    log.debug(resp);
                    log.debug("Index query successfull");
                }
            } else {
                log.warn("Unable to query documents from elasticsearch index");
            }
            // String -> JSON
            JsonNode obj = objectMapper.readTree(resp);
            return obj;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private @Nullable Concept getConceptFromIndex(@NotNull UUID graphId,
                                                  @NotNull UUID conceptId) {

        String documentId = Concept.formDocumentId(graphId, conceptId);
        Response response = alsoUnsuccessful(() -> esRestClient.performRequest("GET",
            "/" + indexName + "/concept/" + urlEncode(documentId) + "/_source"));

        if (isSuccess(response)) {
            return Concept.createFromIndex(objectMapper, responseContentAsJson(objectMapper, response));
        } else {
            return null;
        }
    }

    private @NotNull List<Concept> getConceptsFromIndex(@NotNull UUID graphId,
                                                        @NotNull Collection<UUID> conceptIds) {

        // TODO inefficient implementation
        return conceptIds.stream().map(conceptId -> this.getConceptFromIndex(graphId, conceptId))
            .filter(Objects::nonNull).collect(toList());
    }

    private @NotNull Response alsoUnsuccessful(@NotNull ResponseSupplier supplier) {
        try {
            return supplier.get();
        } catch (ResponseException e) {
            return e.getResponse();
        } catch (IOException e) {
            throw new ElasticEndpointException(e);
        }
    }

    private static @NotNull String urlEncode(@NotNull String str) {
        try {
            return URLEncoder.encode(str, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private interface ResponseSupplier {

        @NotNull
        Response get() throws IOException;
    }

    private boolean isSuccess(@NotNull Response response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode >= 200 && statusCode < 400;
    }

    private @NotNull HttpEntity createHttpEntity(@NotNull String classPathResourceJsonFile) {

        ClassPathResource resource = new ClassPathResource(classPathResourceJsonFile);

        try (InputStream is = resource.getInputStream()) {
            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            JsonNode jsonNode = objectMapper.readTree(reader);
            return new NStringEntity(jsonNode.toString(), ContentType.APPLICATION_JSON);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    private void destroy() {
        try {
            log.info("Closing rest client");
            this.esRestClient.close();
        } catch (IOException e) {
            log.warn("Unable to close rest client");
            throw new RuntimeException(e);
        }
    }
}
