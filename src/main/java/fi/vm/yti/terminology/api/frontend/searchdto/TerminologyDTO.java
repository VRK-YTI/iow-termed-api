package fi.vm.yti.terminology.api.frontend.searchdto;

import java.util.List;
import java.util.Map;

public class TerminologyDTO extends TerminologySimpleDTO {

    private Map<String, String> description;
    private List<InformationDomainDTO> informationDomains;
    private List<OrganizationDTO> contributors;

    public TerminologyDTO(final String id,
                          final String code,
                          final String uri,
                          final String status,
                          final Map<String, String> label,
                          final Map<String, String> description,
                          final List<InformationDomainDTO> informationDomains,
                          final List<OrganizationDTO> contributors) {
        super(id, code, uri, status, label);

        this.description = description;
        this.informationDomains = informationDomains;
        this.contributors = contributors;
    }

    public List<InformationDomainDTO> getInformationDomains() {
        return informationDomains;
    }

    public void setInformationDomains(final List<InformationDomainDTO> informationDomains) {
        this.informationDomains = informationDomains;
    }

    public List<OrganizationDTO> getContributors() {
        return contributors;
    }

    public void setContributors(final List<OrganizationDTO> contributors) {
        this.contributors = contributors;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public void setDescription(final Map<String, String> description) {
        this.description = description;
    }
}
