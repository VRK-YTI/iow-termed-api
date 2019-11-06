package fi.vm.yti.terminology.api.frontend.searchdto;

import java.util.Collections;
import java.util.List;

public class ConceptSearchResponse {

    private long totalHitCount;
    private int resultStart;
    private List<ConceptDTO> concepts;

    public ConceptSearchResponse() {
        this.totalHitCount = 0;
        this.resultStart = 0;
        this.concepts = Collections.emptyList();
    }

    public ConceptSearchResponse(final long totalHitCount,
                                 final int resultStart,
                                 final List<ConceptDTO> concepts) {
        this.totalHitCount = totalHitCount;
        this.resultStart = resultStart;
        this.concepts = concepts;
    }

    public long getTotalHitCount() {
        return totalHitCount;
    }

    public void setTotalHitCount(final long totalHitCount) {
        this.totalHitCount = totalHitCount;
    }

    public int getResultStart() {
        return resultStart;
    }

    public void setResultStart(final int resultStart) {
        this.resultStart = resultStart;
    }

    public List<ConceptDTO> getConcepts() {
        return concepts;
    }

    public void setConcepts(final List<ConceptDTO> concepts) {
        this.concepts = concepts;
    }
}
