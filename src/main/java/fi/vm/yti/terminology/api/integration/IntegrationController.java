package fi.vm.yti.terminology.api.integration;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.terminology.api.frontend.FrontendElasticSearchService;
import fi.vm.yti.terminology.api.frontend.FrontendGroupManagementService;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.model.integration.ConceptSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/integration")
public class IntegrationController {

    private final FrontendTermedService termedService;
    private final IntegrationService integrationService;
    private final FrontendElasticSearchService elasticSearchService;
    private final FrontendGroupManagementService groupManagementService;
    private final AuthenticatedUserProvider userProvider;
    private final String namespaceRoot;
    private final String groupManagementUrl;
    private final boolean fakeLoginAllowed;

    private static final Logger logger = LoggerFactory.getLogger(IntegrationController.class);

    public IntegrationController(FrontendTermedService termedService,
                              IntegrationService integrationService,
                              FrontendElasticSearchService elasticSearchService,
                              FrontendGroupManagementService groupManagementService,
                              AuthenticatedUserProvider userProvider,
                              @Value("${namespace.root}") String namespaceRoot,
                              @Value("${groupmanagement.public.url}") String groupManagementUrl,
                              @Value("${fake.login.allowed:false}") boolean fakeLoginAllowed) {
        this.termedService = termedService;
        this.integrationService = integrationService;
        this.elasticSearchService = elasticSearchService;
        this.groupManagementService = groupManagementService;
        this.userProvider = userProvider;
        this.namespaceRoot = namespaceRoot;
        this.groupManagementUrl = groupManagementUrl;
        this.fakeLoginAllowed = fakeLoginAllowed;
    }

    @RequestMapping(value = "/vocabulary/{vocabularyId}/conceptSuggestion", method = POST, produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity  conceptSuggestion(@PathVariable("vocabularyId") String vocabularyId,
                                      @RequestBody ConceptSuggestion incomingConcept) {
        return integrationService.handleConceptSuggestion(vocabularyId,incomingConcept);
    }

}
