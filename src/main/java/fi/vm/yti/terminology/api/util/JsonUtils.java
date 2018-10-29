package fi.vm.yti.terminology.api.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

public final class JsonUtils {

	private JsonUtils() {
		// prevent construction
	}

    public static @NotNull JsonNode requireSingle(JsonNode array) {
        return requireNonNull(findSingle(array), "One array item required, was: " + array.size());
    }

    public static @Nullable JsonNode findSingle(@Nullable JsonNode array) {

	    if (array == null) {
	      throw new RuntimeException("Missing node");
        } else if (array.size() > 1) {
            throw new RuntimeException("One or zero array items required, was: " + array.size());
        } else if (array.size() == 0) {
            return null;
        } else {
            return array.get(0);
        }
    }

	public static JsonNode listToJson(ObjectMapper mapper, @NotNull List<UUID> collection) {
        ArrayNode arrayNode = mapper.createArrayNode();
        collection.forEach(item -> arrayNode.add(item.toString()));
        return arrayNode;
	}

    public static @NotNull List<UUID> jsonToList(@NotNull JsonNode arrayNode) {
        Stream<JsonNode> stream = StreamSupport.stream(arrayNode.spliterator(), false);
        return stream.map(node -> UUID.fromString(node.textValue())).collect(toList());
    }

	public static @NotNull ObjectNode localizableToJson(ObjectMapper mapper, @NotNull Map<String, List<String>> localizable) {
	    return mapper.valueToTree(localizable);
	}

	public static @NotNull Map<String, List<String>> jsonToLocalizable(@NotNull ObjectMapper mapper, @NotNull JsonNode json) {
        try {
            return mapper.readValue(mapper.treeAsTokens(json), new TypeReference<Map<String, List<String>>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
	}

	public static @NotNull Map<String, List<String>> localizableFromTermReferences(@NotNull List<JsonNode> termReferences, @NotNull String property) {

		return termReferences.stream()
				.flatMap(x -> {

				    JsonNode properties = x.get("properties");

					if (properties.has(property)) {
						return asStream(properties.get(property));
					} else {
						return Stream.empty();
					}
				})
				.collect(groupingBy(localization -> localization.get("lang").asText(),
						mapping(localization -> localization.get("value").asText(), toList())));
	}

	public static @NotNull Map<String, List<String>> localizableFromTermedProperties(@NotNull JsonNode properties, @NotNull String property) {
		if (properties.has(property)) {
			return localizableFromTermedProperties(properties.get(property));
		} else {
			return Collections.emptyMap();
		}
	}

	private static @NotNull Map<String, List<String>> localizableFromTermedProperties(@NotNull JsonNode property) {
		return asStream(property)
				.collect(groupingBy(localization -> localization.get("lang").textValue(),
						mapping(localization -> localization.get("value").textValue(), toList())));
	}

	public static @Nullable String getSinglePropertyValue(@NotNull JsonNode properties, @NotNull String propertyName) {
		if (properties.has(propertyName)) {
			JsonNode property = properties.get(propertyName);

			if (property.size() == 0) {
				return null;
			} else {
				return property.get(0).get("value").textValue();
			}
		} else {
			return null;
		}
	}

	public static @NotNull Stream<JsonNode> asStream(@Nullable JsonNode node) {
	    if (node == null) {
	        return Stream.empty();
        } else {
            return StreamSupport.stream(node.spliterator(), false);
        }
	}

	public static void prettyPrintJson(Object node){
		ObjectMapper mapper = new ObjectMapper();
		try {
			System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			e.printStackTrace();
		}
	}

	public static String prettyPrintJsonAsString(Object node){
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			e.printStackTrace();
		}
		return "";
	}
}
