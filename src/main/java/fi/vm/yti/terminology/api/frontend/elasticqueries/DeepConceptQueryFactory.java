package fi.vm.yti.terminology.api.frontend.elasticqueries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.frontend.searchdto.ConceptSimpleDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.DeepSearchConceptHitListDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.DeepSearchHitListDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.TerminologySearchRequest;
import fi.vm.yti.terminology.api.util.ElasticRequestUtils;

public class DeepConceptQueryFactory {

    private static final Logger log = LoggerFactory.getLogger(DeepConceptQueryFactory.class);

    private static final FetchSourceContext sourceIncludes = new FetchSourceContext(true, new String[]{ "id", "uri", "status", "label", "vocabulary" }, new String[]{});
    private static final Script topHitScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, "_score", Collections.emptyMap());

    private final ObjectMapper objectMapper;

    public DeepConceptQueryFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SearchRequest createQuery(String query,
                                     String prefLang,
                                     boolean superUser,
                                     Set<String> incompleteFromTerminologies) {

        // NOTE: In deep concept query the query should always be non-empty.
        QueryBuilder labelQuery = ElasticRequestUtils.buildPrefixSuffixQuery(query).field("label.*");

        // Block INCOMPLETE concepts from being shown to users who are not contributors of the terminology. Needed when the terminology itself is in some visible state.
        QueryBuilder withIncompleteHandling = superUser ? labelQuery : QueryBuilders.boolQuery()
            .must(labelQuery)
            .must(QueryBuilders.boolQuery()
                .should(QueryBuilders.boolQuery().mustNot(QueryBuilders.termQuery("status", "INCOMPLETE")))
                .should(QueryBuilders.termsQuery("vocabulary.id", incompleteFromTerminologies))
                .minimumShouldMatch(1));

        SearchRequest sr = new SearchRequest("concepts")
            .source(new SearchSourceBuilder()
                .query(withIncompleteHandling)
                .size(0)
                .aggregation(AggregationBuilders.terms("group_by_terminology")
                    .field("vocabulary.id")
                    .size(1000)
                    .order(BucketOrder.aggregation("best_concept_hit", false))
                    .subAggregation(AggregationBuilders.topHits("top_concept_hits")
                        .sort(SortBuilders.scoreSort().order(SortOrder.DESC))
                        .size(6)
                        .fetchSource(sourceIncludes))
                    .subAggregation(AggregationBuilders.max("best_concept_hit")
                        .script(topHitScript))));
        //log.debug("Deep Concept Query request: " + sr.toString());
        return sr;
    }

    public Map<String, List<DeepSearchHitListDTO<?>>> parseResponse(SearchResponse response, TerminologySearchRequest request) {
        Map<String, List<DeepSearchHitListDTO<?>>> ret = new HashMap<>();
        try {
            Pattern highlightPattern = ElasticRequestUtils.createHighlightPattern(request.getQuery());
            Terms groupBy = response.getAggregations().get("group_by_terminology");
            for (Terms.Bucket bucket : groupBy.getBuckets()) {
                TopHits hitsAggr = bucket.getAggregations().get("top_concept_hits");
                SearchHits hits = hitsAggr.getHits();

                long total = hits.getTotalHits();
                if (total > 0) {
                    String terminologyId = bucket.getKeyAsString();
                    List<ConceptSimpleDTO> topHits = new ArrayList<>();
                    DeepSearchConceptHitListDTO hitList = new DeepSearchConceptHitListDTO(total, topHits);
                    ret.put(terminologyId, Collections.singletonList(hitList));

                    for (SearchHit hit : hits.getHits()) {
                        JsonNode concept = objectMapper.readTree(hit.getSourceAsString());
                        String conceptId = ElasticRequestUtils.getTextValueOrNull(concept, "id");
                        String conceptUri = ElasticRequestUtils.getTextValueOrNull(concept, "uri");
                        String conceptStatus = ElasticRequestUtils.getTextValueOrNull(concept, "status");
                        Map<String, String> labelMap = ElasticRequestUtils.labelFromKeyValueNode(concept.get("label"));

                        ElasticRequestUtils.highlightLabel(labelMap, highlightPattern);

                        ConceptSimpleDTO dto = new ConceptSimpleDTO(conceptId, conceptUri, conceptStatus, labelMap);
                        topHits.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Cannot parse deep concept query response", e);
        }
        return ret;
    }
}
