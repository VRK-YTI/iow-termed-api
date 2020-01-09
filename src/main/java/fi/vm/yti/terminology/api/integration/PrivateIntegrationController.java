package fi.vm.yti.terminology.api.integration;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.vm.yti.terminology.api.model.integration.ConceptSuggestionRequest;
import fi.vm.yti.terminology.api.model.integration.ConceptSuggestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/private/v1/integration")
@Tag(name = "Private/Integration")
public class PrivateIntegrationController {

    private final IntegrationService integrationService;

    private static final Logger logger = LoggerFactory.getLogger(PrivateIntegrationController.class);

    public PrivateIntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @Operation(summary = "Submit a concept suggestion", description = "Submit a concept suggestion and return created concept if successful")
    @ApiResponse(
        responseCode = "200",
        description = "Returns JSON with basic info of created suggestion, or an error string",
        content = { @Content(schema = @Schema(implementation = ConceptSuggestionResponse.class)) })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Concept suggestion as JSON")
    @PostMapping(path = "/terminology/conceptSuggestion", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<String> conceptSuggestion(HttpServletRequest req,
                                             @RequestBody ConceptSuggestionRequest incomingConcept) {
        logger.info("POST /private/v1/integration/terminology/conceptSuggestion from " + (req != null ? req.getRemoteHost() : "N/A"));
        return integrationService.handleConceptSuggestion(incomingConcept);
    }
}
