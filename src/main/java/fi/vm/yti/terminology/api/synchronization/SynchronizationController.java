package fi.vm.yti.terminology.api.synchronization;

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
@RequestMapping(value = "/api/v1/admin/synchronize")
@Tag(name = "Admin")
public class SynchronizationController {

    private final SynchronizationService synchronizationService;
    private final AuthenticatedUserProvider userProvider;

    private static final Logger logger = LoggerFactory.getLogger(SynchronizationController.class);

    @Autowired
    public SynchronizationController(SynchronizationService synchronizationService,
                                     AuthenticatedUserProvider userProvider) {
        this.synchronizationService = synchronizationService;
        this.userProvider = userProvider;
    }

    @Operation(summary = "Request group resync", description = "Request synchronization with YTI Group Management Service")
    @ApiResponse(responseCode = "200", description = "String \"OK!\" after successful synchronization")
    @ApiResponse(responseCode = "401", description = "If the caller is not logged in and a super user")
    @GetMapping(produces = TEXT_PLAIN_VALUE)
    public String synchronize() {
        logger.info("GET /api/v1/admin/synchronize requested");
        if (this.userProvider.getUser().isSuperuser()) {
        synchronizationService.synchronize();
        return "OK!";
        } else {
            throw new AuthorizationException("Super user rights required for synchronize");
        }
    }
}
