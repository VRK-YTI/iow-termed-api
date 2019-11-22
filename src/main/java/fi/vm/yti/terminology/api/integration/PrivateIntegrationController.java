package fi.vm.yti.terminology.api.integration;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.vm.yti.terminology.api.model.integration.ConceptSuggestionRequest;
import io.swagger.annotations.ApiResponse;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/private/v1/integration")
public class PrivateIntegrationController {

    private final IntegrationService integrationService;

    private static final Logger logger = LoggerFactory.getLogger(PrivateIntegrationController.class);

    public PrivateIntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    /**
     * @param incomingConcept
     * @return
     */
    @ApiResponse(code = 200, message = "Returns JSON with basic info of created suggestion, or an error string")
    @RequestMapping(value = "/terminology/conceptSuggestion", method = POST, produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<String> conceptSuggestion(@Context HttpServletRequest req,
                                             @RequestBody ConceptSuggestionRequest incomingConcept) {
        logger.info("POST /private/v1/integration/terminology/conceptSuggestion from " + (req != null ? req.getRemoteHost() : "N/A"));
        return integrationService.handleConceptSuggestion(incomingConcept);
    }
}
