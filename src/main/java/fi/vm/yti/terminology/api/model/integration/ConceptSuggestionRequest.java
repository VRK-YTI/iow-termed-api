package fi.vm.yti.terminology.api.model.integration;

import java.util.Objects;

public class ConceptSuggestionRequest {

    /*
     * { "prefLabel":{"fi":"esimerkki"}, "definition":{"fi":"jotain"},
     * "terminologyUri":"http://uri.suomi.fi/terminology/kira/",
     * "identifier":"e15c8009-804c-4aba-a836-f5c911ea5ef1" }
     */
    private LocalizedString prefLabel = null;
    private LocalizedString definition = null;
    private String terminologyUri = null;

    // Jackson constructor
    private ConceptSuggestionRequest() {
        // Jackson constructor
        this(new LocalizedString("", ""), new LocalizedString("", ""));
    }

    public ConceptSuggestionRequest(LocalizedString prefLabel,
                                    LocalizedString definition) {
        this.prefLabel = prefLabel;
        this.definition = definition;
    }

    public static ConceptSuggestionRequest placeholder() {
        return new ConceptSuggestionRequest();
    }

    public LocalizedString getPrefLabel() {
        return prefLabel;
    }

    /**
     * @param prefLabel the prefLabel to set
     */
    public void setPrefLabel(LocalizedString prefLabel) {
        this.prefLabel = prefLabel;
    }

    public LocalizedString getDefinition() {
        return definition;
    }

    /**
     * @param definition the definition to set
     */
    public void setDefinition(LocalizedString definition) {
        if (definition != null) {
            this.definition = definition;
        }
    }

    public String getTerminologyUri() {
        return terminologyUri;
    }

    public void setTerminologyUri(String terminologyUri) {
        this.terminologyUri = terminologyUri;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ConceptSuggestionRequest))
            return false;
        final ConceptSuggestionRequest that = (ConceptSuggestionRequest) o;
        return Objects.equals(prefLabel, that.prefLabel) && Objects.equals(terminologyUri, that.terminologyUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefLabel, terminologyUri);
    }
}
