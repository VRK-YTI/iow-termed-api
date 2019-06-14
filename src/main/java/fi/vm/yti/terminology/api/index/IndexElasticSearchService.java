package fi.vm.yti.terminology.api.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.vm.yti.terminology.api.exception.ElasticEndpointException;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;

import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import static org.springframework.http.HttpMethod.GET;

import javax.annotation.PreDestroy;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.util.Parameters;
import fi.vm.yti.terminology.api.model.termed.*;

import static fi.vm.yti.terminology.api.util.ElasticRequestUtils.responseContentAsJson;
import static fi.vm.yti.terminology.api.util.ElasticRequestUtils.responseContentAsString;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;

@Service
public class IndexElasticSearchService {

    private static final Logger log = LoggerFactory.getLogger(IndexElasticSearchService.class);

    private final RestClient esRestClient;
    private final String createIndexFilename;
    private final String createMappingsFilename;
    private final String indexName;
    private List<String> indexNames = new ArrayList<>();

    private final String indexMappingType;
    private final boolean deleteIndexOnAppRestart;

    private final IndexTermedService termedApiService;
    private final FrontendTermedService terminologyAPI;
    private final ObjectMapper objectMapper;

    private static final String TERMINOLOGY_ROOT = "TerminologyRoot";
    private UUID TERMINOLOGY_ROOT_ID = null;

    @Autowired
    public IndexElasticSearchService(@Value("${search.host.url}") String searchHostUrl,
            @Value("${search.host.port}") int searchHostPort, @Value("${search.host.scheme}") String searchHostScheme,
            @Value("${search.index.file}") String createIndexFilename,
            @Value("${search.index.mapping.file}") String createMappingsFilename,
            @Value("${search.index.name}") String indexName,
            @Value("${search.index.mapping.type}") String indexMappingType,
            @Value("${search.index.deleteIndexOnAppRestart}") boolean deleteIndexOnAppRestart,
            IndexTermedService termedApiService, ObjectMapper objectMapper, FrontendTermedService terminologyAPI) {
        this.createIndexFilename = createIndexFilename;
        this.createMappingsFilename = createMappingsFilename;
        this.indexName = indexName;
        this.indexMappingType = indexMappingType;
        this.deleteIndexOnAppRestart = deleteIndexOnAppRestart;
        this.termedApiService = termedApiService;
        this.objectMapper = objectMapper;
        this.terminologyAPI = terminologyAPI; // get_root_graph is implemented here
        this.esRestClient = RestClient.builder(new HttpHost(searchHostUrl, searchHostPort, searchHostScheme)).build();
    }

    public void initIndex() {

        String[] indexNames = indexName.split(",");
        String[] indexMaps = createMappingsFilename.split(",");
        String[] indexMappingTypes = indexMappingType.split(",");
        if (indexNames.length != indexMaps.length) {
            log.error("InitIndex, configuration error. Mismatching index-names / mappings");
            return;
        }
        for (int x = 0; x < indexNames.length; x++) {
            log.info("Init index =" + indexNames[x] + " - " + indexMaps[x] + " - " + indexMappingTypes[x]);
            initIndex(indexNames[x], indexMaps[x], indexMappingTypes[x]);
        }
    }

    public void initIndex(String index, String mapping, String mappingType) {

        if (deleteIndexOnAppRestart) {
            deleteIndex(index);
        }

        if (!indexExists(index) && createIndex(index) && createMapping(index, mapping, mappingType)) {
            doFullIndexing();
        }
    }

    public void reindex() {
        log.info("Starting reindexing task..");
        // Clean vocabularies
        deleteAllDocumentsFromNamedIndex("vocabularies");
        deleteAllDocumentsFromIndex();
        doFullIndexing();
        log.info("Finished reindexing!");
    }

    private void doFullIndexing() {
        reindexVocabularies();
        // Index concepts from all vocabularies
       terminologyAPI.getVocabularies().forEach(vocabularyId -> reindexVocabulary(vocabularyId, false));

//        termedApiService.fetchAllAvailableGraphIds().forEach(graphId -> reindexGraph(graphId, false));
    }

    private void reindexVocabularies() {
        // Index vocabularies
        long start = System.currentTimeMillis();
        // index also all vocabulary-objects
        // Get all terminolical vocabularies
        List<UUID> vocabularyIDList = terminologyAPI.getVocabularies();

        List<JsonNode> vocabularies = new ArrayList<>();
        vocabularyIDList.forEach(o -> {
            JsonNode jn = termedApiService.getTerminologyVocabularyNode(o);
            if (jn != null) {
                vocabularies.add(jn);
            }
        });

        long end = System.currentTimeMillis();
        log.info("Vocabulary Search took " + (end - start)+"s");
        if (vocabularyIDList.isEmpty()) {
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
        System.out.println("reindexVocabularies done resp="+response.getStatusLine());
        if (log.isDebugEnabled()) {
            log.debug("Response:" + response + "\n Response status line" + response.getStatusLine());
        }
        if (isSuccess(response)) {
            log.info("Successfully added/updated documents to elasticsearch index: " + vocabularies.size());
        } else {
            log.warn("Unable to add or update document to elasticsearch index: " + vocabularies.size());
            log.info(responseContentAsString(response));
        }
        log.info("Indexed " + vocabularies.size() + " vocabularies");
    }

    private boolean reindexGivenVocabulary(UUID vocId) {
        boolean rv = true;
        // Get vocabulary
        JsonNode jn = termedApiService.getTerminologyVocabularyNode(vocId);
        ObjectMapper mapper = new ObjectMapper();
        try {
            String index = "{\"index\":{\"_index\": \"vocabularies\", \"_type\": \"" + "vocabulary" + "\", \"_id\":"
                    + jn.get("id") + "}}\n" + mapper.writeValueAsString(jn) + "\n";
            String delete = "";
            // CHANGED CONTENT TYPE FOR ELASTIC 6.X
            HttpEntity entity = new NStringEntity(index + delete,
                    // ContentType.create("application/x-ndjson"));
                    ContentType.create("application/json", StandardCharsets.UTF_8));
            // ContentType.create("application/x-ndjson", StandardCharsets.UTF_8));
            Map<String, String> params = new HashMap<>();

            params.put("pretty", "true");
            params.put("refresh", "wait_for");

            Response response = alsoUnsuccessful(() -> esRestClient.performRequest("POST", "/_bulk", params, entity));

            if (isSuccess(response)) {
                log.info("Successfully added/updated documents to elasticsearch index: " + vocId.toString());
            } else {
                log.warn("Unable to add or update document to elasticsearch index: " + vocId.toString());
                log.info(responseContentAsString(response));
                rv = false;
            }
            log.info("Indexed  vocabulary " + vocId.toString());
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return rv;
    }

    void updateIndexAfterUpdate(@NotNull AffectedNodes nodes) {

        int fullReindexNodeCountThreshold = 20;
        if (log.isDebugEnabled()) {
            log.debug("updateIndexAfterUpdate()" + nodes.toString() + " hasVocabulary:" + nodes.hasVocabulary());
        }
        UUID voc = nodes.getGraphId();
        if (log.isDebugEnabled()) {
            log.debug("Vocabulary=" + voc + " vocabulary count=" + nodes.getVocabularyIds().size());
        }
        // if treshold is , make full reindex
        if (nodes.hasVocabulary() && nodes.getVocabularyIds().size() > fullReindexNodeCountThreshold) {
            reindexVocabularies();
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
            reindexVocabulary(nodes.getGraphId(), true);
//            reindexGraph(nodes.getGraphId(), true);
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
/*
    private void reindexGraph(@NotNull UUID graphId, boolean waitForRefresh) {
        log.info("Trying to index concepts of graph " + graphId);

        List<Concept> concepts = termedApiService.getAllConceptsForGraph(graphId);
        bulkUpdateAndDeleteDocumentsToIndex(graphId, concepts, emptyList(), waitForRefresh);
        log.info("Indexed " + concepts.size() + " concepts");
    }
*/
    private void reindexVocabulary(@NotNull UUID vocabularyId, boolean waitForRefresh) {

        log.info("Trying to index concepts of vocabulary " + vocabularyId);

        List<Concept> concepts = termedApiService.getAllConceptsForGraph(vocabularyId);
        bulkUpdateAndDeleteDocumentsToIndex(vocabularyId, concepts, emptyList(), waitForRefresh);
        log.info("Indexed " + concepts.size() + " concepts");
    }

    private void deleteIndex() {
        log.info("Deleting elasticsearch index: " + indexName);
        Stream<String> ind = Stream.of(indexName.split(",")).map(o -> o.trim());
        ind.forEach(s -> {
            deleteIndex(s);
        });
    }

    private void deleteIndex(String index) {
        log.info("Deleting elasticsearch index: " + index);

        Response response = alsoUnsuccessful(() -> esRestClient.performRequest("DELETE", "/" + index));

        if (isSuccess(response)) {
            log.info("Elasticsearch index deleted: " + index);
        } else {
            log.info("Elasticsearch index:" + index + " not deleted. Maybe because it did not exist?");
        }
    }

    private boolean indexExists(String index) {
        log.info("Checking if elasticsearch index exists: " + index);
        Response response = alsoUnsuccessful(() -> esRestClient.performRequest("HEAD", "/" + index));
        if (response.getStatusLine().getStatusCode() == 404) {
            log.info("Elasticsearch index does not exist: " + index);
            return false;
        } else {
            return true;
        }
    }

    private boolean createIndex(String index) {

        HttpEntity entity = createHttpEntity(createIndexFilename);
        log.info("Trying to create elasticsearch index: " + index);
        Response response = alsoUnsuccessful(
                () -> esRestClient.performRequest("PUT", "/" + index, singletonMap("pretty", "true"), entity));

        if (isSuccess(response)) {
            log.info("elasticsearch index successfully created: " + index);
            return true;
        } else {
            log.warn("Unable to create elasticsearch index: " + index);
            return false;
        }
    }

    private boolean createMapping(String index, String mapping, String mappingType) {

        HttpEntity entity = createHttpEntity(mapping);
        log.info("Trying to create elasticsearch index mapping type: " + mappingType);
        // Mapping name is same than index name
        // Response response = alsoUnsuccessful(() -> esRestClient.performRequest("PUT",
        // "/" + index + "/_mapping/" + index, singletonMap("pretty", "true"), entity));

        Response response = alsoUnsuccessful(() -> esRestClient.performRequest("PUT",
                "/" + index + "/_mapping/" + mappingType, singletonMap("pretty", "true"), entity));

        if (isSuccess(response)) {
            log.info("elasticsearch index mapping type successfully created: " + mappingType);
            return true;
        } else {
            log.warn("Unable to create elasticsearch index mapping type: " + mappingType);
            return false;
        }
    }

    private @NotNull String createBulkIndexMetaAndSource(@NotNull Concept concept) {
        return createBulkIndexMetaAndSource(concept, "concepts");
    }

    // private @NotNull String createBulkIndexMetaAndSource(@NotNull JsonNode
    // vocabulary) {
    // return createBulkIndexMetaAndSource(concept, "concept");
    // }

    private @NotNull String createBulkIndexMetaAndSource(@NotNull Concept concept, String index) {
        return "{\"index\":{\"_index\": \"" + index + "\", \"_type\": \"concept\", \"_id\":\"" + concept.getDocumentId()
                + "\"}}\n" + concept.toElasticSearchDocument(objectMapper) + "\n";
    }

    private @NotNull String createBulkDeleteMeta(@NotNull UUID graphId, @NotNull UUID conceptId) {
        return "{\"delete\":{\"_index\": \"" + indexName + "\", \"_type\": \"concept\", \"_id\":\""
                + Concept.formDocumentId(graphId, conceptId) + "\"}}\n";
    }

    private void bulkUpdateAndDeleteDocumentsToIndex(@NotNull UUID graphId, @NotNull List<Concept> updateConcepts,
            @NotNull List<UUID> deleteConceptsIds, boolean waitForRefresh) {

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
            log.info("Successfully added/updated documents to elasticsearch index: " + updateConcepts.size());
            log.info("Successfully deleted documents from elasticsearch index: " + deleteConceptsIds.size());
        } else {
            log.warn("Unable to add or update document to elasticsearch index: " + updateConcepts.size());
            log.warn("Unable to delete document from elasticsearch index: " + deleteConceptsIds.size());
        }
    }

    private void deleteDocumentsFromNamedIndexByGraphId(@NotNull UUID graphId, @NotNull String index) {

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
                () -> esRestClient.performRequest("POST", "/" + indexName + "/_delete_by_query", emptyMap(), body));

        if (isSuccess(response)) {
            log.info(responseContentAsString(response));
            log.info("Successfully deleted vocabulary documents from elasticsearch index from graph: " + graphId);
        } else {
            log.warn("Unable to delete vocabulary documents from elasticsearch index");
        }
    }

    private void deleteAllDocumentsFromIndex() {

        HttpEntity body = new NStringEntity("{\"query\": { \"match_all\": {}}}", ContentType.APPLICATION_JSON);
        // Response response = alsoUnsuccessful(() ->
        // esRestClient.performRequest("POST",
        // "/" + indexName + "/" + indexMappingType + "/_delete_by_query", emptyMap(),
        // body));
        Response response = alsoUnsuccessful(
                () -> esRestClient.performRequest("POST", "/" + indexName + "/_delete_by_query", emptyMap(), body));

        if (isSuccess(response)) {
            log.info(responseContentAsString(response));
            log.info("Successfully deleted all documents from elasticsearch index");
        } else {
            log.warn("Unable to delete documents from elasticsearch index");
        }
    }

    private void deleteAllDocumentsFromNamedIndex(String index) {

        HttpEntity body = new NStringEntity("{\"query\": { \"match_all\": {}}}", ContentType.APPLICATION_JSON);
        Response response = alsoUnsuccessful(
                () -> esRestClient.performRequest("POST", "/" + index + "/_delete_by_query", emptyMap(), body));

        if (isSuccess(response)) {
            log.info(responseContentAsString(response));
            log.info("Successfully deleted all documents from elasticsearch index:" + index);
        } else {
            log.warn("Unable to delete documents from elasticsearch index:" + index);
        }
    }

    public @Nullable JsonNode freeSearchFromIndex(String query) {
        Parameters params = new Parameters();
        params.add("source", query.toString());
        params.add("source_content_type", "application/json");
        String endpoint = "/" + indexName + "/_search";

        HttpEntity body = new NStringEntity(query, ContentType.APPLICATION_JSON);
        try {
            Response response = esRestClient.performRequest("GET", endpoint, Collections.emptyMap(), body);
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

    public @Nullable JsonNode freeSearchFromIndex(String query, String indexName) {
        Parameters params = new Parameters();
        params.add("source", query.toString());
        params.add("source_content_type", "application/json");
        String endpoint = "/" + indexName + "/_search";

        HttpEntity body = new NStringEntity(query, ContentType.APPLICATION_JSON);
        try {
            Response response = esRestClient.performRequest("GET", endpoint, Collections.emptyMap(), body);
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

    private @Nullable Concept getConceptFromIndex(@NotNull UUID graphId, @NotNull UUID conceptId) {

        String documentId = Concept.formDocumentId(graphId, conceptId);
        Response response = alsoUnsuccessful(() -> esRestClient.performRequest("GET",
                "/" + indexName + "/concept/" + urlEncode(documentId) + "/_source"));

        if (isSuccess(response)) {
            return Concept.createFromIndex(objectMapper, responseContentAsJson(objectMapper, response));
        } else {
            return null;
        }
    }

    private @NotNull List<Concept> getConceptsFromIndex(@NotNull UUID graphId, @NotNull Collection<UUID> conceptIds) {

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
