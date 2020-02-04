package fi.vm.yti.terminology.api.integration;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.util.StdDateFormat;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.security.AuthorizationException;
import fi.vm.yti.security.YtiUser;
import fi.vm.yti.terminology.api.model.integration.ConceptSuggestionRequest;
import fi.vm.yti.terminology.api.model.integration.ConceptSuggestionResponse;
import fi.vm.yti.terminology.api.model.integration.ContainersResponse;
import fi.vm.yti.terminology.api.model.integration.IntegrationContainerRequest;
import fi.vm.yti.terminology.api.model.integration.IntegrationResourceRequest;
import fi.vm.yti.terminology.api.model.integration.PrivateConceptSuggestionRequest;
import fi.vm.yti.terminology.api.model.integration.ResourcesResponse;
import fi.vm.yti.terminology.api.model.integration.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/api/v1/integration")
@Tag(name = "Integration")
public class IntegrationController {

    public final static class ContainerResponseWrapper extends ResponseWrapper<ContainersResponse> {

    }

    public final static class ResourceResponseWrapper extends ResponseWrapper<ResourcesResponse> {

    }

    private final IntegrationService integrationService;
    private final AuthenticatedUserProvider userProvider;

    private static final Logger logger = LoggerFactory.getLogger(IntegrationController.class);

    public IntegrationController(IntegrationService integrationService,
                                 AuthenticatedUserProvider userProvider) {
        this.integrationService = integrationService;
        this.userProvider = userProvider;
    }

    @Operation(summary = "Submit a concept suggestion", description = "Submit a concept suggestion and return created concept if successful")
    @ApiResponse(
        responseCode = "200",
        description = "Returns JSON with basic info of created suggestion, or an error string",
        content = { @Content(schema = @Schema(implementation = ConceptSuggestionResponse.class)) })
    @ApiResponse(responseCode = "401", description = "If the caller is not logged in")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Concept suggestion as JSON")
    @PostMapping(path = "/terminology/conceptSuggestion", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<String> conceptSuggestion(HttpServletRequest req,
                                             @RequestBody ConceptSuggestionRequest incomingConcept) {
        logger.info("POST /api/v1/integration/terminology/conceptSuggestion from "
            + (req != null ? req.getRemoteHost() : "N/A"));
        YtiUser user = userProvider.getUser();
        if (!user.isAnonymous()) {
            return integrationService.handleConceptSuggestion(new PrivateConceptSuggestionRequest(incomingConcept, user.getId().toString()));
        } else {
            throw new AuthorizationException("Making concept suggestions require authorized user");
        }
    }

    @Operation(summary = "Get terminology list", description = "List or search for containers, i.e., the terminologies. See also the alternative POST request variant.")
    @ApiResponse(
        responseCode = "200",
        description = "Returns JSON with terminology list",
        content = { @Content(schema = @Schema(implementation = ContainerResponseWrapper.class)) })
    @ApiResponse(responseCode = "400", description = "Invalid request parameters supplied")
    @ApiResponse(responseCode = "404", description = "Service not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping(path = "/containers", produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> containers(
        @Parameter(description = "Language code for sorting results", example = "fi") @RequestParam(required = false) String language,
        @Parameter(description = "Pagination parameter for page size", example = "100") @RequestParam(defaultValue = "10000") int pageSize,
        @Parameter(description = "Pagination parameter for start index", example = "0") @RequestParam(required = false, defaultValue = "0") int from,
        @Parameter(description = "Status enumerations in CSL format", example = "DRAFT,VALID") @RequestParam(required = false) Set<String> status,
        @Parameter(description = "URI of the requested containers in CSL format") @RequestParam(required = false) Set<String> uri,
        @Parameter(description = "Textual search query") @RequestParam(required = false) String searchTerm,
        @Parameter(description = "Boolean whether to ignore contributor checks and include all incomplete content in the response") @RequestParam(required = false) boolean includeIncomplete,
        @Parameter(description = "List of organization UUIDs to use in contributor checks for incomplete content") @RequestParam(required = false) Set<String> includeIncompleteFrom,
        @Parameter(description = "Before date filtering parameter, results will be containers with modified date before this ISO 8601 formatted date string") @RequestParam(required = false) String before,
        @Parameter(description = "After date filtering parameter, results will be containers with modified date after this ISO 8601 formatted date string") @RequestParam(required = false) String after) {

        if (logger.isDebugEnabled()) {
            logger.debug("integrationController.containers.GET");
        }
        // Create and fill parameter object
        IntegrationContainerRequest containersRequest = new IntegrationContainerRequest();
        containersRequest.setLanguage(language);
        containersRequest.setPageSize(pageSize);
        containersRequest.setPageFrom(from);

        // Change status into upper case
        if (status != null) {
            status = status.stream().map(String::toUpperCase).collect(Collectors.toSet());
        }
        containersRequest.setStatus(status);

        containersRequest.setUri(uri);
        containersRequest.setSearchTerm(searchTerm);
        containersRequest.setBefore(before);
        containersRequest.setAfter(after);
        containersRequest.setIncludeIncomplete(includeIncomplete);
        containersRequest.setIncludeIncompleteFrom(includeIncompleteFrom);
        String validationResult = validateContainersInput(containersRequest);
        if (validationResult != null) {
            return new ResponseEntity<>("{\"errorMessage\":\"" + validationResult + "\"}", HttpStatus.BAD_REQUEST);
        }
        return integrationService.handleContainers(containersRequest);
    }

    @Operation(summary = "Get terminology list", description = "List or search for containers, i.e., the terminologies. See also the alternative GET request variant.")
    @ApiResponse(
        responseCode = "200",
        description = "Returns JSON with terminology list",
        content = { @Content(schema = @Schema(implementation = ContainerResponseWrapper.class)) })
    @ApiResponse(responseCode = "400", description = "Invalid request parameters supplied")
    @ApiResponse(responseCode = "404", description = "Service not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Search parameters as JSON request object. Contains mostly equivalent options as GET variant query parameters.",
        required = true
    )
    @PostMapping(path = "/containers", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<String> containers(@RequestBody IntegrationContainerRequest containersRequest) {
        if (logger.isDebugEnabled()) {
            logger.debug("integrationController.containers.POST");
        }

        String validationResult = validateContainersInput(containersRequest);
        if (validationResult != null) {
            return new ResponseEntity<>("{\"errorMessage\":\"" + validationResult + "\"}", HttpStatus.BAD_REQUEST);
        }

        return integrationService.handleContainers(containersRequest);
    }

    @Operation(summary = "Get concept list", description = "List or search for resources, i.e., the concepts. See also the alternative POST request variant.")
    @ApiResponse(
        responseCode = "200",
        description = "Returns JSON with concept list",
        content = { @Content(schema = @Schema(implementation = ResourceResponseWrapper.class)) })
    @ApiResponse(responseCode = "400", description = "Invalid request parammeters suplied")
    @ApiResponse(responseCode = "404", description = "Service not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping(path = "/resources", produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> resources(
        @Parameter(description = "Container (terminology) URL list. If not set then list/search from all containers.") @RequestParam(required = false) Set<String> container,
        @Parameter(description = "Resource (concept) uri list. If set then return only given resources.") @RequestParam(required = false) Set<String> uri,
        @Parameter(description = "Resource (concept) uri list. If set then exclude given resources from results.") @RequestParam(required = false) Set<String> filter,
        @Parameter(description = "Language code for sorting results", example = "fi") @RequestParam(required = false) String language,
        @Parameter(description = "Status enumerations in CSL format", example = "DRAFT,VALID") @RequestParam(required = false) Set<String> status,
        @Parameter(description = "Boolean whether to ignore contributor checks and include all incomplete content in the response") @RequestParam(required = false) boolean includeIncomplete,
        @Parameter(description = "List of organization UUIDs to use in contributor checks for incomplete content. Checks are done on container level.") @RequestParam(required = false) Set<String> includeIncompleteFrom,
        @Parameter(description = "Before date filtering parameter, results will be resources with modified date before this ISO 8601 formatted date string") @RequestParam(required = false) String before,
        @Parameter(description = "After date filtering parameter, results will be resources with modified date after this ISO 8601 formatted date string") @RequestParam(required = false) String after,
        @Parameter(description = "Textual search query") @RequestParam(required = false) String searchTerm,
        @Parameter(description = "Pagination parameter for page size", example = "100") @RequestParam(defaultValue = "10000") int pageSize,
        @Parameter(description = "Pagination parameter for start index", example = "0") @RequestParam(required = false, defaultValue = "0") int from) {

        if (logger.isDebugEnabled()) {
            logger.debug("integrationController.resources");
        }
        IntegrationResourceRequest request = new IntegrationResourceRequest();
        request.setContainer(container);
        request.setLanguage(language);

        // Change status into upper case
        if (status != null) {
            status = status.stream().map(String::toUpperCase).collect(Collectors.toSet());
        }
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
        String validationResult = validateResourcesInput(request);
        if (validationResult != null) {
            return new ResponseEntity<>("{\"errorMessage\":\"" + validationResult + "\"}", HttpStatus.BAD_REQUEST);
        }
        return integrationService.handleResources(request);
    }

    @Operation(summary = "Get concept list", description = "List or search for resources, i.e., the concepts. See also the alternative GET request variant.")
    @ApiResponse(
        responseCode = "200",
        description = "Returns JSON with concept list",
        content = { @Content(schema = @Schema(implementation = ResourceResponseWrapper.class)) })
    @ApiResponse(responseCode = "400", description = "Invalid request parammeters suplied")
    @ApiResponse(responseCode = "404", description = "Service not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Search parameters as JSON request object. Contains mostly equivalent options as GET variant query parameters.",
        required = true
    )
    @PostMapping(path = "/resources", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    ResponseEntity<String> resources(@RequestBody IntegrationResourceRequest resourceRequest) {
        if (logger.isDebugEnabled()) {
            logger.debug("integrationController.resources.POST");
        }
        String validationResult = validateResourcesInput(resourceRequest);
        if (validationResult != null) {
            return new ResponseEntity<>("{\"errorMessage\":\"" + validationResult + "\"}", HttpStatus.BAD_REQUEST);
        }

        return integrationService.handleResources(resourceRequest);
    }

    /**
     * Check whether incoming request parameters are valid Mainly checks that date
     * strings are acceptable
     *
     * @param containersRequest
     * @return
     */
    private String validateContainersInput(IntegrationContainerRequest containersRequest) {
        ArrayList<String> rv = new ArrayList<>();
        if (containersRequest.getLanguage() != null) {
            try {
                Locale l = LocaleUtils.toLocale(containersRequest.getLanguage());
                System.out.println("Locale present");
            } catch (IllegalArgumentException iae) {
                rv.add("Illegal language:" + containersRequest.getLanguage());
            }
        }
        if (containersRequest.getPageSize() != null && containersRequest.getPageSize() < 0) {
            rv.add("Illegal pageSize:" + containersRequest.getPageSize());
        }
        if (containersRequest.getPageFrom() != null && containersRequest.getPageFrom() < 0) {
            rv.add("Illegal pageFrom:" + containersRequest.getPageFrom());
        }
        if (containersRequest.getStatus() != null) {
            Set<String> statusSet = containersRequest.getStatus();
            Set<String> validStatuses = Stream
                .of("INCOMPLETE", "SUPERSEDED", "RETIRED", "INVALID", "VALID", "SUGGESTED", "DRAFT")
                .collect(Collectors.toSet());
            if (!validStatuses.containsAll(statusSet)) {
                rv.add("Unknown status:" + statusSet.toString());
            }
        }
        if (containersRequest.getAfter() != null) {
            final StdDateFormat dateFormat = new StdDateFormat();
            try {
                Date d = dateFormat.parse(containersRequest.getAfter());
            } catch (ParseException e) {
                rv.add("Parsing After date from string failed: " + containersRequest.getAfter());
            }
        }
        if (containersRequest.getBefore() != null) {
            final StdDateFormat dateFormat = new StdDateFormat().withColonInTimeZone(true);
            try {
                Date d = dateFormat.parse(containersRequest.getBefore());
            } catch (ParseException e) {
                rv.add("Parsing Before date from string failed: " + containersRequest.getBefore());
            }
        }
        if (rv.isEmpty()) {
            return null;
        }
        return rv.toString();
    }

    /**
     * Check whether incoming request parameters are valid Mainly checks that date
     * strings are acceptable
     *
     * @param containersRequest
     * @return
     */
    private String validateResourcesInput(IntegrationResourceRequest containersRequest) {
        ArrayList<String> rv = new ArrayList<>();
        if (containersRequest.getLanguage() != null) {
            try {
                Locale l = LocaleUtils.toLocale(containersRequest.getLanguage());
                System.out.println("Locale present");
            } catch (IllegalArgumentException iae) {
                rv.add("Illegal language:" + containersRequest.getLanguage());
            }
        }
        if (containersRequest.getPageSize() != null && containersRequest.getPageSize() < 0) {
            rv.add("Illegal pageSize:" + containersRequest.getPageSize());
        }
        if (containersRequest.getPageFrom() != null && containersRequest.getPageFrom() < 0) {
            rv.add("Illegal pageFrom:" + containersRequest.getPageFrom());
        }
        if (containersRequest.getStatus() != null) {
            Set<String> statusSet = containersRequest.getStatus();
            Set<String> validStatuses = Stream
                .of("INCOMPLETE", "SUPERSEDED", "RETIRED", "INVALID", "VALID", "SUGGESTED", "DRAFT")
                .collect(Collectors.toSet());
            if (!validStatuses.containsAll(statusSet)) {
                rv.add("Unknown status:" + statusSet.toString());
            }
        }
        if (containersRequest.getAfter() != null) {
            final StdDateFormat dateFormat = new StdDateFormat().withColonInTimeZone(true);
            try {
                Date d = dateFormat.parse(containersRequest.getAfter());
            } catch (ParseException e) {
                rv.add("Parsing After date from string failed: " + containersRequest.getAfter());
            }
        }
        if (containersRequest.getBefore() != null) {
            final StdDateFormat dateFormat = new StdDateFormat().withColonInTimeZone(true);
            try {
                Date d = dateFormat.parse(containersRequest.getBefore());
            } catch (ParseException e) {
                rv.add("Parsing Before date from string failed: " + containersRequest.getBefore());
            }
        }
        if (rv.isEmpty()) {
            return null;
        }
        return rv.toString();
    }
}
