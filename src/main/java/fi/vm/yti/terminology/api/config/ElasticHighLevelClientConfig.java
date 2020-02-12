package fi.vm.yti.terminology.api.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
@PropertySource(value = "classpath", ignoreResourceNotFound = true)
public class ElasticHighLevelClientConfig {

    private static final int ES_CONNECTION_TIMEOUT = 300000;
    private static final int ES_RETRY_TIMEOUT = 60000;

    @Value("${search.host.url}")
    protected String elasticsearchHost;

    @Value("${search.host.port}")
    protected Integer elasticsearchPort;

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
        loggingFilter.setIncludeClientInfo(true);
        loggingFilter.setIncludeQueryString(true);
        loggingFilter.setIncludePayload(true);
        return loggingFilter;
    }

    @Bean
    protected RestHighLevelClient elasticSearchRestHighLevelClient() {
        final RestClientBuilder builder = RestClient.builder(
            new HttpHost(elasticsearchHost, elasticsearchPort, "http"))
            .setRequestConfigCallback(
                requestConfigBuilder -> requestConfigBuilder
                    .setConnectTimeout(ES_CONNECTION_TIMEOUT)
                    .setSocketTimeout(ES_CONNECTION_TIMEOUT))
            .setMaxRetryTimeoutMillis(ES_RETRY_TIMEOUT);
        return new RestHighLevelClient(builder);
    }
}
