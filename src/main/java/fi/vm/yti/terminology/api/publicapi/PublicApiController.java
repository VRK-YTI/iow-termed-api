package fi.vm.yti.terminology.api.publicapi;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public")
public class PublicApiController {

    private static final Logger logger = LoggerFactory.getLogger(PublicApiController.class);
    private final PublicApiTermedService termedService;
    private final PublicApiElasticSearchService publicApiElasticSearchService;

    public PublicApiController(PublicApiTermedService termedService,
                               PublicApiElasticSearchService publicApiElasticSearchService) {
        this.termedService = termedService;
        this.publicApiElasticSearchService = publicApiElasticSearchService;
    }

    @Operation(summary = "Get list of all terminologies", description = "Get list of all terminologies", deprecated = true)
    @ApiResponse(responseCode = "200", description = "Terminology list as a JSON array")
    @GetMapping(path = "/vocabularies", produces = APPLICATION_JSON_VALUE)
    List<PublicApiVocabulary> getVocabularyList() {
        logger.info("GET /vocabularies requested");
        return termedService.getVocabularyList();
    }

    @Operation(summary = "Search for concepts", description = "Perform index search for concepts", deprecated = true)
    @ApiResponse(responseCode = "200", description = "Search result concept list as a JSON array")
    @GetMapping(value = "/searchconcept", produces = APPLICATION_JSON_VALUE)
    List<PublicApiConcept> searchConceptWithStatus(@Parameter(description = "Serch term for elastic search") @RequestParam(required = false, defaultValue = "") String searchTerm,
                                                   @Parameter(description = "Terminology ID. If missing search concepts from all terminologies.") @RequestParam(required = false) String vocabularyId,
                                                   @Parameter(description = "Status for filtering. If missing, show all.") @RequestParam(required = false) String status,
                                                   @Parameter(description = "Language for filtering. If missing, search labels in any language-") @RequestParam(required = false) String language) {

        logger.info("GET /searchconcept requested");
        return publicApiElasticSearchService.searchConcept(searchTerm, vocabularyId, status, language);
    }
}
