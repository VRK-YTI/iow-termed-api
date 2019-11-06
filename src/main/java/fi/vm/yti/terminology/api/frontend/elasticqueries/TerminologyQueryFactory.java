package fi.vm.yti.terminology.api.frontend.elasticqueries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.frontend.searchdto.DeepSearchHitListDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.InformationDomainDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.OrganizationDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.TerminologyDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.TerminologySearchRequest;
import fi.vm.yti.terminology.api.frontend.searchdto.TerminologySearchResponse;
import fi.vm.yti.terminology.api.util.ElasticRequestUtils;

public class TerminologyQueryFactory {

    private static final Logger log = LoggerFactory.getLogger(TerminologyQueryFactory.class);

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_PAGE_FROM = 0;

    private ObjectMapper objectMapper;

    public TerminologyQueryFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SearchRequest createQuery(TerminologySearchRequest request,
                                     boolean superUser,
                                     Set<String> privilegedOrganizations) {
        return createQuery(request.getQuery(), Collections.EMPTY_SET, pageSize(request), pageFrom(request), superUser, privilegedOrganizations);
    }

    public SearchRequest createQuery(TerminologySearchRequest request,
                                     Collection<String> additionalTerminologyIds,
                                     boolean superUser,
                                     Set<String> privilegedOrganizations) {
        return createQuery(request.getQuery(), additionalTerminologyIds, pageSize(request), pageFrom(request), superUser, privilegedOrganizations);
    }

    private SearchRequest createQuery(String query,
                                      Collection<String> additionalTerminologyIds,
                                      int pageSize,
                                      int pageFrom,
                                      boolean superUser,
                                      Set<String> privilegedOrganizations) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .from(pageFrom)
            .size(pageSize);

        QueryBuilder incompleteQuery = statusAndContributorQuery(privilegedOrganizations);

        QueryBuilder labelQuery = null;
        if (!query.isEmpty()) {
            labelQuery = ElasticRequestUtils.buildPrefixSuffixQuery(query).field("properties.prefLabel.value");
        }

        TermsQueryBuilder idQuery = null;
        if (additionalTerminologyIds != null && !additionalTerminologyIds.isEmpty()) {
            idQuery = QueryBuilders.termsQuery("type.graph.id.keyword", additionalTerminologyIds);
        }

        if (idQuery != null && labelQuery != null) {
            sourceBuilder.query(combineIncompleteQuery(QueryBuilders.boolQuery()
                .should(labelQuery)
                .should(idQuery)
                .minimumShouldMatch(1), incompleteQuery, superUser));
        } else if (idQuery != null) {
            sourceBuilder.query(combineIncompleteQuery(idQuery, incompleteQuery, superUser));
        } else if (labelQuery != null) {
            sourceBuilder.query(combineIncompleteQuery(labelQuery, incompleteQuery, superUser));
        } else {
            if (superUser) {
                sourceBuilder.query(QueryBuilders.matchAllQuery());
            } else {
                sourceBuilder.query(incompleteQuery);
            }
        }

        SearchRequest sr = new SearchRequest("vocabularies")
            .source(sourceBuilder);
        //log.debug("Terminology Query request: " + sr.toString());
        return sr;
    }

    public SearchRequest createMatchingTerminologiesQuery(Set<String> privilegedOrganizations) {
        return createMatchingTerminologiesQuery(privilegedOrganizations, null);
    }

    public SearchRequest createMatchingTerminologiesQuery(final Collection<String> privilegedOrganizations,
                                                          final Collection<String> limitToThese) {
        // TODO: When terminology node ID starts to be "the" id then fetchSource(false) and modify parsing, and change id limit to terms query.
        final QueryBuilder contribQuery = QueryBuilders.termsQuery("references.contributor.id", privilegedOrganizations);
        QueryBuilder finalQuery = contribQuery;
        if (limitToThese != null && !limitToThese.isEmpty()) {
            // QueryBuilders.termsQuery("id", limitToThese);
            final BoolQueryBuilder limitQuery = QueryBuilders.boolQuery().minimumShouldMatch(1);
            for (String id : limitToThese) {
                limitQuery.should(QueryBuilders.matchQuery("type.graph.id", id));
            }
            finalQuery = QueryBuilders.boolQuery()
                .must(contribQuery)
                .must(limitQuery);
        }

        SearchRequest sr = new SearchRequest("vocabularies")
            .source(new SearchSourceBuilder()
                .size(1000)
                .query(finalQuery));
        //.fetchSource(false));
        return sr;
    }

    public Set<String> parseMatchingTerminologiesResponse(SearchResponse response) {
        Set<String> ret = new HashSet<>();
        for (SearchHit hit : response.getHits()) {
            try {
                JsonNode terminology = objectMapper.readTree(hit.getSourceAsString());
                //ret.add(hit.getId());
                ret.add(terminology.get("type").get("graph").get("id").textValue());
            } catch (Exception e) {
                log.error("Cannot parse matching terminologies response", e);
            }
        }
        return ret;
    }

    public TerminologySearchResponse parseResponse(SearchResponse response,
                                                   TerminologySearchRequest request,
                                                   Map<String, List<DeepSearchHitListDTO<?>>> deepSearchHitList) {
        List<TerminologyDTO> terminologies = new ArrayList<>();
        TerminologySearchResponse ret = new TerminologySearchResponse(0, pageFrom(request), terminologies, deepSearchHitList);
        try {
            SearchHits hits = response.getHits();
            ret.setTotalHitCount(hits.getTotalHits());
            Pattern highlightPattern = ElasticRequestUtils.createHighlightPattern(request.getQuery());
            for (SearchHit hit : hits) {
                JsonNode terminology = objectMapper.readTree(hit.getSourceAsString());
                // NOTE: terminology.get("id") would make more sense, but currently concepts contain only graph id => use it here also.
                String terminologyId = terminology.get("type").get("graph").get("id").textValue();
                String terminologyCode = ElasticRequestUtils.getTextValueOrNull(terminology, "code");
                String terminologyUri = ElasticRequestUtils.getTextValueOrNull(terminology, "uri");

                JsonNode properties = terminology.get("properties");
                JsonNode statusArray = properties.get("status");
                String terminologyStatus = statusArray != null ? (statusArray.has(0) ? statusArray.get(0).get("value").textValue() : "DRAFT") : "DRAFT";
                Map<String, String> labelMap = ElasticRequestUtils.labelFromLangValueArray(properties.get("prefLabel"));
                Map<String, String> descriptionMap = ElasticRequestUtils.labelFromLangValueArray(properties.get("description"));

                ElasticRequestUtils.highlightLabel(labelMap, highlightPattern);

                JsonNode references = terminology.get("references");
                JsonNode domainArray = references.get("inGroup");
                JsonNode contributorArray = references.get("contributor");
                List<InformationDomainDTO> domains = new ArrayList<>();
                List<OrganizationDTO> contributors = new ArrayList<>();
                if (domainArray != null) {
                    for (JsonNode domain : domainArray) {
                        String domainId = domain.get("id").textValue();
                        Map<String, String> domainLabel = ElasticRequestUtils.labelFromLangValueArray(domain.get("properties").get("prefLabel"));
                        domains.add(new InformationDomainDTO(domainId, domainLabel));
                    }
                }
                if (contributorArray != null) {
                    for (JsonNode contributor : contributorArray) {
                        String orgId = contributor.get("id").textValue();
                        Map<String, String> orgLabel = ElasticRequestUtils.labelFromLangValueArray(contributor.get("properties").get("prefLabel"));
                        contributors.add(new OrganizationDTO(orgId, orgLabel));
                    }
                }

                terminologies.add(new TerminologyDTO(terminologyId, terminologyCode, terminologyUri, terminologyStatus, labelMap, descriptionMap, domains, contributors));

            }
        } catch (Exception e) {
            log.error("Cannot parse terminology query response", e);
        }
        return ret;
    }

    private int pageSize(TerminologySearchRequest request) {
        Integer size = request.getPageSize();
        if (size != null && size >= 0) {
            return size.intValue();
        }
        return DEFAULT_PAGE_SIZE;
    }

    private int pageFrom(TerminologySearchRequest request) {
        Integer from = request.getPageFrom();
        if (from != null && from >= 0) {
            return from.intValue();
        }
        return DEFAULT_PAGE_FROM;
    }

    private QueryBuilder combineIncompleteQuery(QueryBuilder query,
                                                QueryBuilder incompleteQuery,
                                                boolean superUser) {
        if (superUser) {
            return query;
        }
        return QueryBuilders.boolQuery()
            .must(incompleteQuery)
            .must(query);
    }

    private QueryBuilder statusAndContributorQuery(Set<String> privilegedOrganizations) {
        // Content must either be in some other state than INCOMPLETE, or the user must match a contributor organization.
        QueryBuilder statusQuery = QueryBuilders.boolQuery().mustNot(QueryBuilders.matchQuery("properties.status.value", "INCOMPLETE"));
        QueryBuilder privilegeQuery;
        if (privilegedOrganizations != null && !privilegedOrganizations.isEmpty()) {
            privilegeQuery = QueryBuilders.boolQuery()
                .should(statusQuery)
                .should(QueryBuilders.termsQuery("references.contributor.id", privilegedOrganizations))
                .minimumShouldMatch(1);
        } else {
            privilegeQuery = statusQuery;
        }
        return privilegeQuery;
    }
}
