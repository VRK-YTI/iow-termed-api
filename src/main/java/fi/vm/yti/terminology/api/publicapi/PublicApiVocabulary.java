package fi.vm.yti.terminology.api.publicapi;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PublicApiVocabulary {

    private UUID id;
    private String uri;
    private String status;
    private HashMap<String, String> prefLabel;
    private List<String> languages;

    public UUID getId() {
        return id;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public HashMap<String, String> getPrefLabel() {
        return prefLabel;
    }

    public void setPrefLabel(HashMap<String, String> prefLabel) {
        this.prefLabel = prefLabel;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(final List<String> languages) {
        this.languages = languages;
    }
}
