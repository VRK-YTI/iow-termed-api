package fi.vm.yti.terminology.api.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.frontend.FrontendGroupManagementService;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.index.IndexElasticSearchService;
import fi.vm.yti.terminology.api.model.integration.ConceptSuggestionRequest;
import fi.vm.yti.terminology.api.model.integration.ConceptSuggestionResponse;
import fi.vm.yti.terminology.api.model.integration.ContainersResponse;
import fi.vm.yti.terminology.api.model.integration.IntegrationContainerRequest;
import fi.vm.yti.terminology.api.model.integration.IntegrationResourceRequest;
import fi.vm.yti.terminology.api.model.integration.LocalizedString;
import fi.vm.yti.terminology.api.model.integration.Meta;
import fi.vm.yti.terminology.api.model.integration.PrivateConceptSuggestionRequest;
import fi.vm.yti.terminology.api.model.integration.ResourcesResponse;
import fi.vm.yti.terminology.api.model.integration.ResponseWrapper;
import fi.vm.yti.terminology.api.model.termed.Attribute;
import fi.vm.yti.terminology.api.model.termed.GenericDeleteAndSave;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.GenericNodeInlined;
import fi.vm.yti.terminology.api.model.termed.Graph;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.util.ElasticRequestUtils;
import fi.vm.yti.terminology.api.util.JsonUtils;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@Service
public class IntegrationService {

    /**
     * Whether to ignore resource states in contributor checks regarding INCOMPLETE
     * things. The differentiating case is when container (terminology) is in some
     * other state (say DRAFT), the resource in question is INCOMPLETE, and given
     * organizations do not match the containers contributors.
     */
    private final static boolean CONFIG_ONLY_CHECK_CONTAINER_STATE = true;
    /**
     * Whether to bypass container level INCOMPLETE checks when containers or
     * resources are queried with given container URI. NOTE that for resource
     * queries th resources are (not) checked according to the
     * {@link #CONFIG_ONLY_CHECK_CONTAINER_STATE} parameter.
     */
    private final static boolean CONFIG_DO_NOT_CHECK_STATE_OF_GIVEN_CONTAINERS = true;

    private static final Logger logger = LoggerFactory.getLogger(IntegrationService.class);
    private static final Set<String> sortLanguages = new HashSet<>(Arrays.asList("fi", "en", "sv"));
    private final FrontendTermedService termedService;
    private final IndexElasticSearchService elasticSearchService;
    private final AuthenticatedUserProvider userProvider;
    private final String indexName;
    private final String VOCABULARY_INDEX = "vocabularies";
    private final String CONCEPTS_INDEX = "concepts";
    private final Pattern uriPattern;

    /**
     * Map containing metadata types. used when creating nodes.
     */
    private HashMap<String, MetaNode> typeMap = new HashMap<>();

    @Autowired
    public IntegrationService(TermedRequester termedRequester,
                              FrontendGroupManagementService groupManagementService,
                              FrontendTermedService frontendTermedService,
                              IndexElasticSearchService elasticSearchService,
                              AuthenticatedUserProvider userProvider,
                              @Value("${search.index.name}") String indexName,
                              @Value("${namespace.root}") String namespaceRoot) {
        this.termedService = frontendTermedService;
        this.elasticSearchService = elasticSearchService;
        this.userProvider = userProvider;
        this.indexName = indexName;
        this.uriPattern = Pattern.compile("^(?<nsuri>" + Pattern.quote(namespaceRoot) + "[\\w\\-]+/)(?<code>[\\w\\-]+)?$");
    }

    ResponseEntity<String> handleContainers(IntegrationContainerRequest request) {

        if (logger.isDebugEnabled()) {
            logger.debug("GET /containers requested. status=" + request.getStatus());
        }

        SearchRequest sr = createContainersQuery(request);
        if (logger.isDebugEnabled()) {
            logger.debug("HandleContainers() query=" + sr.source().toString());
        }

        JsonNode r = elasticSearchService.freeSearchFromIndex(sr);

        // logger.info(" result:" + JsonUtils.prettyPrintJsonAsString(r));
        // Use highLevel API result so
        logger.debug("Raw json-result:" + r);
        Meta meta = new Meta();
        meta.setAfter(request.getAfter());
        meta.setPageSize(request.getPageSize());
        meta.setFrom(request.getPageFrom());
        // Response item list
        List<ContainersResponse> resp = new ArrayList<>();
        if (r != null) {
            r = r.get("hits");
            // Total hits
            if (r.get("total") != null) {
                meta.setTotalResults(r.get("total").asInt());
            }
            r = r.get("hits");
            r.forEach(hit -> {
                JsonNode source = hit.get("_source");
                if (source != null) {
                    // System.out.println("containers-Response=" +
                    // JsonUtils.prettyPrintJsonAsString(source));
                    ContainersResponse cr = parseContainerResponse(source);
                    // Set return type
                    cr.setType("terminology");
                    resp.add(cr);
                } else {
                    logger.error("Missing containers source. Hits:" + hit);
                }
            });
            meta.setResultCount(r.size());
        }

        ResponseWrapper<ContainersResponse> wrapper = new ResponseWrapper<>();
        wrapper.setMeta(meta);
        wrapper.setResults(resp);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        try {
            return new ResponseEntity<>(mapper.writeValueAsString(wrapper), HttpStatus.OK);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new ResponseEntity<>("{}", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private SearchRequest createContainersQuery(IntegrationContainerRequest request) {

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        List<QueryBuilder> mustList = boolQuery.must();
        List<QueryBuilder> mustNotList = boolQuery.mustNot();

        if (request.getBefore() != null) {
            mustList.add(QueryBuilders.rangeQuery("lastModifiedDate").lt(request.getBefore()));
        }

        if (request.getAfter() != null) {
            mustList.add(QueryBuilders.rangeQuery("lastModifiedDate").gte(request.getAfter()).to("now"));
        }

        if (request.getFilter() != null) {
            String u = request.getFilter().toString();
            request.getFilter().forEach(o -> {
                mustNotList.add(QueryBuilders.prefixQuery("uri", o));
            });
        }

        Set<String> terminologyNsUris = null;
        if (request.getUri() != null && !request.getUri().isEmpty()) {
            terminologyNsUris = new HashSet<>();
            BoolQueryBuilder uriBoolQuery = QueryBuilders.boolQuery();
            for (String uriFromRequest : request.getUri()) {
                if (!uriFromRequest.endsWith("/")) {
                    uriFromRequest = uriFromRequest + "/";
                }
                Matcher m = uriPattern.matcher(uriFromRequest);
                if (m.matches() && m.group("code") == null) {
                    uriFromRequest = m.group("nsuri"); // For robustness sake, although should be no-op.
                } else {
                    logger.warn("URI is probably invalid: '" + uriFromRequest + "'");
                }
                // NOTE: This works only for "new type" (namespace uri only) terminology URIs.
                // (Previously the request had already short forms, but in index there was longer "terminology node" uri, and thus prefix query was needed.)
                uriBoolQuery.should(QueryBuilders.termQuery("uri", uriFromRequest));
                terminologyNsUris.add(uriFromRequest);
            }
            uriBoolQuery.minimumShouldMatch(1);
            mustList.add(uriBoolQuery);
        } else {
            // Don't return items without URI
            mustList.add(QueryBuilders.existsQuery("uri"));
        }

        // Checks regarding the "visibility" of INCOMPLETE containers (terminologies)
        {
            // NOTE: If "from" set is given then it kind of overrides the "super user"
            // parameter includeIncomplete.
            final boolean incompleteFromSetGiven = request.getIncludeIncompleteFrom() != null
                && !request.getIncludeIncompleteFrom().isEmpty();
            final boolean directContainersGiven = terminologyNsUris != null && !terminologyNsUris.isEmpty();
            final boolean superUserGiven = request.getIncludeIncomplete();
            final boolean bypassAllChecks = (superUserGiven && !incompleteFromSetGiven)
                || (directContainersGiven && CONFIG_DO_NOT_CHECK_STATE_OF_GIVEN_CONTAINERS);
            if (!bypassAllChecks) {
                if (incompleteFromSetGiven) {
                    // NOTE: Structure and mapping of statuses are wrong on vocabularies index, fix
                    // these when possible.
                    mustList.add(QueryBuilders.boolQuery()
                        .should(QueryBuilders.boolQuery()
                            .mustNot(QueryBuilders.matchQuery("properties.status.value", "INCOMPLETE")))
                        .should(QueryBuilders.termsQuery("references.contributor.id",
                            request.getIncludeIncompleteFrom()))
                        .minimumShouldMatch(1));
                } else {
                    // NOTE: Structure and mapping of statuses are wrong on vocabularies index, fix
                    // these when possible.
                    mustNotList.add(QueryBuilders.matchQuery("properties.status.value", "INCOMPLETE"));
                }
            }
        }

        // NOTE: Status INCOMPLETE has some specific handling earlier.
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            // NOTE: Structure and mapping of statuses are wrong on vocabularies index, fix
            // these when possible. (E.g., cannot use terms query.)
            BoolQueryBuilder statusQuery = QueryBuilders.boolQuery().minimumShouldMatch(1);
            for (String status : request.getStatus()) {
                statusQuery.should(QueryBuilders.matchQuery("properties.status.value", status));
            }
            mustList.add(statusQuery);
        }

        // if search-term is given, match for all labels
        if (request.getSearchTerm() != null && !request.getSearchTerm().isEmpty()) {
            QueryStringQueryBuilder labelQuery = ElasticRequestUtils.buildPrefixSuffixQuery(request.getSearchTerm())
                .field("properties.prefLabel.value");
            mustList.add(labelQuery);
        }

        if (mustList.size() > 0 || mustNotList.size() > 0) {
            sourceBuilder.query(boolQuery);
        } else {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        if (request.getPageFrom() != null) {
            sourceBuilder.from(request.getPageFrom());
        } else {
            sourceBuilder.from(0);
        }

        if (request.getPageSize() != null && request.getPageSize() > 0) {
            sourceBuilder.size(request.getPageSize());
        } else {
            sourceBuilder.size(10000);
        }

        String[] includeFields = new String[] { "id", "properties.prefLabel", "properties.language",
                "properties.description", "createdDate", "lastModifiedDate", "properties.status.value",
                "uri", "references.contributor.id" };
        sourceBuilder.fetchSource(includeFields, null);
        // Add endpoint into the request
        SearchRequest sr = new SearchRequest(VOCABULARY_INDEX).source(sourceBuilder);
        // Add label sorting according to label

        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            // System.out.println("Add sort language:" + request.getLanguage());
            addLanguagePrefLabelSort(request.getLanguage(), "uri", "uri", sourceBuilder);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("SearchRequest=" + sr);
            logger.debug(sr.source().toString());
        }
        return sr;
    }

    /**
     * Transform containers response from elastic to integration-api JSON format
     *
     * @param source
     * @return
     */
    private ContainersResponse parseContainerResponse(JsonNode source) {
        ContainersResponse respItem = new ContainersResponse();
        if (logger.isDebugEnabled()) {
            logger.debug("Parse incoming:\n" + JsonUtils.prettyPrintJsonAsString(source));
        }

        // Some vocabularies has no status at all
        String stat = "DRAFT";
        if (source.findPath("status") != null && !source.findPath("status").isTextual()) {
            if (!source.findPath("status").findPath("value").isNull()
                && !source.findPath("status").findPath("value").asText().isEmpty())
                stat = source.findPath("status").findPath("value").asText();
        }
        respItem.setStatus(stat);

        if (source.get("createdDate") != null) {
            respItem.setCreated(source.get("createdDate").asText());
        }

        if (source.get("lastModifiedDate") != null) {
            respItem.setModified(source.get("lastModifiedDate").asText());
        }

        String uri = null;
        if (source.get("uri") != null) {
            String uriCandidate = source.get("uri").asText();
            if (uriCandidate != null) {
                Matcher m = uriPattern.matcher(uriCandidate);
                if (m.matches()) {
                    uri = m.group("nsuri");
                } else {
                    logger.warn("Invalid terminology URI: '" + uriCandidate + "'");
                }
            }
        }
        if (uri == null) {
            logger.warn("Missing terminology URI");
        }
        respItem.setUri(uri);

        JsonNode label = source.findPath("prefLabel");
        if (label != null) {
            Map<String, String> preflabs = new HashMap<>();
            label.forEach(lb -> {
                String lan = null;
                String val = null;
                if (lb.findPath("lang") != null) {
                    lan = lb.findPath("lang").asText();
                }
                if (lb.findPath("value") != null) {
                    val = lb.findPath("value").asText();
                }
                if (lan != null && val != null) {
                    preflabs.put(lan, val);
                }
            });
            respItem.setPrefLabel(preflabs);
        }

        JsonNode description = source.findPath("description");
        if (description != null) {
            Map<String, String> desc = new HashMap<>();
            description.forEach(de -> {
                String lan = null;
                String val = null;
                if (de.findPath("lang") != null) {
                    lan = de.findPath("lang").asText();
                }
                if (de.findPath("value") != null) {
                    val = de.findPath("value").asText();
                    val = Jsoup.clean(val, Whitelist.none());
                }
                if (lan != null && val != null) {
                    desc.put(lan, val);
                }
            });
            respItem.setDescription(desc);

            List<String> languageList = new ArrayList<String>();
            // Add hard-coded language-list
            JsonNode lang = source.findPath("language");
            if (lang != null) {
                lang.forEach(de -> {
                    String val = null;
                    if (de.findPath("value") != null) {
                        val = de.findPath("value").asText();
                        val = Jsoup.clean(val, Whitelist.none());
                        languageList.add(val);
                    }
                });
                if (logger.isDebugEnabled()) {
                    logger.debug("Vocabulary LANGS=" + languageList);
                }
                respItem.setLanguages(languageList);
            }
        }
        return respItem;
    }

    ResponseEntity<String> handleResources(IntegrationResourceRequest request) {
        if (logger.isDebugEnabled()) {
            logger.debug("(GET/POST) /resources requested. URL=" + request.getContainer() + " UriSet="
                + request.getFilter());
        }

        SearchRequest sr = createResourcesQuery(request);
        if (logger.isDebugEnabled()) {
            logger.debug("HandleVocabularies() query=" + sr.source().toString());
        }

        JsonNode r = elasticSearchService.freeSearchFromIndex(sr);
        // Use highLevel API result so
        if (logger.isDebugEnabled()) {
            logger.debug("raw json-result:" + r);
        }
        Meta meta = new Meta();
        meta.setAfter(request.getAfter());
        meta.setPageSize(request.getPageSize());
        meta.setFrom(request.getPageFrom());
        // If we ask all from all vocabularies, set default pagesize as 1000
        if ((request.getContainer() == null || request.getContainer().isEmpty()) && request.getPageSize() != null
            && request.getPageSize() < 1) {
            meta.setPageSize(1000);
        }
        // Response item list
        List<ResourcesResponse> resp = new ArrayList<>();
        if (r != null) {
            r = r.get("hits");
            // Total hits
            if (r.get("total") != null) {
                meta.setTotalResults(r.get("total").asInt());
            }
            r = r.get("hits");
            r.forEach(hit -> {
                JsonNode source = hit.get("_source");
                if (source != null) {
                    ResourcesResponse node = parseResourceResponse(source);
                    node.setType("concept");
                    if (node.getUri() != null && node.getPrefLabel() != null && node.getStatus() != null) {
                        resp.add(node);
                    } else {
                        logger.error("Resource response missing mandatory fields. dropping " + node);
                    }
                } else {
                    logger.error("handleResources hit=" + hit);
                }
            });
            meta.setResultCount(r.size());
        } else {
            // Empty result list
            meta.setResultCount(0);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("total result count=" + meta.getTotalResults());
            logger.debug("current block  result count=" + meta.getResultCount());
        }

        ResponseWrapper<ResourcesResponse> wrapper = new ResponseWrapper<>();
        wrapper.setMeta(meta);
        wrapper.setResults(resp);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        try {
            return new ResponseEntity<>(mapper.writeValueAsString(wrapper), HttpStatus.OK);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new ResponseEntity<>("{}", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Create resources elastic query. Default intepretation is and. So if multiple fields are defined, result is logical AND
     * between them. For instanse given containers(A,B) and uris(x,y) it returns x,y if those belongs into those namespaces. However
     * if request was: containers(A), uris(x,y) it returns only x, because y was not in A namespace.
     *
     * @param request
     * @return SearchRequest object which contains elastic contructed search and it's additional modifiers.
     * @
     */
    private SearchRequest createResourcesQuery(IntegrationResourceRequest request) {

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        List<QueryBuilder> mustList = boolQuery.must();
        List<QueryBuilder> mustNotList = boolQuery.mustNot();

        // Container filtering (namespaces)
        // NOTE: Filter by requiring resource uri to start with container uri, i.e., the namespace uri.
        // TODO: Fix when concept loans are implemented!
        Set<String> terminologyNsUris = null;
        if (request.getContainer() != null && !request.getContainer().isEmpty()) {
            terminologyNsUris = new HashSet<>();
            BoolQueryBuilder uriBoolQuery = QueryBuilders.boolQuery();
            for (String uriFromRequest : request.getContainer()) {
                if (!uriFromRequest.endsWith("/")) {
                    uriFromRequest = uriFromRequest + "/";
                }
                Matcher m = uriPattern.matcher(uriFromRequest);
                if (m.matches() && m.group("code") == null) {
                    uriFromRequest = m.group("nsuri"); // For robustness sake, although should be no-op.
                    // We compare container uri (namespace uri) to resource uri, so use prefix. Trailing slash in ns should make this safe.
                    uriBoolQuery.should(QueryBuilders.prefixQuery("uri", uriFromRequest));
                } else {
                    logger.warn("URI is probably invalid: '" + uriFromRequest + "'");
                    uriBoolQuery.should(QueryBuilders.termQuery("uri", uriFromRequest)); // basically will not match
                }
                terminologyNsUris.add(uriFromRequest);
            }
            uriBoolQuery.minimumShouldMatch(1);
            mustList.add(uriBoolQuery);
        }

        // Handle uri list. More exact items inside containers. In general (container namespace)/(concept)
        if (request.getUri() != null && !request.getUri().isEmpty()) {
            QueryBuilder uriQuery = QueryBuilders.boolQuery().should(QueryBuilders.termsQuery("uri", request.getUri()))
                .minimumShouldMatch(1);
            mustList.add(uriQuery);
        }

        // Check existence of either container or uri and if missing, ensure that returned items have some URI defined.
        if (request.getContainer() == null && request.getUri() == null) {
            // Don't return items without URI
            mustList.add(QueryBuilders.existsQuery("uri"));
        }

        // Checks regarding the "visibility" of INCOMPLETE containers (terminologies)
        // and resources (concepts).
        {
            // NOTE: If "from" set is given then it kind of overrides the "super user"
            // parameter includeIncomplete.
            final boolean incompleteFromSetGiven = request.getIncludeIncompleteFrom() != null
                && !request.getIncludeIncompleteFrom().isEmpty();
            final boolean directContainerUriGiven = terminologyNsUris != null;
            final boolean superUserGiven = request.getIncludeIncomplete();
            final boolean bypassAllChecks = (superUserGiven && !incompleteFromSetGiven) || (directContainerUriGiven
                && CONFIG_DO_NOT_CHECK_STATE_OF_GIVEN_CONTAINERS && CONFIG_ONLY_CHECK_CONTAINER_STATE);
            if (!bypassAllChecks) {
                if (incompleteFromSetGiven) {
                    Set<String> terminologyIds;
                    if (directContainerUriGiven) {
                        // In this case the ID set should have at most one ID, but the same logic
                        // suffices
                        terminologyIds = resolveTerminologiesMatchingOrganizations(request.getIncludeIncompleteFrom(),
                            terminologyNsUris);
                    } else {
                        terminologyIds = resolveTerminologiesMatchingOrganizations(request.getIncludeIncompleteFrom(),
                            Collections.emptySet());
                    }
                    BoolQueryBuilder statusQuery = QueryBuilders.boolQuery();
                    if (!directContainerUriGiven || !CONFIG_DO_NOT_CHECK_STATE_OF_GIVEN_CONTAINERS) {
                        statusQuery.mustNot(QueryBuilders.termQuery("vocabulary.status", "INCOMPLETE"));
                    }
                    if (!CONFIG_ONLY_CHECK_CONTAINER_STATE) {
                        statusQuery.mustNot(QueryBuilders.termQuery("status", "INCOMPLETE"));
                    }
                    mustList.add(QueryBuilders.boolQuery().should(statusQuery)
                        .should(QueryBuilders.termsQuery("vocabulary.id", terminologyIds)).minimumShouldMatch(1));
                } else {
                    mustNotList.add(QueryBuilders.termQuery("vocabulary.status", "INCOMPLETE"));
                    if (!CONFIG_ONLY_CHECK_CONTAINER_STATE) {
                        mustNotList.add(QueryBuilders.termQuery("status", "INCOMPLETE"));
                    }
                }
            }
        }

        // if search-term is given, match for all labels
        if (request.getSearchTerm() != null && !request.getSearchTerm().isEmpty()) {
            logger.info("Additional SearchTerm=" + request.getSearchTerm());
            QueryStringQueryBuilder labelQuery = ElasticRequestUtils.buildPrefixSuffixQuery(request.getSearchTerm())
                .field("label.*");
            mustList.add(labelQuery);
        }

        // Date comparison: before, after
        if (request.getBefore() != null) {
            mustList.add(QueryBuilders.rangeQuery("modified").lt(request.getBefore()));
        }

        if (request.getAfter() != null) {
            mustList.add(QueryBuilders.rangeQuery("modified").gte(request.getAfter()).to("now"));
        }

        // Exclude filter. Filter out given uris
        if (request.getFilter() != null) {
            logger.info("Exclude filter:" + request.getFilter());
            mustNotList.add(QueryBuilders.termsQuery("uri", request.getFilter()));
        }

        // NOTE: Status INCOMPLETE has some specific handling earlier.
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            mustList.add(QueryBuilders.termsQuery("status", request.getStatus()));
        }

        sourceBuilder.query(boolQuery);

        // paging handling 
        if (request.getPageFrom() != null) {
            sourceBuilder.from(request.getPageFrom());
        } else {
            sourceBuilder.from(0);
        }

        if (request.getPageSize() != null && request.getPageSize() > 0) {
            sourceBuilder.size(request.getPageSize());
        } else {
            sourceBuilder.size(10000);
        }

        String[] includeFields = new String[] { "id", "label", "definition", "created", "modified", "status", "uri" };
        sourceBuilder.fetchSource(includeFields, null);
        // Add endpoint into the request
        SearchRequest sr = new SearchRequest(CONCEPTS_INDEX).source(sourceBuilder);
        // Add label sorting according to label
        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            addLanguagePrefLabelSort(request.getLanguage(), "sortByLabel.fi", "label", sourceBuilder);
        }
        if (logger.isDebugEnabled()) {
            logger.info("SearchRequest=" + sr);
            logger.debug(sr.source().toString());
        }
        return sr;
    }

    /**
     * Transform incoming response into the resource-api JSON form
     */
    private ResourcesResponse parseResourceResponse(JsonNode source) {
        String stat = null;
        String createdDate = null;
        String modifiedDate = null;
        String uri = null;
        String container = null;

        // logger.info("parseResponse:\n" + JsonUtils.prettyPrintJsonAsString(source));

        if (source.get("status") != null) {
            stat = source.get("status").asText();
        } else {
            logger.warn("Resource response missing status");
        }
        if (source.get("created") != null) {
            createdDate = source.get("created").asText();
        } else {
            logger.warn("Resource response missing created date");
        }
        if (source.get("modified") != null) {
            modifiedDate = source.get("modified").asText();
        } else {
            logger.warn("Resource response missing modified date");
        }

        if (source.get("uri") != null) {
            // NOTE: At the moment we expect resource uri to start with container uri, i.e., the namespace uri.
            // TODO: Fix when concept loans are implemented!
            uri = source.get("uri").asText();
            Matcher m = uriPattern.matcher(uri);
            if (m.matches()) {
                container = m.group("nsuri");
            } else {
                logger.warn("Invalid URI: '" + uri + "'");
            }
        } else {
            logger.warn("Resource response missing URI");
        }

        ResourcesResponse respItem = new ResourcesResponse();
        respItem.setUri(uri);
        respItem.setStatus(stat);
        if (container != null && !container.isEmpty()) {
            respItem.setContainer(container);
        }
        if (createdDate != null) {
            respItem.setCreated(createdDate);
        }
        if (modifiedDate != null) {
            respItem.setModified(modifiedDate);
        }
        JsonNode label = source.get("label");
        if (label != null) {
            Map<String, String> preflabs = new HashMap<>();
            Iterator<String> i = label.fieldNames();
            while (i.hasNext()) {
                String lan = i.next();
                JsonNode jn = label.get(lan);
                if (jn != null) {
                    preflabs.put(lan, Jsoup.clean(jn.get(0).asText(), Whitelist.none()));
                }
            }
            respItem.setPrefLabel(preflabs);
        }

        JsonNode description = source.get("definition");
        if (description != null) {
            Map<String, String> desc = new HashMap<>();
            Iterator<String> i = description.fieldNames();
            while (i.hasNext()) {
                String lan = i.next();
                JsonNode jn = description.get(lan);
                if (jn != null) {
                    desc.put(lan, Jsoup.clean(jn.get(0).asText(), Whitelist.none()));
                }
            }
            respItem.setDescription(desc);
        }
        return respItem;
    }

    private void addLanguagePrefLabelSort(final String language,
                                          final String backupSortField,
                                          final String sortFieldWithoutLanguage,
                                          final SearchSourceBuilder searchBuilder) {
        if (language != null && !language.isEmpty()) {
            searchBuilder.sort(SortBuilders.fieldSort("label." + language + ".keyword").order(SortOrder.ASC)
                .unmappedType("keyword"));
            sortLanguages.forEach(sortLanguage -> {
                if (!language.equalsIgnoreCase(sortLanguage)) {
                    searchBuilder.sort(SortBuilders.fieldSort("label." + sortLanguage + ".keyword").order(SortOrder.ASC)
                        .unmappedType("keyword"));
                }
            });
            searchBuilder.sort(backupSortField, SortOrder.ASC);
        } else {
            searchBuilder.sort(sortFieldWithoutLanguage, SortOrder.ASC);
        }
    }

    /**
     * Initialize cached META-model. - Read given vocabularity for meta-types, cache
     * them
     *
     * @param vocabularyId UUID of the vocabularity
     */
    private void initMetaModel(UUID vocabularyId) {
        // Get metamodel types for given vocabularity
        List<MetaNode> metaTypes = termedService.getTypes(vocabularyId);
        metaTypes.forEach(t -> {
            typeMap.put(t.getId(), t);
        });
    }

    /**
     * Executes concept suggestion operation. Reads incoming json and process it
     *
     * @return
     */
    ResponseEntity<String> handleConceptSuggestion(PrivateConceptSuggestionRequest incomingConcept) {
        if (logger.isDebugEnabled())
            logger.debug("POST /vocabulary/concept requested. creating Concept for "
                + JsonUtils.prettyPrintJsonAsString(incomingConcept));
        UUID activeVocabulary;
        String terminologyUri = incomingConcept.getTerminologyUri();
        ConceptSuggestionResponse outgoingResponse = new ConceptSuggestionResponse();

        // Check that mandatory id exists
        if (terminologyUri == null) {
            return new ResponseEntity<>("Created Concept suggestion failed. Mandatory terminology URI is missing.\n",
                HttpStatus.NOT_FOUND);
        }

        // Concept reference-map
        Map<String, List<Identifier>> conceptReferences = new HashMap<>();

        // Get vocabularies and match code with name
        List<Graph> vocs = termedService.getGraphs();
        // Filter given code as result
        List<IdCode> vocabularies = vocs.stream().filter(o -> o.getUri().equalsIgnoreCase(terminologyUri)).map(o -> {
            // List<IdCode> vocabularies = vocs.stream().filter(o ->
            // o.getCode().equalsIgnoreCase(terminologyUri.toString())).map(o -> {
            return new IdCode(o.getCode(), o.getId());
        }).collect(Collectors.toList());
        if (vocabularies.size() > 1) {
            return new ResponseEntity<>("Created Concept suggestion failed for " + terminologyUri
                + ". Multiple matches for terminology. \n", HttpStatus.NOT_FOUND);
        } else if (vocabularies.size() == 1) {
            // found, set UUID
            activeVocabulary = vocabularies.get(0).id;
        } else {
            return new ResponseEntity<>(
                "Created Concept suggestion failed for " + terminologyUri + ". Terminology not found. \n",
                HttpStatus.NOT_FOUND);
        }

        // Try to fetch it just to ensure it exist
        GenericNodeInlined vocabularyNode = termedService.getVocabulary(activeVocabulary);
        if (vocabularyNode == null) {
            return new ResponseEntity<>(
                "Created Concept suggestion failed for UUID:" + terminologyUri + ". Terminology not found. \n",
                HttpStatus.NOT_FOUND);
        }

        // get metamodel for vocabulary
        initMetaModel(activeVocabulary);
        // Create new Term
        GenericNode term = CreateTerm(vocabularyNode, incomingConcept, conceptReferences);
        // Create new Concept
        GenericNode concept = CreateConcept(vocabularyNode, incomingConcept, conceptReferences);
        if (term != null && concept != null) {
            outgoingResponse.setTerminologyUri(vocabularyNode.getUri());
            if (userProvider.getUser() != null && userProvider.getUser().getId() != null) {
                outgoingResponse.setCreator(userProvider.getUser().getId().toString());
            }
            // Publish them to server
            List<GenericNode> addNodeList = new ArrayList<>();
            addNodeList.add(term);
            addNodeList.add(concept);
            GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(), addNodeList);
            termedService.bulkChangeWithoutAuthorization(operation, true,
                UUID.fromString(incomingConcept.getCreator()));
            if (logger.isDebugEnabled())
                logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
            // Fetch created concept and get it's URI, set it to the returned json
            // Return also it's UUID
            GenericNode createdConcept = termedService.getConceptNode(activeVocabulary, concept.getId());
            outgoingResponse.setTerminologyUri(incomingConcept.getTerminologyUri());
            outgoingResponse.setDefinition(incomingConcept.getDefinition());
            outgoingResponse.setPrefLabel(incomingConcept.getPrefLabel());
            outgoingResponse.setUri(createdConcept.getUri());
            outgoingResponse.setCreated(createdConcept.getCreatedDate());
        }
        return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(outgoingResponse), HttpStatus.OK);
    }

    private GenericNode CreateTerm(GenericNodeInlined vocabulary,
                                   ConceptSuggestionRequest incoming,
                                   Map<String, List<Identifier>> parentReferences) {
        GenericNode node = null;
        // Populate term
        Map<String, List<Attribute>> properties = new HashMap<>();
        addProperty("prefLabel", properties, LocalizedString.asAttribute(incoming.getPrefLabel()));
        Attribute att = new Attribute("", "SUGGESTED");
        addProperty("status", properties, att);
        // Create Concept
        TypeId typeId = typeMap.get("Term").getDomain();
        node = new GenericNode(typeId, properties, emptyMap());

        // Add term as prefLabel for paren concept.
        List<Identifier> ref;
        if (parentReferences.get("prefLabelXl") != null)
            ref = parentReferences.get("prefLabelXl");
        else
            ref = new ArrayList<>();
        ref.add(new Identifier(node.getId(), typeId));
        parentReferences.put("prefLabelXl", ref);
        return node;
    }

    /**
     * Add individual named attribute to property list
     *
     * @param attributeName like prefLabel
     * @param properties    Propertylist where attribute is added
     * @param att           Attribute to be added
     */
    private void addProperty(String attributeName,
                             Map<String, List<Attribute>> properties,
                             Attribute att) {
        if (!properties.containsKey(attributeName)) {
            List<Attribute> a = new ArrayList<>();
            a.add(att);
            properties.put(attributeName, a);
        } else
            properties.get(attributeName).add(att);
    }

    private GenericNode CreateConcept(GenericNodeInlined vocabulary,
                                      ConceptSuggestionRequest incoming,
                                      Map<String, List<Identifier>> conceptReferences) {
        GenericNode node = null;
        Map<String, List<Attribute>> properties = new HashMap<>();
        addProperty("definition", properties, LocalizedString.asAttribute(incoming.getDefinition()));
        Attribute att = new Attribute("", "SUGGESTED");
        addProperty("status", properties, att);

        // Create Concept
        TypeId typeId = typeMap.get("Concept").getDomain();
        // Note! Autogenerated UUID
        node = new GenericNode(typeId, properties, conceptReferences);
        return node;
    }

    /**
     * Inner class for vocabulary-UUID pair.
     */
    private class IdCode {

        String code;
        UUID id;

        public IdCode(String code,
                      UUID id) {
            this.code = code;
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

    }

    /**
     * Fetch ids for all terminologies that have contributor match with given
     * organization ids.
     *
     * @param organizationIds UUIDs for the organizations as strings
     * @param onlyTheseNsUris If not empty then limit the result set to
     *                        terminologies having these namespace URIs. NOTE: NS
     *                        URIs, not IDs, nor node URIs!
     * @return set of terminology UUIDs as strings
     */
    private Set<String> resolveTerminologiesMatchingOrganizations(Set<String> organizationIds,
                                                                  Set<String> onlyTheseNsUris) {
        Set<String> ret = new HashSet<>();
        try {
            QueryBuilder query = QueryBuilders.termsQuery("references.contributor.id", organizationIds);
            if (onlyTheseNsUris != null && !onlyTheseNsUris.isEmpty()) {
                BoolQueryBuilder compound = QueryBuilders.boolQuery().must(query);
                for (String nsUri : onlyTheseNsUris) {
                    compound.should(QueryBuilders.prefixQuery("uri", nsUri));
                }
                query = compound.minimumShouldMatch(1);
            }
            SearchRequest request = new SearchRequest("vocabularies")
                .source(new SearchSourceBuilder().query(query).size(1000));
            // logger.debug("Matching terminologies request: " + request.toString());
            JsonNode response = elasticSearchService.freeSearchFromIndex(request);
            if (response != null) {
                JsonNode hitsMeta = response.get("hits");
                if (hitsMeta != null) {
                    JsonNode hits = hitsMeta.get("hits");
                    if (hits != null) {
                        for (JsonNode hit : hits) {
                            ret.add(hit.get("_source").get("type").get("graph").get("id").textValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error while resolving terminologies matching contributors.", e);
        }
        logger.debug(
            "Resolved " + ret.size() + " matching terminologies for " + organizationIds.size() + " organizations"
                + (onlyTheseNsUris != null ? " (" + onlyTheseNsUris.size() + " limiting URIs)" : ""));
        return ret;
    }
}
