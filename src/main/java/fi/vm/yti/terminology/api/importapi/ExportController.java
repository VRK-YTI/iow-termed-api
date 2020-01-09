package fi.vm.yti.terminology.api.importapi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.model.termed.Graph;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/api/v1/export")
@Tag(name = "Import-Export")
public class ExportController {

    private final FrontendTermedService termedService;
    private final ExportService exportService;

    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);

    public ExportController(FrontendTermedService termedService,
                            ExportService exportService) {
        this.termedService = termedService;
        this.exportService = exportService;
    }

    /**
     * export/{vocabularyID}/ Toteutetaan proxy api termed node-trees api:lle, jolle
     * voi antaa parametrina ainakin seuraavat formaatit: application/json
     * text/turtle application/rdf+xml
     * <p>
     * API polku esim: /export/{uuid}?accept=... Mahdollista rakentaa tyyppi export
     * polkuun esim: /export/type/Concept/
     *
     * @param terminologyId
     * @return
     */
    @Operation(summary = "Export a terminology", description = "Export requested terminology in stated format")
    @ApiResponse(responseCode = "200", description = "Requested terminology exported in the requested format")
    @GetMapping(path = "/{terminologyID}", produces = { APPLICATION_JSON_VALUE, "application/rdf+xml", "text/turtle" })
    ResponseEntity<String> export(
        @Parameter(description = "Terminology identifier (UUID/URI)") @PathVariable("terminologyID") Object terminologyId,
        @Parameter(description = "Export format JSON, RDF, TURTLE.", example = "JSON") @RequestParam String format) {
        logger.debug("ExportController uuid:" + terminologyId + " format:" + format);

        ResponseEntity<String> re = null;
        UUID id = null;
        // Try to cast incoming as UUID and if fails, assume it is Code ie. name of the
        // vocabulary
        try {
            id = UUID.fromString((String) terminologyId);
        } catch (IllegalArgumentException ex) {
            id = this.resolveCode((String) terminologyId);
        } catch (Exception e) {
            logger.error("Error fetching vocabulary id", e.getMessage());
            e.printStackTrace();
        }
        // Id resolved, go fetch data
        if (id != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("ExportController uuid:" + id.toString());
            }
            if (format.equalsIgnoreCase("JSON")) {
                re = exportService.getJSON(id);
            } else if (format.equalsIgnoreCase("rdf")) {
                re = exportService.getRDF(id);
            } else {
                re = exportService.getTXT(id);
            }
        } else {
            re = new ResponseEntity<>("{}", HttpStatus.NOT_FOUND);
        }
        return re;
    }

    @Operation(summary = "Export certain node types", description = "Export requested node types in stated format from a terminology")
    @ApiResponse(responseCode = "200", description = "Requested nodes exported in the requested format")
    @GetMapping(path = "/{terminologyID}/type/{nodeType}", produces = { APPLICATION_JSON_VALUE, "application/rdf+xml", "text/turtle" })
    ResponseEntity<String> export(
        @Parameter(description = "Terminology identifier (UUID/URI)") @PathVariable("terminologyID") Object terminologyId,
        @Parameter(description = "Type of requested nodes. (Concept, Collection, Term)", example = "Concept") @PathVariable("nodeType") String nodeType,
        @Parameter(description = "Export format JSON, RDF, TURTLE.", example = "JSON") @RequestParam String format) {
        if (logger.isDebugEnabled()) {
            logger.debug("ID:" + terminologyId + " node=" + nodeType + " format:" + format);
        }
        UUID id = null;
        // Try to cast incoming as UUID and if fails, assume it is Code ie. name of the
        // vocabulary
        try {
            id = UUID.fromString((String) terminologyId);
        } catch (IllegalArgumentException ex) {

            id = this.resolveCode((String) terminologyId);
        } catch (Exception e) {
            logger.error("Error fetching vocabulary id", e.getMessage());
            id = null;
        }

        ResponseEntity<String> re = null;
        // Id resolved, go fetch data
        if (id != null) {
            if (logger.isDebugEnabled()) {
                logger.error("ExportController uuid");
            }
            if (format.equalsIgnoreCase("JSON")) {
                re = exportService.getJSON(id, nodeType);
            } else if (format.equalsIgnoreCase("rdf")) {
                re = exportService.getRDF(id, nodeType);
            } else {
                re = exportService.getTXT(id, nodeType);
            }
        } else {
            re = new ResponseEntity<>("{}", HttpStatus.NOT_FOUND);
        }
        return re;
    }

    private UUID resolveCode(String code) {
        UUID rv = null;
        List<Graph> graphs = termedService.getGraphs();
        List<Graph> matchingGraphs = graphs.stream().filter(o -> o.getCode().equalsIgnoreCase((String) code))
            .collect(Collectors.toList());

        List<UUID> idlist = new ArrayList<>();
        matchingGraphs.forEach(g -> {
            idlist.add(g.getId());
        });
        if (!idlist.isEmpty() && idlist.size() == 1) {
            rv = idlist.get(0);
        } else {
            logger.error("Several matches for " + code);
        }
        return rv;
    }
}
