package fi.vm.yti.terminology.api.model.integration;

import java.util.Objects;

public final class PrivateConceptSuggestionRequest extends ConceptSuggestionRequest {

    private String creator = null;

    // Jackson constructor
    private PrivateConceptSuggestionRequest() {
        // Jackson constructor
        this(new LocalizedString("", ""), new LocalizedString("", ""));
    }

    public PrivateConceptSuggestionRequest(LocalizedString prefLabel,
                                           LocalizedString definition) {
        super(prefLabel, definition);
    }

    public PrivateConceptSuggestionRequest(ConceptSuggestionRequest request,
                                           String creator) {
        super(request.getPrefLabel(), request.getDefinition());
        this.creator = creator;
    }

    public static PrivateConceptSuggestionRequest placeholder() {
        return new PrivateConceptSuggestionRequest();
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final PrivateConceptSuggestionRequest that = (PrivateConceptSuggestionRequest) o;
        return Objects.equals(creator, that.creator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), creator);
    }
}
