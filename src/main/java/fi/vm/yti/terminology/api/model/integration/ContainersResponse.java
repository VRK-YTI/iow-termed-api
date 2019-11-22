
package fi.vm.yti.terminology.api.model.integration;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "uri", "prefLabel", "description", "status", "modified" })
public class ContainersResponse implements Serializable {

    @JsonProperty("uri")
    private String uri = null;
    @JsonProperty("container")
    private String container = null;
    @JsonProperty("prefLabel")
    @Valid
    private Map<String,String> prefLabel = null;
    @JsonProperty("description")
    @JsonInclude(Include.NON_EMPTY)
    @Valid
    private Map<String, String> description = null;
    @JsonProperty("status")
    private String status = null;
    @JsonProperty("modified")
    private String modified = null;

    @JsonProperty("type")
    private String type = null;
    private List<String> languages = null;

    private final static long serialVersionUID = 306028529823257143L;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ContainersResponse() {
    }

    /**
     * 
     * @param status
     * @param description
     * @param prefLabel
     * @param uri
     * @param modified
     */
    public ContainersResponse(String uri, String container,  Map<String,String> prefLabel, Map<String,String> description, String status, String modified) {
//    public ContainersResponse(String uri, String container,  PrefLabel prefLabel, Description description, String status, String modified) {
        super();
        this.uri = uri;
        this.container = container;
        this.prefLabel = prefLabel;
        this.description = description;
        this.status = status;
        this.modified = modified;
    }

    @JsonProperty("uri")
    public String getUri() {
        return uri;
    }

    @JsonProperty("uri")
    public void setUri(String uri) {
        this.uri = uri;
    }
    /**
     * @return String return the container URI
     */
    @JsonProperty("container")
    public String getContainer() {
        return container;
    }

    /**
     * @param container the container to set
     */
    @JsonProperty("container")
    public void setContainer(String container) {
        this.container = container;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("prefLabel")
    public Map<String, String> getPrefLabel() {
        return prefLabel;
    }

    @JsonProperty("prefLabel")
    public void setPrefLabel(Map<String, String> prefLabel) {
        this.prefLabel = prefLabel;
    }

    @JsonProperty("description")
    public Map<String, String> getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(Map<String, String> description) {
        this.description = description;
    }

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @JsonProperty("modified")
    public String getModified() {
        return modified;
    }

    @JsonProperty("modified")
    public void setModified(String modified) {
        this.modified = modified;
    }

    @JsonProperty("languages")
    public List<String> getLanguages() {
        return languages;
    }

    @JsonProperty("languages")
    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    @Override
    public String toString() {
        String value = "{\"uri\":\""+this.uri+"\","+
        "\"container\":\""+this.container+"\","+
        "\"type\":\""+this.type+"\","+
        "\"languages\":\""+this.languages+"\","+
        "\"prefLabel\":\""+this.prefLabel+"\","+
        "\"description\":\""+this.description+"\","+ 
        "\"status\":\""+this.status+"\","+
        "\"modified\":\""+this.modified+"\"}";     
        return value;
    }
}
