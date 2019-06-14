package fi.vm.yti.terminology.api.publicapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.index.IndexTermedService;
import static fi.vm.yti.terminology.api.util.ElasticRequestUtils.responseContentAsJson;

@Service
public class PublicApiElasticSearchService {

    private final String indexName;
    private final String indexMappingType;

    private final RestClient esRestClient;

    private final IndexTermedService indexTermedService;

    private static Locale[] availableLocales;
    private static Set<Locale> uniqueNonNullLocales;

    @Autowired
    public PublicApiElasticSearchService(@Value("${search.host.url}") String searchHostUrl,
                                         @Value("${search.host.port}") int searchHostPort,
                                         @Value("${search.host.scheme}") String searchHostScheme,
                                         @Value("${search.index.name}") String indexName,
                                         @Value("${search.index.mapping.type}") String indexMappingType,
                                         IndexTermedService indexTermedService) {
        this.indexName = indexName;
        this.indexMappingType = indexMappingType;
        this.esRestClient = RestClient.builder(new HttpHost(searchHostUrl, searchHostPort, searchHostScheme)).build();
        this.indexTermedService = indexTermedService;
    }

    static {
        availableLocales = Locale.getAvailableLocales();
        uniqueNonNullLocales = new HashSet<>();

        for (Locale locale : availableLocales) {
            if (!locale.getLanguage().isEmpty() && !uniqueNonNullLocales.contains(locale)) {
                uniqueNonNullLocales.add(locale);
            }
        }
    }

    List<PublicApiConcept> searchConcept(String searchTerm,
                                         String vocabularyId,
                                         String status) {

        String endpoint = "/" + indexName + "/" + indexMappingType + "/_search";

        StringBuilder queryBuilder = new StringBuilder();
        if (vocabularyId == null || vocabularyId.isEmpty() || vocabularyId.equals("0")) {
            queryBuilder.append("{\"query\":{\"bool\":{\"must\":[{\"multi_match\":{\"query\":\"");
            JsonStringEncoder.getInstance().quoteAsString(searchTerm, queryBuilder);
            queryBuilder.append("\",\"fields\":[\"label.fi^10\",\"label.*\"],\"type\":\"best_fields\",\"minimum_should_match\":\"90%\"}}],\"must_not\":[]}},\"highlight\":{\"pre_tags\":[\"<b>\"],\"post_tags\":[\"</b>\"],\"fields\":{\"label.*\":{}}},\"from\":0,\"size\":100,\"sort\":[\"_score\"]}");
        } else {
            queryBuilder.append("{\"query\":{\"bool\":{\"must\":[{\"match\":{\"vocabulary.id\":\"");
            JsonStringEncoder.getInstance().quoteAsString(vocabularyId, queryBuilder);
            queryBuilder.append("\"}},{\"multi_match\":{\"query\":\"");
            JsonStringEncoder.getInstance().quoteAsString(searchTerm, queryBuilder);
            queryBuilder.append("\",\"fields\":[\"label.fi^10\",\"label.*\"],\"type\":\"best_fields\",\"minimum_should_match\":\"90%\"}}],\"must_not\":[]}},\"highlight\":{\"pre_tags\":[\"<b>\"],\"post_tags\":[\"</b>\"],\"fields\":{\"label.*\":{}}},\"from\":0,\"size\":100,\"sort\":[\"_score\"]}");
        }
        NStringEntity body = new NStringEntity(queryBuilder.toString(), ContentType.APPLICATION_JSON);

        try {
            Response response = esRestClient.performRequest("GET", endpoint, Collections.emptyMap(), body);
            ObjectMapper om = new ObjectMapper();
            return getAsPublicApiConcepts(status, responseContentAsJson(om, response));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<PublicApiConcept> getAsPublicApiConcepts(String status,
                                                          JsonNode concepts) {
        List<PublicApiConcept> result = new ArrayList<>();
        JsonNode hitsNode = concepts.path("hits").path("hits");
        for (JsonNode jsonNode : hitsNode) {
            boolean addItem = true;
            PublicApiConcept concept = new PublicApiConcept();
            concept.setId(UUID.fromString(jsonNode.path("_source").path("id").asText()));
            concept.setVocabularyId(UUID.fromString(jsonNode.path("_source").path("vocabulary").path("id").asText()));
            concept.setVocabularyUri(jsonNode.path("_source").path("vocabulary").path("uri").asText());
            concept.setPrefLabel(extractLocalizableFromGivenField(jsonNode.path("_source"), "label"));
            concept.setDefinition(extractLocalizableFromGivenField(jsonNode.path("_source"), "definition"));
            concept.setVocabularyPrefLabel(extractLocalizableFromGivenField(jsonNode.path("_source").path("vocabulary"), "label"));
            concept.setUri(jsonNode.path("_source").path("uri").asText());
            concept.setStatus(jsonNode.path("_source").path("status").asText());

            // Filtering out items which status  does not match to given one.
            if (status != null && !status.isEmpty()) {
                if (!concept.getStatus().equalsIgnoreCase(status)) {
                    addItem = false;
                }
            }
            if (addItem) {
                result.add(concept);
            }
        }
        return result;
    }

    public HashMap<String, String> extractLocalizableFromGivenField(JsonNode node,
                                                                    String fieldName) {
        HashMap<String, String> result = new HashMap<>();

        uniqueNonNullLocales.forEach(locale -> {
            JsonNode theNode = node.get(fieldName).get(locale.getLanguage());
            if (theNode != null) {
                result.put(locale.getLanguage(), Jsoup.clean(node.get(fieldName).get(locale.getLanguage()).get(0).textValue(), Whitelist.none()));
            }
        });

        return result;
    }
}
