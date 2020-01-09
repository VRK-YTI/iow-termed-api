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
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.index.IndexTermedService;
import fi.vm.yti.terminology.api.util.ElasticRequestUtils;
import static fi.vm.yti.terminology.api.util.ElasticRequestUtils.responseContentAsJson;

@Service
public class PublicApiElasticSearchService {

    private static final Logger logger = LoggerFactory.getLogger(PublicApiElasticSearchService.class);
    private final String indexName;
    private final String indexMappingType;

    private final RestClient esRestClient;

    private final IndexTermedService indexTermedService;

    private static Locale[] availableLocales;
    private static Set<String> uniqueNonNullLanguages;

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
        uniqueNonNullLanguages = new HashSet<>();

        for (Locale locale : availableLocales) {
            if (!locale.getLanguage().isEmpty() && !uniqueNonNullLanguages.contains(locale.getLanguage())) {
                uniqueNonNullLanguages.add(locale.getLanguage());
            }
        }
    }

    List<PublicApiConcept> searchConcept(String searchTerm,
                                         String vocabularyId,
                                         String status,
                                         String language) {

        final String endpoint = "/" + indexName + "/" + indexMappingType + "/_search";
        final boolean terminologyGiven = vocabularyId != null && !vocabularyId.isEmpty() && !"0".equals(vocabularyId);

        StringBuilder queryBuilder = new StringBuilder();
        if (language != null && !language.isEmpty() && ElasticRequestUtils.LANGUAGE_CODE_PATTERN.matcher(language).matches()) {
            String labelPart = "label." + language;
            logger.debug("Searching concepts based on '" + labelPart + "'");
            if (!terminologyGiven) {
            queryBuilder.append("{\"query\":{\"bool\":{\"must\":[{\"multi_match\":{\"query\":\"");
            JsonStringEncoder.getInstance().quoteAsString(searchTerm, queryBuilder);
                queryBuilder.append("\",\"fields\":[\"" + labelPart + "\"],\"type\":\"best_fields\",\"minimum_should_match\":\"90%\"}}],\"must_not\":[]}},\"highlight\":{\"pre_tags\":[\"<b>\"],\"post_tags\":[\"</b>\"],\"fields\":{\"label.*\":{}}},\"from\":0,\"size\":100,\"sort\":[\"_score\"]}");
        } else {
            queryBuilder.append("{\"query\":{\"bool\":{\"must\":[{\"match\":{\"vocabulary.id\":\"");
            JsonStringEncoder.getInstance().quoteAsString(vocabularyId, queryBuilder);
            queryBuilder.append("\"}},{\"multi_match\":{\"query\":\"");
            JsonStringEncoder.getInstance().quoteAsString(searchTerm, queryBuilder);
                queryBuilder.append("\",\"fields\":[\"" + labelPart + "\"],\"type\":\"best_fields\",\"minimum_should_match\":\"90%\"}}],\"must_not\":[]}},\"highlight\":{\"pre_tags\":[\"<b>\"],\"post_tags\":[\"</b>\"],\"fields\":{\"label.*\":{}}},\"from\":0,\"size\":100,\"sort\":[\"_score\"]}");
        }
        } else {
            if (language != null && !language.isEmpty()) {
                logger.warn("Rejected concept search language specifier: '" + language + "'");
            }
            if (!terminologyGiven) {
                queryBuilder.append("{\"query\":{\"bool\":{\"must\":[{\"multi_match\":{\"query\":\"");
                JsonStringEncoder.getInstance().quoteAsString(searchTerm, queryBuilder);
                queryBuilder.append("\",\"fields\":[\"label.*\"],\"type\":\"best_fields\",\"minimum_should_match\":\"90%\"}}],\"must_not\":[]}},\"highlight\":{\"pre_tags\":[\"<b>\"],\"post_tags\":[\"</b>\"],\"fields\":{\"label.*\":{}}},\"from\":0,\"size\":100,\"sort\":[\"_score\"]}");
            } else {
                queryBuilder.append("{\"query\":{\"bool\":{\"must\":[{\"match\":{\"vocabulary.id\":\"");
                JsonStringEncoder.getInstance().quoteAsString(vocabularyId, queryBuilder);
                queryBuilder.append("\"}},{\"multi_match\":{\"query\":\"");
                JsonStringEncoder.getInstance().quoteAsString(searchTerm, queryBuilder);
                queryBuilder.append("\",\"fields\":[\"label.*\"],\"type\":\"best_fields\",\"minimum_should_match\":\"90%\"}}],\"must_not\":[]}},\"highlight\":{\"pre_tags\":[\"<b>\"],\"post_tags\":[\"</b>\"],\"fields\":{\"label.*\":{}}},\"from\":0,\"size\":100,\"sort\":[\"_score\"]}");
            }
        }
        NStringEntity body = new NStringEntity(queryBuilder.toString(), ContentType.APPLICATION_JSON);

        try {
            Request request = new Request("GET", endpoint);
            request.setEntity(body);
            Response response = esRestClient.performRequest(request);
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

        uniqueNonNullLanguages.forEach(language -> {
            JsonNode theNode = node.get(fieldName).get(language);
            if (theNode != null) {
                result.put(language, Jsoup.clean(node.get(fieldName).get(language).get(0).textValue(), Whitelist.none()));
            }
        });

        return result;
    }
}
