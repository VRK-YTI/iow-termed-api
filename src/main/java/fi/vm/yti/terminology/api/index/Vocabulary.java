package fi.vm.yti.terminology.api.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fi.vm.yti.terminology.api.util.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Vocabulary {

    private final UUID graphId;
    private final String uri;
    private final Map<String, List<String>> label;

    // Optional URI for a while,
    private Vocabulary(@NotNull UUID graphId,  String uri, @NotNull Map<String, List<String>> label) {
        this.graphId = graphId;
        this.uri = uri;
        this.label = label;
    }

    private Vocabulary(@NotNull UUID graphId, @NotNull Map<String, List<String>> label) {
        this.graphId = graphId;
        this.label = label;
        this.uri = null;
    }

    @NotNull UUID getGraphId() {
        return graphId;
    }

    @NotNull String getUri() {
        return uri;
    }

    static @NotNull Vocabulary createFromExtJson(@NotNull JsonNode json) {

        JsonNode typeObj = json.get("type");
        JsonNode properties = json.get("properties");
        UUID graphId = UUID.fromString(typeObj.get("graph").get("id").textValue());        
        String uri = null;
        if(json.get("uri") != null){
            uri = json.get("uri").textValue();
        }
        Map<String, List<String>> label = JsonUtils.localizableFromTermedProperties(properties, "prefLabel");
        return new Vocabulary(graphId, uri, label);
    }

    static @NotNull Vocabulary createFromIndex(ObjectMapper mapper, @NotNull JsonNode json) {

        UUID graphId = UUID.fromString(json.get("id").textValue());
        String uri = json.get("uri").textValue();
        Map<String, List<String>> label = JsonUtils.jsonToLocalizable(mapper, json.get("label"));

        return new Vocabulary(graphId, uri, label);
    }

    @NotNull ObjectNode toElasticSearchObject(ObjectMapper objectMapper) {

        ObjectNode output = objectMapper.createObjectNode();

        output.set("label", JsonUtils.localizableToJson(objectMapper, label));
        output.put("id", graphId.toString());
        output.put("uri", uri);

        return output;
    }
}
