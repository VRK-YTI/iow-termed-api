package fi.vm.yti.terminology.api.resolve;

import fi.vm.yti.terminology.api.TermedContentType;
import fi.vm.yti.terminology.api.model.termed.NodeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

@Controller
@RequestMapping("/api")
public class ResolveController {
    private static final Logger logger = LoggerFactory.getLogger(ResolveController.class);

    private final ResolveService urlResolverService;
    private final String applicationUrl;

    @Autowired
    ResolveController(ResolveService urlResolverService, @Value("${application.public.url}") String applicationUrl) {
        this.urlResolverService = urlResolverService;
        this.applicationUrl = applicationUrl;
    }

    @GetMapping("/resolve")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<String> resolve(@RequestParam String uri, @RequestParam(required = false) String format,
            @RequestHeader("Accept") String acceptHeader) {

        logger.debug("Resolving URI: " + uri);

        // Check whether uri is syntactically valid.
        try {
            URI u = new URI(uri);
            URL ur = u.toURL();
        } catch(URISyntaxException uex){
            return new ResponseEntity<>(uex.getMessage(), HttpStatus.BAD_REQUEST);               
        } catch (MalformedURLException mex) {
            return new ResponseEntity<>(mex.getMessage(), HttpStatus.BAD_REQUEST);               
        }
        // ok, continue into the resolver
        try {
            ResolvedResource resource = urlResolverService.resolveResource(uri);
            ResolvableContentType contentType = ResolvableContentType.fromString(format, acceptHeader);
            // String responseValue = "redirect:" + applicationUrl + formatPath(resource,
            // contentType)
            // + (contentType.isHandledByFrontend() || StringUtils.isEmpty(format) ? "" :
            // "&format=" + format);
            // return new ResponseEntity<>(responseValue, HttpStatus.OK);
            String responseValue = applicationUrl + formatPath(resource, contentType)
                    + (contentType.isHandledByFrontend() || StringUtils.isEmpty(format) ? "" : "&format=" + format);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setLocation(new URI(responseValue));
            return new ResponseEntity<>(httpHeaders, HttpStatus.SEE_OTHER);
        } catch (Exception ex) {        
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    private static String formatPath(ResolvedResource resource, ResolvableContentType contentType) {

        if (contentType.isHandledByFrontend()) {
            switch (resource.getType()) {
            case VOCABULARY:
                return "/concepts/" + resource.getGraphId();
            case CONCEPT:
                return "/concepts/" + resource.getGraphId() + "/concept/" + resource.getId();
            case COLLECTION:
                return "/concepts/" + resource.getGraphId() + "/collection/" + resource.getId();
            default:
                throw new RuntimeException("Unsupported type: " + resource.getType());
            }
        } else {
            switch (resource.getType()) {
            case VOCABULARY:
                return "/api/vocabulary?graphId=" + resource.getGraphId();
            case CONCEPT:
                return "/api/concept?graphId=" + resource.getGraphId() + "&id=" + resource.getId();
            case COLLECTION:
                return "/api/collection?graphId=" + resource.getGraphId() + "&id=" + resource.getId();
            default:
                throw new RuntimeException("Unsupported type: " + resource.getType());
            }
        }
    }

    @GetMapping("/vocabulary")
    @ResponseBody
    public String getVocabulary(@RequestParam UUID graphId, @RequestParam(required = false) String format,
            @RequestHeader("Accept") String acceptHeader) {
        return urlResolverService.getResource(graphId, asList(NodeType.Vocabulary, NodeType.TerminologicalVocabulary),
                TermedContentType.fromString(format, acceptHeader), null);
    }

    @GetMapping("/concept")
    @ResponseBody
    public String getConcept(@RequestParam UUID graphId, @RequestParam UUID id,
            @RequestParam(required = false) String format, @RequestHeader("Accept") String acceptHeader) {
        return urlResolverService.getResource(graphId, singletonList(NodeType.Concept),
                TermedContentType.fromString(format, acceptHeader), id);
    }

    @GetMapping("/collection")
    @ResponseBody
    public String getCollection(@RequestParam UUID graphId, @RequestParam UUID id,
            @RequestParam(required = false) String format, @RequestHeader("Accept") String acceptHeader) {
        return urlResolverService.getResource(graphId, singletonList(NodeType.Collection),
                TermedContentType.fromString(format, acceptHeader), id);
    }
}
