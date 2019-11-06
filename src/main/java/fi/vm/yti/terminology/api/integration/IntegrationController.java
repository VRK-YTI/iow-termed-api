package fi.vm.yti.terminology.api.integration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.util.Date;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.vm.yti.terminology.api.model.integration.ConceptSuggestionRequest;
import fi.vm.yti.terminology.api.model.integration.IntegrationContainerRequest;
import fi.vm.yti.terminology.api.model.integration.IntegrationResourceRequest;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@RestController
@RequestMapping("/integration")
public class IntegrationController {

    private final IntegrationService integrationService;

    private static final Logger logger = LoggerFactory.getLogger(IntegrationController.class);

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    /**
     * 
     * @param vocabularyId
     * @param after
     * @param incomingConcept
     * @return
     */
    @ApiResponse(code = 200, message = "Returns JSON with Vocabulary-list, pref-labels, descriptions, status and modified date")
    @RequestMapping(value = "/terminology/conceptSuggestion", method = POST, produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<String> conceptSuggestion(@Context HttpServletRequest req,
            @RequestBody ConceptSuggestionRequest incomingConcept) {
        if (req != null) {
            logger.debug("ConceptSuggestion incoming reaquest from" + req.getRemoteHost());
        }
        return integrationService.handleConceptSuggestion(incomingConcept);
    }

    @ApiResponse(code = 200, message = "Returns JSON with Vocabulary-list.")
    @RequestMapping(value = "/containers", method = GET, produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> containers(
            @ApiParam(value = "Language code for sorting results.") @RequestParam(value = "language", required = false) String language,
            @ApiParam(value = "Pagination parameter for page size.") @RequestParam(value = "pageSize", required = true, defaultValue = "0") int pageSize,
            @ApiParam(value = "Pagination parameter for start index.") @RequestParam(value = "from", required = false, defaultValue = "0") int from,
            @ApiParam(value = "Status enumerations in CSL format.") @RequestParam(value = "status", required = false) Set<String> statusEnum,
            @ApiParam(value = "URI of the requested containers in CSL format.") @RequestParam(value = "uri", required = false) Set<String> uri,
            @ApiParam(value = "Textual search query") @RequestParam(value = "searchTerm", required = false) String searchTerm,
            @ApiParam(value = "Boolean whether to include incomplete states into the response.") @RequestParam(value = "includeIncomplete", required = false) boolean incomplete,
            @ApiParam(value = "User organizations filtering parameter, for filtering incomplete resources") @RequestParam(value = "includeIncompleteFrom", required = false) Set<String> includeIncompleteFrom,
            @ApiParam(value = "Before date filtering parameter, results will be containers with modified date before this ISO 8601 formatted date string.") @RequestParam(value = "before", required = false) String before,
            @ApiParam(value = "After date filtering parameter, results will be containers with modified date after this ISO 8601 formatted date string.") @RequestParam(value = "after", required = false) String after) {
        if (logger.isDebugEnabled()) {
            logger.debug("integrationController.containers.GET");
        }
        // Create and fill parameter object
        IntegrationContainerRequest containersRequest = new IntegrationContainerRequest();
        containersRequest.setLanguage(language);
        containersRequest.setPageSize(pageSize);
        containersRequest.setPageFrom(from);
        containersRequest.setStatus(statusEnum);
        containersRequest.setUri(uri);
        containersRequest.setSearchTerm(searchTerm);
        containersRequest.setBefore(before);
        containersRequest.setAfter(after);
        containersRequest.setIncludeIncomplete(incomplete);
        containersRequest.setIncludeIncompleteFrom(includeIncompleteFrom);
        return integrationService.handleContainers(containersRequest);
    }

    @ApiResponse(code = 200, message = "Returns JSON with filtered Concept-list if excluded URIS are given as parameter")
    @RequestMapping(value = "/containers", method = POST, produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<String> containers(@RequestBody IntegrationContainerRequest containersRequest) {
        if (logger.isDebugEnabled()) {
            logger.debug("integrationController.containers.POST");
        }
        return integrationService.handleContainers(containersRequest);
    }

    @RequestMapping(value = "/resources", method = GET, produces = APPLICATION_JSON_VALUE)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Returns JSON with Concept-list."),
            @ApiResponse(code = 400, message = "Invalid model supplied"),
            @ApiResponse(code = 404, message = "Service not found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    ResponseEntity<String> resources(
            @ApiParam(value = "Container URL") @RequestParam(value = "container", required = false) String container,
            @ApiParam(value = "Required URI list in CSL format") @RequestParam(value = "uri", required = false) Set<String> uri,
            @ApiParam(value = "Language") @RequestParam(value = "language", required = false) String lang,
            @ApiParam(value = "Queried statuses in CSL format.") @RequestParam(value = "status", required = false) Set<String> status,
            @ApiParam(value = "Boolean whether to include resources from all incomplete conainers in the response.") @RequestParam(value = "includeIncomplete", required = false) boolean includeIncomplete,
            @ApiParam(value = "User organizations filtering parameter, for filtering resources from incomplete containers") @RequestParam(value = "includeIncompleteFrom", required = false) Set<String> includeIncompleteFrom,
            @ApiParam(value = "Before date filtering parameter, results will be resources with modified date before this ISO 8601 formatted date string.") @RequestParam(value = "before", required = false) String before,
            @ApiParam(value = "After date filtering parameter, results will be resources with modified date after this ISO 8601 formatted date string.") @RequestParam(value = "after", required = false) String after,
            @ApiParam(value = "Exclude filtering parameter, for ") @RequestParam(value = "filter", required = false) Set<String> filter,
            @ApiParam(value = "Textual search query") @RequestParam(value = "searchTerm", required = false) String searchTerm,
            @ApiParam(value = "Pagesize") @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @ApiParam(value = "From") @RequestParam(value = "from", required = false) Integer from) {

        if (logger.isDebugEnabled()) {
            logger.debug("integrationController.resources");
        }
        IntegrationResourceRequest request = new IntegrationResourceRequest();
        request.setContainer(container);
        request.setLanguage(lang);
        request.setStatus(status);
        request.setBefore(before);
        request.setFilter(filter);
        request.setAfter(after);
        request.setSearchTerm(searchTerm);
        request.setPageSize(pageSize);
        request.setPageFrom(from);
        request.setIncludeIncomplete(includeIncomplete);
        request.setIncludeIncompleteFrom(includeIncompleteFrom);
        request.setUri(uri);
        return integrationService.handleResources(request);
    }

    @ApiResponse(code = 200, message = "Returns JSON with filtered Concept-list if excluded URIS are given")
    @RequestMapping(value = "/resources", method = POST, produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<String> resources(@RequestBody IntegrationResourceRequest resourceRequest) {
        if (logger.isDebugEnabled()) {
            logger.debug("integrationController.resources.POST");
        }
        return integrationService.handleResources(resourceRequest);
    }
}
