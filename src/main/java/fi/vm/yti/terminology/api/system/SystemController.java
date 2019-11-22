package fi.vm.yti.terminology.api.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiResponse;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemService systemService;
    private final ServiceUrls serviceUrls;
    private final boolean restrictFilterOptions;

    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);

    public SystemController(SystemService systemService,
                            ServiceUrls serviceUrls,
                            @Value("${front.restrictFilterOptions}") boolean restrictFilterOptions) {
        this.systemService = systemService;
        this.serviceUrls = serviceUrls;
        this.restrictFilterOptions = restrictFilterOptions;
    }

    /**
     * @return
     */
    @ApiResponse(code = 200, message = "Returns JSON with item counts. Terminologies, concepts")
    @RequestMapping(value = "/counts", method = GET, produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> countStatistics() {
        logger.info("GET /api/v1/system/counts requested");
        return systemService.countStatistics();
    }

    @RequestMapping(value = "/config", method = GET, produces = APPLICATION_JSON_VALUE)
    public Configuration getConfiguration() {
        logger.info("GET /api/v1/system/config requested");

        Configuration conf = new Configuration();
        conf.codeListUrl = this.serviceUrls.getCodeListUrl();
        conf.dataModelUrl = this.serviceUrls.getDataModelUrl();
        conf.commentsUrl = this.serviceUrls.getCommentsUrl();
        conf.groupmanagementUrl = this.serviceUrls.getGroupManagementUrl();
        conf.messagingEnabled = this.serviceUrls.getMessagingEnabled();
        conf.env = this.serviceUrls.getEnv();
        conf.restrictFilterOptions = this.restrictFilterOptions;

        return conf;
    }
}
