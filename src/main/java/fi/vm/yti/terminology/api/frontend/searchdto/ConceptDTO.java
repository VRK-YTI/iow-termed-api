package fi.vm.yti.terminology.api.frontend.searchdto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ConceptDTO extends ConceptSimpleDTO {

    private Map<String, String> altLabel;
    private Map<String, String> definition;
    private Instant modified;
    private List<String> narrower;
    private List<String> broader;
    private TerminologySimpleDTO terminology;

    public ConceptDTO(final String id,
                      final String uri,
                      final String status,
                      final Map<String, String> label,
                      final Map<String, String> altLabel,
                      final Map<String, String> definition,
                      final Instant modified,
                      final List<String> narrower,
                      final List<String> broader,
                      final TerminologySimpleDTO terminology) {
        super(id, uri, status, label);

        this.altLabel = altLabel;
        this.definition = definition;
        this.modified = modified;
        this.narrower = narrower;
        this.broader = broader;
        this.terminology = terminology;
    }

    public Map<String, String> getAltLabel() {
        return altLabel;
    }

    public void setAltLabel(final Map<String, String> altLabel) {
        this.altLabel = altLabel;
    }

    public Map<String, String> getDefinition() {
        return definition;
    }

    public void setDefinition(final Map<String, String> definition) {
        this.definition = definition;
    }

    public Instant getModified() {
        return modified;
    }

    public void setModified(final Instant modified) {
        this.modified = modified;
    }

    public List<String> getNarrower() {
        return narrower;
    }

    public void setNarrower(final List<String> narrower) {
        this.narrower = narrower;
    }

    public List<String> getBroader() {
        return broader;
    }

    public void setBroader(final List<String> broader) {
        this.broader = broader;
    }

    public TerminologySimpleDTO getTerminology() {
        return terminology;
    }

    public void setTerminology(final TerminologySimpleDTO terminology) {
        this.terminology = terminology;
    }
}
