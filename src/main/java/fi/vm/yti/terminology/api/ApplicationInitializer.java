package fi.vm.yti.terminology.api;

import fi.vm.yti.migration.MigrationInitializer;
import fi.vm.yti.terminology.api.exception.ElasticEndpointException;
import fi.vm.yti.terminology.api.exception.TermedEndpointException;
import fi.vm.yti.terminology.api.index.BrokenTermedDataLinkException;
import fi.vm.yti.terminology.api.index.IndexElasticSearchService;
import fi.vm.yti.terminology.api.index.IndexTermedService;
import fi.vm.yti.terminology.api.synchronization.SynchronizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class ApplicationInitializer {

    private final int RETRIES = 10;
    private final IndexElasticSearchService elasticSearchService;
    private final IndexTermedService termedApiService;
    private final SynchronizationService synchronizationService;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private String hookId;

    @Value("${notify.hook.url}")
    private String NOTIFY_HOOK_URL;

    @Autowired
    public ApplicationInitializer(IndexElasticSearchService elasticSearchService,
                                  IndexTermedService termedApiService,
                                  SynchronizationService synchronizationService,
                                  MigrationInitializer migrationInitializer /* XXX: dependency to enforce init order */) {
        this.elasticSearchService = elasticSearchService;
        this.termedApiService = termedApiService;
        this.synchronizationService = synchronizationService;
    }

    @PostConstruct
    public void onInit() throws InterruptedException {

        for (int retryCount = 1; retryCount <= RETRIES; retryCount++) {
            try {
                log.info("Initializing (attempt " + retryCount + "/" + RETRIES + ")");

                synchronizationService.synchronize();

                elasticSearchService.initIndexes();

                if (!NOTIFY_HOOK_URL.isEmpty()) {
                    registerNotificationUrl(NOTIFY_HOOK_URL);
                }

                return;

            } catch (TermedEndpointException | ElasticEndpointException | BrokenTermedDataLinkException e) {
                log.warn("Initialization failed (" + retryCount + ")", e);
                Thread.sleep(30000);
            }
        }

        throw new RuntimeException("Cannot initialize");
    }

    @PreDestroy
    public void onDestroy() {
        if (!NOTIFY_HOOK_URL.isEmpty()) {
            this.unRegisterNotificationUrl();
        }
    }

    private void registerNotificationUrl(String url) {
        log.info("Clearing existing change listeners from Termed API");
        try {
            termedApiService.deleteChangeListeners();
        } catch(Exception e) {
            log.error("Could not drop existing Termed change listeners", e);
        }

        log.info("Registering change listener to Termed API");

        hookId = termedApiService.registerChangeListener(url);

        if (hookId != null) {
            log.info("Registered change listener to termed API with id: " + hookId);
        } else {
            log.warn("Unable to register change listener to termed API");
        }
    }

    private void unRegisterNotificationUrl() {
        if (hookId != null) {
            log.info("Deleting change listener from termed API having id: " + hookId);
            termedApiService.deleteChangeListener(hookId);
            log.info("Deleted change listener successfully");
        }
    }
}
