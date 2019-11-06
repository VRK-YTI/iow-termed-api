package fi.vm.yti.terminology.api.migration.task;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.AttributeIndex;
import fi.vm.yti.terminology.api.migration.DomainIndex;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.Graph;
import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.model.termed.GraphId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static fi.vm.yti.terminology.api.migration.DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID;

/**
 * Migration for YTI-1159, New common metamodel ToDo:
 *
 * * LinkNode poisto ja siihen liittyvät assiaatiot + Poistetaan
 * createTerminologicalConceptLinkMeta() + Poistetaan
 * ReferenceIndex.relatedMatch + Muutetaan viittaamaan Concept-nodeen:
 * ReferenceIndex.exactMatch ja ReferenceIndex.closeMatch
 *
 *
 * Concept nodelle usedInScheme joka viittaa sanastonodeen. Syntyy silloin kun
 * käsite on "lainattu" toiseen sanastoon
 *
 * PrefLabel: "Käytössä sanastossa" / "Used in scheme"
 *
 * Id: usedInScheme
 *
 * URI: skos:inScheme
 *
 *
 * * Concept nodelle definedInScheme joka viittaa sanastonodeen. Syntyy
 * käsitettä luotaessa
 *
 * PrefLabel: "Määritelty sanastossa" / "Defined in scheme"
 *
 * Id: definedInScheme
 *
 * URI: skos:inScheme
 * 
 * Poistetaan asiasanastot
 */
@Component
public class V17_NewCommonMetaModelUpdate implements MigrationTask {
    private static Logger logger = LoggerFactory.getLogger(V17_NewCommonMetaModelUpdate.class);
    private final MigrationService migrationService;

    // Match id -> new id used when changing graph-id -> terminology-id
    private HashMap<UUID, UUID> idMapping = new HashMap<>();
    // Match Uri -> new uri used when changing graph-uri -> terminology-uri.
    private HashMap<String, String> uriMapping = new HashMap<>();

    // graph-id, Url
    private HashMap<UUID, String> graphUrlMapping = new HashMap<>();
    // vocabulary-id, Url
    private HashMap<UUID, String> vocabularyUrlMapping = new HashMap<>();

    V17_NewCommonMetaModelUpdate(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        migrationService.updateTypes(VocabularyNodeType.TerminologicalVocabulary, meta -> {
            // Go through text-attributes and add descriptions to known ids
            updateTypes(meta);
        });

        // Delete concept-link YTI-330
        deleteConceptLinksFromGraphs();
        // TODO: fix this
        migrationService.deleteTypes(VocabularyNodeType.TerminologicalVocabulary, "ConceptLink");

        // Start data manipulation.
        List<UUID> vocabularies = migrationService.findTerminologyIdList();
        vocabularies.forEach(id -> {
            GenericNode n = migrationService.getNode(id);
            System.out.println("Vocabulary:" + n.getId() + " uri:" + n.getUri() + " graph:" + n.getType().getGraphId());
            vocabularyUrlMapping.put(n.getId(), n.getUri());
            idMapping.put(n.getType().getGraphId(), n.getId());
        });

        // lisätään definedInScheme
        System.out.println("Vocabulary mapping:" + vocabularyUrlMapping);
        UUID graph = UUID.fromString("b55e33e1-7544-474d-990e-58c53b3fa690");
        addDefinedInScheme(graph);
        // System.exit(-1);
    }

    private void deleteConceptLinksFromGraphs() {
        logger.info("deleteConceptLinksFromGraphs");
        // go through all graphs and see whether there exist concept-links under it.
        List<Identifier> conceptLinkRefs = migrationService.getAllNamedReferences(null, "ConceptLink");
        if (conceptLinkRefs != null && conceptLinkRefs.size() > 0) {
            logger.info("Found conceptLinks count=" + conceptLinkRefs.size());
            List<UUID> idlist = conceptLinkRefs.stream().map(Identifier::getId).collect(Collectors.toList());
            logger.info("RemoveNodes identifiers:" + idlist);
            idlist.forEach(i -> {
                logConceptLink(i);
            });

            // Now remove links
            conceptLinkRefs.forEach(l -> {
                List<Identifier> removeList = new ArrayList<>();
                removeList.add(l);
                // Delete nodes
                migrationService.removeNodes(false, true, l.getType().getGraph().getId(), removeList);
            });
        }
    }

    private void addDefinedInScheme(UUID graph) {
        List<GenericNode> nodes = migrationService.getAllNodes(graph);
        nodes.forEach(o -> {
            GenericNode gn = migrationService.getNode(o.getId());
            if (o.getType().getId() == NodeType.ConceptLink) {
                logger.info("O=ConceptLink, continue");
            } else {
                // Use DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID as new root graph
                gn.getType().getGraph().setId(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
                if (o.getType().getId() == NodeType.TerminologicalVocabulary) {
                    System.out.println("VocabularyNode, change node if");
                } else if (o.getType().getId() == NodeType.Concept) {

                    // Change all concepts and terms under default 1 graph. We use
                    // DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID as root
                    gn.getType().getGraph().setId(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);

                    // update concept and add there definedInScheme element with vocabulary id
                    Map<String, List<Identifier>> refList = gn.getReferences();
                    if (refList.get("definedInSchema") == null) {
                        // Add vocabulary reference
                        Identifier ref = new Identifier(idMapping.get(o.getType().getGraphId()),
                                DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_DOMAIN);
                        List<Identifier> rList = new ArrayList<>();
                        rList.add(ref);
                        refList.put("definedInScheme", rList);
                    }
                    JsonUtils.prettyPrintJson(gn);
                }
                logger.info("Replace from item:" + o.getId() + " <" + o.getUri() + "> graph id:"
                        + o.getType().getGraphId() + " -> id:" + idMapping.get(o.getType().getGraphId()));
                // System.out.println("After");
                // JsonUtils.prettyPrintJson(gn);
                // push it back into the termed
            }
        });
    }

    private void logConceptLink(UUID i) {
        // logger.info("Fetch node:" + i);
        GenericNode n = migrationService.getNode(i);
        String targetUri = null;
        // Fetch source node
        if (n.getProperties() != null && n.getProperties().get("targetId") != null) {
            String targetId = n.getProperties().get("targetId").get(0).getValue();
            UUID targId = UUID.fromString(targetId);
            if (targId != null) {
                GenericNode sn = migrationService.getNode(targId);
                if (sn != null) {
                    targetUri = sn.getUri();
                } else {
                    logger.error("Concept link reference  fault. id:" + targetId + " does not exist");
                }
            }
        }

        if (n != null) {
            List<Identifier> identifier = n.getReferrers().get("closeMatch");
            if (identifier != null) {
                Identifier idf = identifier.get(0);
                // Fetch source node
                GenericNode sn = migrationService.getNode(idf.getId());
                logger.info("concept-link reference closeMatch graph:" + idf.getType().getGraph().getId()
                        + " concept id:" + idf.getId() + " target-> uri:" + targetUri + " label="
                        + n.getProperties().get("prefLabel").get(0).getValue());
            }
            identifier = n.getReferrers().get("exactMatch");
            if (identifier != null) {
                Identifier idf = identifier.get(0);
                TypeId tid = new TypeId(NodeType.Concept, new GraphId(idf.getType().getGraph().getId()));
                GenericNode sn = migrationService.getNode(tid, idf.getId());
                if (targetUri == null) {
                    logger.error(
                            "Link error, concept-link reference exactMatch graph:" + idf.getType().getGraph().getId()
                                    + " concept id:" + idf.getId() + " concept uri:" + sn.getUri() + " target-> uri:"
                                    + targetUri + " label=" + n.getProperties().get("prefLabel").get(0).getValue());

                } else {
                    logger.info("concept-link reference exactMatch graph:" + idf.getType().getGraph().getId()
                            + " concept id:" + idf.getId() + " concept uri:" + sn.getUri() + " target-> uri:"
                            + targetUri + " label=" + n.getProperties().get("prefLabel").get(0).getValue());
                }
                // JsonUtils.prettyPrintJson(n);
            }
            identifier = n.getReferrers().get("relatedMatch");
            if (identifier != null) {
                Identifier idf = identifier.get(0);
                // Fetch source node
                GenericNode sn = migrationService.getNode(idf.getId());
                logger.info("concept-link reference relatedMatch graph:" + idf.getType().getGraph().getId()
                        + " concept id:" + idf.getId() + " concept uri:" + sn.getUri() + " target-> uri:" + targetUri
                        + " label=" + n.getProperties().get("prefLabel").get(0).getValue());
            }

            // JsonUtils.prettyPrintJson(n);
        }
    }

    /**
     * Add new used- and definedInScheme attributes
     * 
     * @param meta MetaNode to be updated
     * @return true if updated
     */
    boolean updateTypes(MetaNode meta) {
        boolean rv = false;
        String domainName = meta.getDomain().getId().name();
        if (meta.isOfType(NodeType.TerminologicalVocabulary)) {
            if (meta.attributeExist("priority")) {
                logger.info("Remove priority attribute from metaID::" + meta.getId() + " Domain:"
                        + meta.getDomain().getGraphId().toString());
                JsonUtils.prettyPrintJson(meta);
                meta.removeAttribute("priority");
            }
        } else if (meta.isOfType(NodeType.Concept)) {
            logger.info("Add relatedMatch and usedIn into the  metaID::" + meta.getId() + " Domain:"
                    + meta.getDomain().getGraphId().toString());
            // Add new used- and definedInScheme YTI-1159
            TypeId domain = meta.getDomain();
            meta.addAttribute(AttributeIndex.usedInScheme(domain, 15));
            meta.addAttribute(AttributeIndex.definedInScheme(domain, 16));
            // Remove reference-attribute relatedMatch because ConceptLinkMeta is retired
            if (meta.attributeExist("relatedMatch")) {
                logger.info("Remove relatedMatch from Meta");
                meta.removeReference("relatedMatch");
            }
            if (meta.attributeExist("usedIn")) {
                logger.info("Remove usedIn from Meta");
                meta.removeReference("usedIn");
            }
            rv = true;
        }
        return rv;
    }

}