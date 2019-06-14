package fi.vm.yti.terminology.api.frontend.searchdto;

import java.util.List;
import java.util.Map;

public class TerminologySearchResponse {
    private long totalHitCount;
    private int resultStart;
    private List<TerminologyDTO> terminologies;
    private Map<String, List<DeepSearchHitListDTO<?>>> deepHits;

    public TerminologySearchResponse(long totalHitCount, int resultStart, List<TerminologyDTO> terminologies, Map<String, List<DeepSearchHitListDTO<?>>> deepHits) {
        this.totalHitCount = totalHitCount;
        this.resultStart = resultStart;
        this.terminologies = terminologies;
        this.deepHits = deepHits;
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

    public List<TerminologyDTO> getTerminologies() {
        return terminologies;
    }

    public void setTerminologies(final List<TerminologyDTO> terminologies) {
        this.terminologies = terminologies;
    }

    public Map<String, List<DeepSearchHitListDTO<?>>> getDeepHits() {
        return deepHits;
    }

    public void setDeepHits(final Map<String, List<DeepSearchHitListDTO<?>>> deepHits) {
        this.deepHits = deepHits;
    }
}
