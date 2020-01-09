package fi.vm.yti.terminology.api.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.security.AuthorizationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/api/v1/admin/reindex")
@Tag(name = "Admin")
public class ReindexController {

    private final IndexElasticSearchService elasticSearchService;
    private final AuthenticatedUserProvider userProvider;

    private static final Logger logger = LoggerFactory.getLogger(ReindexController.class);

    @Autowired
    public ReindexController(IndexElasticSearchService elasticSearchService,
                             AuthenticatedUserProvider userProvider) {
        this.elasticSearchService = elasticSearchService;
        this.userProvider = userProvider;
    }

    @Operation(summary = "Request reindex", description = "Request complete reindex of terminologies and concepts")
    @ApiResponse(responseCode = "200", description = "String \"OK!\" after successful reindex")
    @ApiResponse(responseCode = "401", description = "If the caller is not logged in and a super user")
    @GetMapping(produces = TEXT_PLAIN_VALUE)
    public String reindex() {
        logger.info("GET /api/v1/admin/reindex");
        if (this.userProvider.getUser().isSuperuser()) {
        this.elasticSearchService.reindex();
        return "OK!";
        } else {
            throw new AuthorizationException("Super user rights required for reindex");
        }
    }
}
