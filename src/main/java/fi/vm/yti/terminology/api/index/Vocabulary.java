package fi.vm.yti.terminology.api.index;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fi.vm.yti.terminology.api.util.JsonUtils;

public final class Vocabulary {

    private final UUID graphId;
    private final String uri;
    private final Map<String, List<String>> label;
    private final String status;

    private Vocabulary(@NotNull UUID graphId,
                       @NotNull String uri,
                       @NotNull Map<String, List<String>> label,
                       @NotNull String status) {
        this.graphId = graphId;
        this.uri = uri;
        this.label = label;
        this.status = status;
    }

    @NotNull UUID getGraphId() {
        return graphId;
    }

    @NotNull String getUri() {
        return uri;
    }

    @NotNull String getStatus() {
        return status;
    }

    static @NotNull Vocabulary createFromExtJson(@NotNull JsonNode json) {

        JsonNode typeObj = json.get("type");
        JsonNode properties = json.get("properties");
        UUID graphId = UUID.fromString(typeObj.get("graph").get("id").textValue());
        String uri = json.get("uri").textValue();
        Map<String, List<String>> label = JsonUtils.localizableFromTermedProperties(properties, "prefLabel");
        String status = JsonUtils.getSinglePropertyValue(properties, "status");
        if (status == null || status.isEmpty()) {
            status = "DRAFT";
        }
        return new Vocabulary(graphId, uri, label, status);
    }

    static @NotNull Vocabulary createFromIndex(ObjectMapper mapper,
                                               @NotNull JsonNode json) {

        UUID graphId = UUID.fromString(json.get("id").textValue());
        String uri = json.get("uri").textValue();
        Map<String, List<String>> label = JsonUtils.jsonToLocalizable(mapper, json.get("label"));
        String status = json.get("status").textValue();

        return new Vocabulary(graphId, uri, label, status);
    }

    @NotNull ObjectNode toElasticSearchObject(ObjectMapper objectMapper) {

        ObjectNode output = objectMapper.createObjectNode();

        output.set("label", JsonUtils.localizableToJson(objectMapper, label));
        output.put("id", graphId.toString());
        output.put("uri", uri);
        output.put("status", status);

        return output;
    }
}
