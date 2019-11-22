package fi.vm.yti.terminology.api.index;

import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static fi.vm.yti.terminology.api.model.termed.NodeType.Concept;
import static fi.vm.yti.terminology.api.model.termed.NodeType.TerminologicalVocabulary;
import static fi.vm.yti.terminology.api.model.termed.NodeType.Vocabulary;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@RestController
public class NotificationController {

    private final IndexElasticSearchService elasticSearchService;

    private final Object lock = new Object();

    private static final List<NodeType> conceptTypes = singletonList(Concept);
    private static final List<NodeType> vocabularyTypes = asList(TerminologicalVocabulary, Vocabulary);

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    @Autowired
    public NotificationController(IndexElasticSearchService elasticSearchService) {
        this.elasticSearchService = elasticSearchService;
    }

    @RequestMapping("/private/v1/notify")
    public void notify(@RequestBody TermedNotification notification) {
        logger.info("/private/v1/notify requested with notification.user: " + notification.body.user + " and node identifier ids:");
        for (final Identifier ident: notification.body.nodes) {
            logger.info(ident.getId().toString());
        }

        synchronized(this.lock) {
            logger.debug("notify - acquired lock");

            Map<UUID, List<Identifier>> nodesByGraphId =
                    notification.body.nodes.stream().collect(Collectors.groupingBy(node -> node.getType().getGraph().getId()));

            for (Map.Entry<UUID, List<Identifier>> entries : nodesByGraphId.entrySet()) {

                UUID graphId = entries.getKey();
                List<Identifier> nodes = entries.getValue();

                logger.debug("notify - updating a set of " + nodes.size() + " for " + graphId.toString());
                List<UUID> vocabularies = extractIdsOfType(nodes, vocabularyTypes);
                List<UUID> concepts = extractIdsOfType(nodes, conceptTypes);

                switch (notification.type) {
                    case NodeSavedEvent:
                        this.elasticSearchService.updateIndexAfterUpdate(new AffectedNodes(graphId, vocabularies, concepts));
                        break;
                    case NodeDeletedEvent:
                        this.elasticSearchService.updateIndexAfterDelete(new AffectedNodes(graphId, vocabularies, concepts));
                        break;
                }
            }
            logger.debug("notify - releasing lock");
        }
    }

    private static @NotNull List<UUID> extractIdsOfType(@NotNull List<Identifier> nodes, @NotNull List<NodeType> types) {
        return nodes.stream()
                .filter(node -> types.contains(node.getType().getId()))
                .map(Identifier::getId)
                .collect(toList());
    }
}
