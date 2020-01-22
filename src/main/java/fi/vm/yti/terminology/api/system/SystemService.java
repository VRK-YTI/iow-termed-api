package fi.vm.yti.terminology.api.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.util.Parameters;
import static org.springframework.http.HttpMethod.GET;

@Service
public class SystemService {

    private static final Logger logger = LoggerFactory.getLogger(SystemService.class);
    private final TermedRequester termedRequester;
    private final ObjectMapper objectMapper;

    @Autowired
    public SystemService(final TermedRequester termedRequester,
                         final ObjectMapper objectMapper) {
        this.termedRequester = termedRequester;
        this.objectMapper = objectMapper;
    }

    ResponseEntity<String> countStatistics(boolean full) {

        if (logger.isDebugEnabled()) {
            logger.debug("GET /count requested.");
        }

        int terminologies = countTerminologies();
        int concepts = countConcepts();
        if (full) {
            String terminologyStatistics = countStatistics();
            return new ResponseEntity<>("{ \"terminologyCount\":" + terminologies + ", \"conceptCount\":" + concepts
                + ", \"statistics\":[" + terminologyStatistics + " ]}", HttpStatus.OK);
        } else {
            return new ResponseEntity<>(
                "{ \"terminologyCount\":" + terminologies + ", \"conceptCount\":" + concepts + " }", HttpStatus.OK);
        }
    }

    private int countTerminologies() {
        int rv = 0;
        String url = "/node-count?where=type.id:TerminologicalVocabulary";
        String count = termedRequester.exchange(url, GET, Parameters.empty(), String.class);
        logger.info("countTerminologies rv=" + count);
        if (count != null) {
            rv = Integer.parseInt(count);
        }
        return rv;
    }

    private int countConcepts() {
        int rv = 0;
        String url = "/node-count/?where=type.id:Concept";
        String count = termedRequester.exchange(url, GET, Parameters.empty(), String.class);
        logger.info("countConcepts rv=" + count);
        if (count != null) {
            rv = Integer.parseInt(count);
        }
        return rv;
    }

    private int countConcepts(UUID terminologyId) {
        int rv = 0;
        // http://localhost:9102/api/node-count?select=*&where=references.definedInScheme.id:6610ec2d-3b87-11ea-9fee-85b8cab3faae

        String url = "/node-count?where=references.definedInScheme.id:" + terminologyId;
        System.out.println("countConcepts:"+url);
        String count = termedRequester.exchange(url, GET, Parameters.empty(), String.class);
        logger.info("countConcepts rv=" + count);
        if (count != null) {
            rv = Integer.parseInt(count);
        }
        return rv;
    }

    private String countStatistics() {
        String rv = null;
        logger.info("countStatistics");
        List<String> statistics = new ArrayList<>();
        String url = "/node-trees?select=id,uri&where=type.id:TerminologicalVocabulary";
        String terminologies = termedRequester.exchange(url, GET, Parameters.empty(), String.class);
        if (terminologies != null) {
            try {
                iduri[] ids = objectMapper.readValue(terminologies, iduri[].class);
                for (iduri o : ids) {
                    System.out.println("uri:" + o.getUri() + ",  id:" + o.getId());
                    statistics.add("{\"uri\":"+"\"" + o.getUri() + "\", \"count\":" + countConcepts(o.getId()) + "}");
                    logger.info("countConcepts for " + o.getUri() + " conceptCount:" + countConcepts(o.getId()));
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            rv = statistics.toString();
        }
        return rv;
    }

    private static class iduri {

        UUID id;
        String uri;

        // for jackson
        private iduri() {
            this(null, null);
        }

        public iduri(UUID id,
                     String uri) {
            this.id = id;
            this.uri = uri;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public UUID getId() {
            return id;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }
    }
}
