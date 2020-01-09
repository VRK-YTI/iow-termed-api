package fi.vm.yti.terminology.api.importapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "level",
        "targetIdentifier",
        "message"
})
public class ImportStatusMessage implements Serializable
{
    private static ObjectMapper mapper = new ObjectMapper();

    public enum Level {
        WARNING,ERROR
    };

    /**
     * Level of the message, "Warning/Error"
     */
    @JsonProperty("level")
    private Level level;

    /**
     * Arbitrary identifier about the entity the message is about. Maybe null for,
     * e.g., incorrect file format or other general errors.
     */
    @JsonProperty("targetIdentifier")
    private String targetIdentifier;
    /**
     * At least somewhat human readable message.
     */
    @JsonProperty("message")
    private String message;
    private final static long serialVersionUID = 1851114350787485486L;

    /**
     * No args constructor for use in serialization
     *
     */
    public ImportStatusMessage() {
    }

    /**
     *
     * @param message
     * @param targetIdentifier
     */
    public ImportStatusMessage(String targetIdentifier, String message) {
        super();
        this.level = Level.WARNING; // Default level
        this.targetIdentifier = targetIdentifier;
        this.message = message;
    }

    /**
     *
     * @param message
     * @param targetIdentifier
     */
    public ImportStatusMessage(Level level,String targetIdentifier, String message) {
        super();
        this.level = level;
        this.targetIdentifier = targetIdentifier;
        this.message = message;
    }

    @JsonProperty("level")
    public Level getLevel() {
        return level;
    }

    @JsonProperty("level")
    public void setLevel(Level level) {
        this.level = level;
    }

    @JsonProperty("targetIdentifier")
    public String getTargetIdentifier() {
        return targetIdentifier;
    }

    @JsonProperty("targetIdentifier")
    public void setTargetIdentifier(String targetIdentifier) {
        this.targetIdentifier = targetIdentifier;
    }

    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    @JsonProperty("message")
    public void setMessage(String message) {
        this.message = message;
    }

    public static ImportStatusMessage fromString(String objStr) {
        try {
            return mapper.readValue(objStr, ImportStatusMessage.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
