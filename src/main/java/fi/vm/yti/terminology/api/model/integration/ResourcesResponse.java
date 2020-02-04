
package fi.vm.yti.terminology.api.model.integration;

import java.io.Serializable;
import java.util.Map;

import javax.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "uri", "type", "container", "prefLabel", "description", "status", "created", "modified" })
public class ResourcesResponse implements Serializable {

    private String uri = null;
    private String container = null;
    @Valid
    private Map<String, String> prefLabel = null;
    @JsonInclude(Include.NON_EMPTY)
    @Valid
    private Map<String, String> description = null;
    private String status = null;
    private String created = null;
    private String modified = null;
    private String type = null;

    private final static long serialVersionUID = 306028529823257143L;

    /**
     * No args constructor for use in serialization
     */
    public ResourcesResponse() {
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public Map<String, String> getPrefLabel() {
        return prefLabel;
    }

    public void setPrefLabel(Map<String, String> prefLabel) {
        this.prefLabel = prefLabel;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public void setDescription(Map<String, String> description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getModified() {
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
    }

    @Override
    public String toString() {
        String value = "{\"uri\":\"" + this.uri + "\"," +
            "\"type\":\"" + this.type + "\"," +
            "\"container\":\"" + this.container + "\"," +
            "\"prefLabel\":\"" + this.prefLabel + "\"," +
            "\"description\":\"" + this.description + "\"," +
            "\"status\":\"" + this.status + "\"," +
            "\"created\":\"" + this.created + "\"," +
            "\"modified\":\"" + this.modified + "\"}";
        return value;
    }
}
