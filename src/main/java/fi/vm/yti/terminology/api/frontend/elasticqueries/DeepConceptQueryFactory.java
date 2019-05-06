package fi.vm.yti.terminology.api.frontend.elasticqueries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.frontend.searchdto.ConceptSimpleDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.DeepSearchConceptHitListDTO;
import fi.vm.yti.terminology.api.frontend.searchdto.DeepSearchHitListDTO;
import fi.vm.yti.terminology.api.util.ElasticRequestUtils;

public class DeepConceptQueryFactory {

    private static final Logger log = LoggerFactory.getLogger(DeepConceptQueryFactory.class);
    private static final Pattern prefLangPattern = Pattern.compile("[a-z]{2,3}");

    private static final String part0 =
        "{\n" +
            "  \"query\" : {\n" +
            "    \"bool\" : {\n" +
            "      \"must\": [ {\n" +
            "        \"multi_match\" : { \n" +
            "          \"query\" : \"";
    private static final String part1_1 =
        "\",\n" +
            "          \"fields\" : [ ";
    private static final String part1_2 =
        "\"label.*\" ],\n" +
            "          \"type\" : \"best_fields\",\n" +
            "          \"minimum_should_match\" : \"90%\"\n" +
            "        }\n" +
            "      } ],\n" +
            "      \"must_not\" : []\n" +
            "    }\n" +
            "  },\n" +
            "  \"size\" : 0,\n" +
            "  \"aggs\" : {\n" +
            "    \"group_by_terminology\" : {\n" +
            "    \"terms\" : {\n" +
            "      \"field\" : \"vocabulary.id\",\n" +
            "      \"size\" : 1000,\n" +
            "      \"order\" : { \"top_hit\" : \"desc\" }\n" +
            "      },\n" +
            "      \"aggs\" : {\n" +
            "        \"top_terminology_hits\" : {\n" +
            "          \"top_hits\" : {\n" +
            "            \"highlight\": {\n" +
            "              \"pre_tags\": [\"<b>\"],\n" +
            "              \"post_tags\": [\"</b>\"],\n" +
            "              \"fields\": {\n" +
            "                \"label.*\": {}\n" +
            "              }\n" +
            "            },\n" +
            "            \"sort\" : [ { \"_score\" : { \"order\" : \"desc\" } } ],\n" +
            "            \"size\" : 6,\n" +
            "            \"_source\" : {\n" +
            "              \"includes\" : [ \"id\", \"uri\", \"status\", \"label\", \"vocabulary\" ]\n" +
            "            }\n" +
            "          }\n" +
            "        },\n" +
            "        \"top_hit\" : { \"max\" : { \"script\" : { \"source\" : \"_score\" } } }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}\n";

    private ObjectMapper objectMapper;

    public DeepConceptQueryFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createQuery(String query,
                              String prefLang) {
        StringBuilder sb = new StringBuilder(part0);
        JsonStringEncoder.getInstance().quoteAsString(query, sb);
        sb.append(part1_1);
        if (prefLang != null && prefLangPattern.matcher(prefLang).matches()) {
            sb.append("\"label." + prefLang + "^10\", ");
        }
        sb.append(part1_2);
        return sb.toString();
    }

    public Map<String, List<DeepSearchHitListDTO<?>>> parseResponse(Response response) {
        Map<String, List<DeepSearchHitListDTO<?>>> ret = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(response.getEntity().getContent());
            JsonNode buckets = root.get("aggregations").get("group_by_terminology").get("buckets");
            for (JsonNode bucket : buckets) {
                JsonNode meta = bucket.get("top_terminology_hits").get("hits");
                int total = meta.get("total").intValue();
                if (total > 0) {
                    String terminologyId = bucket.get("key").textValue();
                    List<ConceptSimpleDTO> topHits = new ArrayList<>();
                    DeepSearchConceptHitListDTO hitList = new DeepSearchConceptHitListDTO(total, topHits);
                    ret.put(terminologyId, Collections.singletonList(hitList));

                    JsonNode hits = meta.get("hits");
                    for (JsonNode hit : hits) {
                        JsonNode concept = hit.get("_source");
                        String conceptId = ElasticRequestUtils.getTextValueOrNull(concept, "id");
                        String conceptUri = ElasticRequestUtils.getTextValueOrNull(concept, "uri");
                        String conceptStatus = ElasticRequestUtils.getTextValueOrNull(concept, "status");
                        Map<String, String> labelMap = ElasticRequestUtils.labelFromKeyValueNode(concept.get("label"));

                        JsonNode highlight = hit.get("highlight");
                        if (highlight != null) {
                            Iterator<Map.Entry<String, JsonNode>> hlightIter = highlight.fields();
                            while (hlightIter.hasNext()) {
                                Map.Entry<String, JsonNode> hlight = hlightIter.next();
                                String key = hlight.getKey();
                                // TODO
                                String value = hlight.getValue().get(0).textValue();
                                if (key.startsWith("label.") && value != null && !value.isEmpty()) {
                                    labelMap.put(key.substring(6), value);
                                }
                            }
                        }

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
