package fi.vm.yti.terminology.api.publicapi;

import java.util.HashMap;
import java.util.UUID;

public class PublicApiConcept {

    private UUID id;
    private HashMap<String, String> prefLabel;
    private HashMap<String, String> definition;
    private HashMap<String, String> vocabularyPrefLabel; //prefLabel of concept's owning prefLabel, needed in the UI
    private UUID vocabularyId;
    private String vocabularyUri;

    private String uri;
    private String url;
    private String status;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getVocabularyUri() {
        return vocabularyUri;
    }

    public void setVocabularyUri(String uri) {
        this.vocabularyUri = uri;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String s) {
        this.status = s;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public HashMap<String, String> getPrefLabel() {
        return prefLabel;
    }

    public void setPrefLabel(HashMap<String, String> prefLabel) {
        this.prefLabel = prefLabel;
    }

    public UUID getVocabularyId() {
        return vocabularyId;
    }

    public void setVocabularyId(UUID vocabularyId) {
        this.vocabularyId = vocabularyId;
    }

    public HashMap<String, String> getDefinition() {
        return definition;
    }

    public void setDefinition(HashMap<String, String> definition) {
        this.definition = definition;
    }

    public HashMap<String, String> getVocabularyPrefLabel() {
        return vocabularyPrefLabel;
    }

    public void setVocabularyPrefLabel(HashMap<String, String> vocabularyPrefLabel) {
        this.vocabularyPrefLabel = vocabularyPrefLabel;
    }
}
