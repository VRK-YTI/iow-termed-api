package fi.vm.yti.terminology.api.frontend;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.terminology.api.frontend.elasticqueries.ConceptQueryFactory;
import fi.vm.yti.terminology.api.frontend.elasticqueries.DeepConceptQueryFactory;
import fi.vm.yti.terminology.api.frontend.elasticqueries.TerminologyQueryFactory;
import fi.vm.yti.terminology.api.frontend.searchdto.ConceptSearchRequest;
import fi.vm.yti.terminology.api.frontend.searchdto.ConceptSearchResponse;
import fi.vm.yti.terminology.api.frontend.searchdto.DeepSearchHitListDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.TerminologySearchRequest;
import fi.vm.yti.terminology.api.frontend.searchdto.TerminologySearchResponse;
import fi.vm.yti.terminology.api.util.Parameters;
import static fi.vm.yti.terminology.api.util.ElasticRequestUtils.responseContentAsString;

@Service
public class FrontendElasticSearchService {

    private static final Logger logger = LoggerFactory.getLogger(FrontendElasticSearchService.class);

    private final RestHighLevelClient esRestClient;

    private final String indexName;
    private final String indexMappingType;

    private final ObjectMapper objectMapper;
    private final AuthenticatedUserProvider userProvider;
    private final TerminologyQueryFactory terminologyQueryFactory;
    private final DeepConceptQueryFactory deepConceptQueryFactory;
    private final ConceptQueryFactory conceptQueryFactory;

    @Autowired
    public FrontendElasticSearchService(@Value("${search.host.url}") String searchHostUrl,
                                        @Value("${search.host.port}") int searchHostPort,
                                        @Value("${search.host.scheme}") String searchHostScheme,
                                        @Value("${search.index.name}") String indexName,
                                        @Value("${search.index.mapping.type}") String indexMappingType,
                                        @Value("${namespace.root}") String namespaceRoot,
                                        ObjectMapper objectMapper,
                                        AuthenticatedUserProvider userProvider) {
        this.indexName = indexName;
        this.indexMappingType = indexMappingType;
        this.esRestClient = new RestHighLevelClient(RestClient.builder(new HttpHost(searchHostUrl, searchHostPort, searchHostScheme)));

        this.objectMapper = objectMapper;
        this.userProvider = userProvider;
        this.terminologyQueryFactory = new TerminologyQueryFactory(objectMapper);
        this.deepConceptQueryFactory = new DeepConceptQueryFactory(objectMapper);
        this.conceptQueryFactory = new ConceptQueryFactory(objectMapper, namespaceRoot);
    }

    @SuppressWarnings("Duplicates")
    String searchConcept(JsonNode query) {
        return searchFromIndex(query, "concepts");
    }

    private String searchFromIndex(JsonNode query,
                                   String index) {
        Parameters params = new Parameters();
        params.add("source", query.toString());
        params.add("source_content_type", "application/json");
        String endpoint = "/" + index + "/" + indexMappingType + "/_search";
        NStringEntity body = new NStringEntity(query.toString(), ContentType.APPLICATION_JSON);
        try {
            Request request = new Request("POST", endpoint);
            request.setEntity(body);
            Response response = esRestClient.getLowLevelClient().performRequest(request);
            return responseContentAsString(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    ConceptSearchResponse searchConcept(ConceptSearchRequest request) {
        request.setQuery(request.getQuery() != null ? request.getQuery().trim() : "");
        try {
            final boolean superUser = superUser();
            SearchRequest query = conceptQueryFactory.createQuery(request, superUser, limit ->
                superUser ? Collections.emptySet() : terminologiesMatchingOrganizations(readOrganizations(), limit)
            );
            SearchResponse response = esRestClient.search(query, RequestOptions.DEFAULT);
            return conceptQueryFactory.parseResponse(response, request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    TerminologySearchResponse searchTerminology(TerminologySearchRequest request) {
        request.setQuery(request.getQuery() != null ? request.getQuery().trim() : "");

        boolean superUser = superUser();
        Set<String> privilegedOrganizations = superUser ? Collections.emptySet() : readOrganizations();

        Map<String, List<DeepSearchHitListDTO<?>>> deepSearchHits = null;
        if (request.isSearchConcepts() && !request.getQuery().isEmpty()) {
            try {
                Set<String> incompleteFromTerminologies = superUser ? Collections.emptySet() : terminologiesMatchingOrganizations(privilegedOrganizations, null);
                SearchRequest query = deepConceptQueryFactory.createQuery(request.getQuery(), request.getPrefLang(), superUser, incompleteFromTerminologies);
                SearchResponse response = esRestClient.search(query, RequestOptions.DEFAULT);
                deepSearchHits = deepConceptQueryFactory.parseResponse(response, request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            SearchRequest finalQuery;
            if (deepSearchHits != null && !deepSearchHits.isEmpty()) {
                Set<String> additionalTerminilogyIds = deepSearchHits.keySet();
                logger.debug("Deep concept search resulted in " + additionalTerminilogyIds.size() + " terminology matches");
                finalQuery = terminologyQueryFactory.createQuery(request, additionalTerminilogyIds, superUser, privilegedOrganizations);
            } else {
                finalQuery = terminologyQueryFactory.createQuery(request, superUser, privilegedOrganizations);
            }
            SearchResponse response = esRestClient.search(finalQuery, RequestOptions.DEFAULT);
            return terminologyQueryFactory.parseResponse(response, request, deepSearchHits);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean superUser() {
        return userProvider.getUser().isSuperuser();
    }

    private Set<String> readOrganizations() {
        // Any role is OK for reading (viewing data).
        return userProvider.getUser().getRolesInOrganizations().entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(entry -> entry.getKey().toString())
            .collect(Collectors.toSet());
    }

    private Set<String> terminologiesMatchingOrganizations(Collection<String> privilegedOrganizations,
                                                           Collection<String> limitToThese) {
        try {
            if (privilegedOrganizations.isEmpty()) {
                return Collections.emptySet();
            }
            SearchRequest sr = terminologyQueryFactory.createMatchingTerminologiesQuery(privilegedOrganizations, limitToThese);
            SearchResponse response = esRestClient.search(sr, RequestOptions.DEFAULT);
            return terminologyQueryFactory.parseMatchingTerminologiesResponse(response);
        } catch (Exception e) {
            logger.error("Failed to resolve terminologies based on contributors", e);
        }
        return Collections.emptySet();
    }
}
