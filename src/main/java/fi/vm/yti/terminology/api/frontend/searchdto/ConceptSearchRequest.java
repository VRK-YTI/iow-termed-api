package fi.vm.yti.terminology.api.frontend.searchdto;

import java.util.Arrays;

import org.elasticsearch.search.sort.SortOrder;

public class ConceptSearchRequest {

    public enum SortBy {PREF_LABEL, MODIFIED}

    public enum SortDirection {
        ASC(SortOrder.ASC), DESC(SortOrder.DESC);
        private final SortOrder esOrder;

        SortDirection(SortOrder esOrder) {
            this.esOrder = esOrder;
        }

        public SortOrder getEsOrder() {
            return esOrder;
        }
    }

    public static class Options {
        public enum OperationMode {ALL_INCOMPLETE, CONTRIBUTOR_CHECK, NO_INCOMPLETE};

        public OperationMode operationMode;
        public Boolean onlyCheckTerminologyState;
        public Boolean doNotCheckTerminologyStateForGivenTerminologies;
        public Boolean doNotCheckTerminologyStateForGivenConcepts;
        public Boolean doNotCheckConceptStateForGivenConcepts;

        @Override
        public String toString() {
            return "Options{" +
                "operationMode=" + operationMode +
                ", onlyCheckTerminologyState=" + onlyCheckTerminologyState +
                ", doNotCheckTerminologyStateForGivenTerminologies=" + doNotCheckTerminologyStateForGivenTerminologies +
                ", doNotCheckTerminologyStateForGivenConcepts=" + doNotCheckTerminologyStateForGivenConcepts +
                ", doNotCheckConceptStateForGivenConcepts=" + doNotCheckConceptStateForGivenConcepts +
                '}';
        }
    }

    private String query;
    private String[] conceptId;
    private String[] terminologyId;
    private String[] notInTerminologyId;
    private String[] broaderConceptId;
    private Boolean onlyTopConcepts;
    private String[] status;
    private SortBy sortBy;
    private SortDirection sortDirection;
    private String sortLanguage;
    private Integer pageSize;
    private Integer pageFrom;
    private Boolean highlight;
    private Options options;

    public String getQuery() {
        return query;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public String[] getConceptId() {
        return conceptId;
    }

    public void setConceptId(final String[] conceptId) {
        this.conceptId = conceptId;
    }

    public String[] getTerminologyId() {
        return terminologyId;
    }

    public void setTerminologyId(final String[] terminologyId) {
        this.terminologyId = terminologyId;
    }

    public String[] getNotInTerminologyId() {
        return notInTerminologyId;
    }

    public void setNotInTerminologyId(final String[] notInTerminologyId) {
        this.notInTerminologyId = notInTerminologyId;
    }

    public String[] getBroaderConceptId() {
        return broaderConceptId;
    }

    public void setBroaderConceptId(final String[] broaderConceptId) {
        this.broaderConceptId = broaderConceptId;
    }

    public Boolean getOnlyTopConcepts() {
        return onlyTopConcepts;
    }

    public void setOnlyTopConcepts(final Boolean onlyTopConcepts) {
        this.onlyTopConcepts = onlyTopConcepts;
    }

    public String[] getStatus() {
        return status;
    }

    public void setStatus(final String[] status) {
        this.status = status;
    }

    public SortBy getSortBy() {
        return sortBy;
    }

    public void setSortBy(final SortBy sortBy) {
        this.sortBy = sortBy;
    }

    public SortDirection getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(final SortDirection sortDirection) {
        this.sortDirection = sortDirection;
    }

    public String getSortLanguage() {
        return sortLanguage;
    }

    public void setSortLanguage(final String sortLanguage) {
        this.sortLanguage = sortLanguage;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(final Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageFrom() {
        return pageFrom;
    }

    public void setPageFrom(final Integer pageFrom) {
        this.pageFrom = pageFrom;
    }

    public Boolean getHighlight() {
        return highlight;
    }

    public void setHighlight(final Boolean highlight) {
        this.highlight = highlight;
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(final Options options) {
        this.options = options;
    }

    @Override
    public String toString() {
        return "ConceptSearchRequest{" +
            "query='" + query + '\'' +
            ", conceptId=" + Arrays.toString(conceptId) +
            ", terminologyId=" + Arrays.toString(terminologyId) +
            ", notInTerminologyId=" + Arrays.toString(notInTerminologyId) +
            ", broaderConceptId=" + Arrays.toString(broaderConceptId) +
            ", onlyTopConcepts=" + onlyTopConcepts +
            ", status=" + Arrays.toString(status) +
            ", sortBy=" + sortBy +
            ", sortDirection=" + sortDirection +
            ", sortLanguage='" + sortLanguage + '\'' +
            ", pageSize=" + pageSize +
            ", pageFrom=" + pageFrom +
            ", highlight=" + highlight +
            ", options=" + options +
            '}';
    }
}
