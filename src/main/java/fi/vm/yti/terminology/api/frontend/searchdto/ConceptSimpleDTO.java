package fi.vm.yti.terminology.api.frontend.searchdto;

import java.util.Map;

public class ConceptSimpleDTO {

    private String id;
    private String uri;
    private String status;
    private Map<String, String> label;

    public ConceptSimpleDTO(final String id,
                            final String uri,
                            final String status,
                            final Map<String, String> label) {
        this.id = id;
        this.uri = uri;
        this.status = status;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(final String uri) {
        this.uri = uri;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public Map<String, String> getLabel() {
        return label;
    }

    public void setLabel(final Map<String, String> label) {
        this.label = label;
    }
}
