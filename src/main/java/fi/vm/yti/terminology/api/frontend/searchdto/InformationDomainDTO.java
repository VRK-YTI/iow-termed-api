package fi.vm.yti.terminology.api.frontend.searchdto;

import java.util.Map;

public class InformationDomainDTO {

    private String id;
    private Map<String, String> label;

    public InformationDomainDTO(final String id,
                                final Map<String, String> label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Map<String, String> getLabel() {
        return label;
    }

    public void setLabel(final Map<String, String> label) {
        this.label = label;
    }

}
