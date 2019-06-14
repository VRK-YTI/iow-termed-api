package fi.vm.yti.terminology.api.frontend.searchdto;

public class TerminologySearchRequest {

    private String query;
    private boolean searchConcepts;
    private String prefLang;
    private Integer pageSize;
    private Integer pageFrom;

    public String getQuery() {
        return query;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public boolean isSearchConcepts() {
        return searchConcepts;
    }

    public void setSearchConcepts(final boolean searchConcepts) {
        this.searchConcepts = searchConcepts;
    }

    public String getPrefLang() {
        return prefLang;
    }

    public void setPrefLang(final String prefLang) {
        this.prefLang = prefLang;
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

    @Override
    public String toString() {
        return "TerminologySearchRequest{" +
            "query='" + query + '\'' +
            ", searchConcepts=" + searchConcepts +
            ", prefLang=" + prefLang +
            ", pageSize=" + pageSize +
            ", pageFrom=" + pageFrom +
            '}';
    }
}
