package fi.vm.yti.terminology.api.model.integration;

import fi.vm.yti.terminology.api.model.termed.*;
import java.util.Objects;

public final class ConceptSuggestionRequest {
    /*
     * { "prefLabel":{"fi":"esimerkki"}, "definition":{"fi":"jotain"},
     * "creator":"45778009-804c-4aba-a836-f5c911ea5ef1",
     * "terminologyUri":"http://uri.suomi.fi/terminology/kira/",
     * "identifier":"e15c8009-804c-4aba-a836-f5c911ea5ef1" }
     */
    private Attribute prefLabel = null;
    private Attribute definition = null;
    private String creator = null;
    private String terminologyUri = null;

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public void setTerminologyUri(String terminologyUri) {
        this.terminologyUri = terminologyUri;
    }

    // Jackson constructor
    private ConceptSuggestionRequest() {
        // Jackson constructor
        this(new Attribute("", ""), new Attribute("", ""));
    }

    public ConceptSuggestionRequest(Attribute prefLabel, Attribute definition) {
        this.prefLabel = prefLabel;
        this.definition = definition;
    }

    public static ConceptSuggestionRequest placeholder() {
        return new ConceptSuggestionRequest();
    }

    public Attribute getPrefLabel() {
        return prefLabel;
    }

    public Attribute getDefinition() {
        return definition;
    }

    public String getCreator() {
        return creator;
    }

    public String getTerminologyUri() {
        return terminologyUri;
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

    /**
     * @param prefLabel the prefLabel to set
     */
    public void setPrefLabel(Attribute prefLabel) {
        this.prefLabel = prefLabel;
    }

    /**
     * @param definition the definition to set
     */
    public void setDefinition(Attribute definition) {
        if (definition != null) {
            this.definition = definition;
        }
    }
}
