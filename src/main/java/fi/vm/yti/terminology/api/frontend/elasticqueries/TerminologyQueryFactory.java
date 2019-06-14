package fi.vm.yti.terminology.api.frontend.elasticqueries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.MatchPhrasePrefixQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
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

    private static final Logger log = LoggerFactory.getLogger(DeepConceptQueryFactory.class);
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_PAGE_FROM = 0;

    private ObjectMapper objectMapper;

    public TerminologyQueryFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SearchRequest createQuery(TerminologySearchRequest request) {
        return createQuery(request.getQuery(), Collections.EMPTY_SET, pageSize(request), pageFrom(request));
    }

    public SearchRequest createQuery(TerminologySearchRequest request,
                                     Collection<String> additionalTerminologyIds) {
        return createQuery(request.getQuery(), additionalTerminologyIds, pageSize(request), pageFrom(request));
    }

    private SearchRequest createQuery(String query,
                                      Collection<String> additionalTerminologyIds,
                                      int pageSize,
                                      int pageFrom) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .from(pageFrom)
            .size(pageSize);

        MatchPhrasePrefixQueryBuilder labelQuery = null;
        if (!query.isEmpty()) {
            labelQuery = QueryBuilders.matchPhrasePrefixQuery("properties.prefLabel.value", query);
            sourceBuilder.highlighter(new HighlightBuilder().preTags("<b>").postTags("</b>").field("properties.prefLabel.value"));
        }

        TermsQueryBuilder idQuery = null;
        if (additionalTerminologyIds != null && !additionalTerminologyIds.isEmpty()) {
            idQuery = QueryBuilders.termsQuery("type.graph.id.keyword", additionalTerminologyIds);
        }

        if (idQuery != null && labelQuery != null) {
            sourceBuilder.query(QueryBuilders.boolQuery()
                .should(labelQuery)
                .should(idQuery)
                .minimumShouldMatch(1));
        } else if (idQuery != null) {
            sourceBuilder.query(idQuery);
        } else if (labelQuery != null) {
            sourceBuilder.query(labelQuery);
        } else {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        SearchRequest sr = new SearchRequest("vocabularies")
            .source(sourceBuilder);
        return sr;
    }

    public TerminologySearchResponse parseResponse(SearchResponse response,
                                                   TerminologySearchRequest request,
                                                   Map<String, List<DeepSearchHitListDTO<?>>> deepSearchHitList) {
        List<TerminologyDTO> terminologies = new ArrayList<>();
        TerminologySearchResponse ret = new TerminologySearchResponse(0, pageFrom(request), terminologies, deepSearchHitList);
        try {
            SearchHits hits = response.getHits();
            ret.setTotalHitCount(hits.getTotalHits());
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

                // TODO: Does not make sense if cannot make to highlight only matching chars
                //handleHighlight(hit.getHighlightFields(), labelMap);

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

    private void handleHighlight(Map<String, HighlightField> highlightFields,
                                 Map<String, String> labelMap) {
        // TODO: Remove this .. err, interesting thing, when index contains things in "label: {fi: 'koira', se: 'hund'}" form
        if (highlightFields != null) {
            HighlightField field = highlightFields.get("properties.prefLabel.value");
            if (field != null) {
                Map<String, String> hmap = new HashMap<>();
                for (Text fragment : field.getFragments()) {
                    String highlightedLabel = fragment.string();
                    String lowlightedLabel = highlightedLabel.replaceAll("</?b>", "");
                    for (Map.Entry<String, String> entry : labelMap.entrySet()) {
                        if (lowlightedLabel.equals(entry.getValue())) {
                            hmap.put(entry.getKey(), highlightedLabel);
                        }
                    }
                }
                labelMap.putAll(hmap);
            }
        }
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
}
