package fi.vm.yti.terminology.api.importapi;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fi.vm.yti.terminology.api.frontend.FrontendTermedService;

@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

    private final ImportService importService;
    public ImportController(FrontendTermedService termedService,
                            ImportService importService) {
        this.importService = importService;
    }
    @RequestMapping(value = "ntrf/{vocabulary}", method = RequestMethod.POST , consumes = "multipart/form-data")
    ResponseEntity<String> importTerms(@PathVariable("vocabulary") UUID vocabularyId,
                               @RequestParam("file") MultipartFile file){
        return importService.handleNtrfDocumentAsync("ntrf",vocabularyId, file);
    }

    @RequestMapping(value = "/status/{jobtoken}", method = GET, produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> getStatus(@PathVariable("jobtoken") UUID id,
                             @RequestParam(value="full", required = false, defaultValue = "false") boolean full) {
        return importService.getStatus(id, full);
    }
}
