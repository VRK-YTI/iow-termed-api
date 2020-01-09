package fi.vm.yti.terminology.api.migration.task;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.index.Vocabulary;
import fi.vm.yti.terminology.api.migration.AttributeIndex;
import fi.vm.yti.terminology.api.migration.DomainIndex;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;
import fi.vm.yti.terminology.api.model.termed.GenericDeleteAndSave;
import fi.vm.yti.terminology.api.model.termed.GenericDeleteModifyAndSave;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.Graph;
import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.model.termed.GraphId;
import fi.vm.yti.terminology.api.TermedContentType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

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

    // graph-id, Url
    private HashMap<UUID, String> graphUrlMapping = new HashMap<>();
    // vocabulary-id, Url
    private HashMap<UUID, String> vocabularyUrlMapping = new HashMap<>();

    V17_NewCommonMetaModelUpdate(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        // Modify base graph
        migrationService.updateTypes(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID, meta -> {
            // Go through text-attributes and add descriptions to known ids
            updateTypes(meta);
        });
        // update all the rest metamodels
        migrationService.updateTypes(VocabularyNodeType.TerminologicalVocabulary, meta -> {
            // Go through text-attributes and add descriptions to known ids
            updateTypes(meta);
        });

        // Delete concept-link YTI-330
        deleteConceptLinksFromGraphs();
        // TODO: fix this
        migrationService.deleteTypes(VocabularyNodeType.TerminologicalVocabulary, "ConceptLink");

        // Start data manipulation.
        List<Graph> graphs = migrationService.getGraphs();
        graphs.forEach(g -> {
            // Store id->uri for a while and then replace it with placeholder
            // for terminologies
            graphUrlMapping.put(g.getId(), g.getUri());
            addMetaToUri(g);
        });

        List<UUID> vocabularies = migrationService.findTerminologyIdList();
        vocabularies.forEach(id -> {
            GenericNode n = migrationService.getNode(id);
            if (n != null) {
                System.out.println(
                        "Vocabulary:" + n.getId() + " uri:" + n.getUri() + " graph:" + n.getType().getGraphId());
                UUID gid = n.getType().getGraphId();
                UUID terminologyId = n.getId();
                // If vocabulary graph id is not yet under main graph, update references
                if (DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID.compareTo(gid) != 0) {
                    // Just in case, generate new id:s for whole graph so they are unique.
                    logger.info("Call regenerate id:s for " + id);
                    migrationService.regeneratieIds(gid);
                    // add definedInScheme and update graph-id links
                    ModifyLinks(gid, terminologyId);
                    // Then delete original graph.
                    migrationService.deleteGraph(gid);
                } else {
                    logger.warn("Not terminology:" + n.getUri());
                }
            }
        });
    }

    /**
     * Originally each graph object contained Meta-model of the terminology. After
     * this it is managed under root graph so in 1.st phase terminology uri is moved
     * under vocabulary-object
     * 
     * @param g
     */
    private void addMetaToUri(Graph g) {
        Graph gr = migrationService.getGraph(g.getId());
        // Add postfix after original uri
        if (gr != null) {
            String uri = gr.getUri();
            if (uri != null && !uri.isEmpty() && uri.contains("http://uri.suomi.fi/terminology")
                    && !uri.endsWith("Meta")) {
                // terminology, replace it
                if (!uri.endsWith("/")) {
                    uri = uri.concat("/");
                }
                uri = uri.concat("Meta");
                gr.setUri(uri);
                logger.info("Replace graph URI for  <" + gr.getUri() + ">");
                migrationService.updateGraph(gr);
            }
        }
    }

    private void deleteConceptLinksFromGraphs() {
        logger.info("deleteConceptLinksFromGraphs");
        // go through all graphs and see whether there exist concept-links under it.
        List<Identifier> conceptLinkRefs = migrationService.getAllNamedReferences(null, "ConceptLink");
        if (conceptLinkRefs != null && conceptLinkRefs.size() > 0) {
            logger.info("Found conceptLinks count=" + conceptLinkRefs.size());
            List<UUID> idlist = conceptLinkRefs.stream().map(Identifier::getId).collect(Collectors.toList());
            logger.info("Remove conceptLink nodes, identifiers:" + idlist);
            idlist.forEach(i -> {
                logAndModifyConceptLink(i);
            });

            // Now remove links
            conceptLinkRefs.forEach(l -> {
                List<Identifier> removeList = new ArrayList<>();
                GenericNode gn = migrationService.getNode(l.getId());
                Identifier idf = null;
                if (gn != null) {
                    // logger.info("remove copntentLink from graph:" + gn.getType().getGraphId());
                    idf = new Identifier(l.getId(), gn.getType());
                    removeList.add(idf);
                    // Delete nodes
                    logger.debug("deleteConceptLinks graph:" + l.getType().getGraph().getId() + " l:" + l.getId());
                    migrationService.removeNodes(false, true, l.getType().getGraph().getId(), removeList);
                } else {
                    logger.info("deleteConceptLinks failed for graph:" + l.getType().getGraph().getId() + " l:"
                            + l.getId());
                }
            });
        }

    }

    private GenericNode modifyTerminologyLinks(GenericNode o) {
        // modify URI and code parts if not yet changed
        String uri = o.getUri();
        if (uri != null && !uri.isEmpty() && uri.contains("terminological-vocabulary")) {
            // Remove last part of the uri
            uri = uri.substring(0, uri.lastIndexOf("/"));
            o.setUri(uri);
            logger.debug("Replace terminology URL:" + uri);
            String code = uri.substring("http://uri.suomi.fi/terminology/".length());
            logger.info("code=" + code);
            o.setCode(code);
            // Remove code it uri is available
            o.setCode(null);
            // o.setUri(null);1
            // change under one graph
            o.getType().getGraph().setId(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
            // Remove dates
            o.setCreatedDate(null);
            o.setLastModifiedDate(null);
        }
        return o;
    }

    private GenericNode modifyConceptLinks(GenericNode o, UUID terminologyId) {
        // GenericNode gn = migrationService.getNode(o.getId());
        // Just replace all graph id's with root graph id.
        GenericNode gn = migrationService.replaceIdRef(o.getId(), o.getType().getGraphId(),
                DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        logger.debug("Replace graph references for <" + o.getUri() + "> id:" + o.getId() + " code:" + gn.getCode());
        gn.setDefinedInScheme(terminologyId);
        // remove uri
        // gn.setUri(null);
        // remove up code
        gn.setCode(null);
        // Remove dates
        gn.setCreatedDate(null);
        gn.setLastModifiedDate(null);
        return gn;
    }

    private GenericNode modifyTermLinks(GenericNode o) {
        // Just replace all graph id's with root graph id.
        GenericNode gn = migrationService.replaceIdRef(o.getId(), o.getType().getGraphId(),
                DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        logger.debug("Replace graph references for term <" + o.getUri() + "> id:" + o.getId() + " code:" + gn.getCode());
        gn.setCode(null);
        // Remove dates
        gn.setCreatedDate(null);
        gn.setLastModifiedDate(null);
        gn.setReferrers(null);
        return gn;
    }

    private GenericNode modifyCollectionLinks(GenericNode o) {
        // Just replace all graph id's with root graph id.
        GenericNode gn = migrationService.replaceIdRef(o.getId(), o.getType().getGraphId(),
                DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        logger.debug("Replace graph references for term <" + o.getUri() + "> id:" + o.getId() + " code:" + gn.getCode());
        gn.setCode(null);
        // Remove dates
        gn.setCreatedDate(null);
        gn.setLastModifiedDate(null);
        gn.setReferrers(null);
        return gn;
    }

    private void ModifyLinks(UUID graph, UUID terminologyId) {
        // Modified node list
        List<GenericNode> updateNodeList = new ArrayList<>();
        Long start = System.currentTimeMillis();
        List<GenericNode> nodes = migrationService.getAllNodes(graph);
        logger.info("Graph:" + graph + " NodeCount=" + nodes.size());
        nodes.forEach(o -> {
            if (o.getType().getId() == NodeType.TerminologicalVocabulary) {
                updateNodeList.add(modifyTerminologyLinks(o));                
            } else if (o.getType().getId() == NodeType.Concept) {
                updateNodeList.add(modifyConceptLinks(o, terminologyId));
            } else if (o.getType().getId() == NodeType.Term) {
                updateNodeList.add(modifyTermLinks(o));
            } else if (o.getType().getId() == NodeType.Collection) {
                updateNodeList.add(modifyCollectionLinks(o));
            }
        });

        logger.info("UpdateNodeList = " + updateNodeList.size());
        if (updateNodeList != null && !updateNodeList.isEmpty()) {
            logger.info("Update following nodes count:" + updateNodeList.size());
            migrationService.updateAndDeleteInternalNodes(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID,
                    new GenericDeleteModifyAndSave(Collections.<Identifier>emptyList(), updateNodeList, Collections.<GenericNode>emptyList()));
        }
        long end = System.currentTimeMillis();
        logger.info("Graph:" + graph.toString() + " update took " + (end - start) + "ms");
    }

    private void logAndModifyConceptLink(UUID i) {
        // logger.info("Fetch node:" + i);
        GenericNode n = migrationService.getNode(i);
        String targetUri = null;
        UUID targId = null;
        // Fetch source node and get target Uri
        if (n != null && (n.getProperties() != null && n.getProperties().get("targetId") != null)) {
            String targetId = n.getProperties().get("targetId").get(0).getValue();
            targId = UUID.fromString(targetId);
            if (targId != null) {
                GenericNode sn = migrationService.getNode(targId);
                if (sn != null) {
                    targetUri = sn.getUri();
                }
            }

            // Log Content-Links into the log for later fixing
            if (n != null) {
                List<Identifier> identifier = n.getReferrers().get("closeMatch");
                if (identifier != null) {
                    Identifier idf = identifier.get(0);
                    // Fetch source node
                    GenericNode sn = migrationService.getNode(idf.getId());
                    logger.info("concept-link reference closeMatch graph:" + idf.getType().getGraph().getId()
                            + " concept id:" + idf.getId() + " target-> uri:" + targetUri + " label="
                            + n.getProperties().get("prefLabel").get(0).getValue() + " targetId:" + targId.toString());
                }
                identifier = n.getReferrers().get("exactMatch");
                if (identifier != null) {
                    Identifier idf = identifier.get(0);
                    TypeId tid = new TypeId(NodeType.Concept, new GraphId(idf.getType().getGraph().getId()));
                    GenericNode sn = migrationService.getNode(tid, idf.getId());
                    if (targetUri == null) {
                        logger.error("Link error, concept-link reference exactMatch graph:"
                                + idf.getType().getGraph().getId() + " concept id:" + idf.getId() + " concept uri:"
                                + sn.getUri() + " target-> uri:" + targetUri + " label="
                                + n.getProperties().get("prefLabel").get(0).getValue() + " targetId:"
                                + targId.toString());

                    } else {
                        logger.info("concept-link reference exactMatch graph:" + idf.getType().getGraph().getId()
                                + " concept id:" + idf.getId() + " concept uri:" + sn.getUri() + " target-> uri:"
                                + targetUri + " label=" + n.getProperties().get("prefLabel").get(0).getValue()
                                + " targetId:" + targId.toString());
                    }
                }
                identifier = n.getReferrers().get("relatedMatch");
                if (identifier != null) {
                    Identifier idf = identifier.get(0);
                    GenericNode sn = migrationService.getNode(idf.getId());
                    logger.info("concept-link reference relatedMatch graph:" + idf.getType().getGraph().getId()
                            + " concept id:" + idf.getId() + " concept uri:" + sn.getUri() + " target-> uri:"
                            + targetUri + " label=" + n.getProperties().get("prefLabel").get(0).getValue()
                            + " targetId:" + targId.toString());
                }
            }
        }
    }

    /**
     * Add new used- and definedInScheme attributes
     * 
     * @param meta MetaNode to be updated
     * @return true if updated
     */
    private boolean updateTypes(MetaNode meta) {
        boolean rv = false;
        String domainName = meta.getDomain().getId().name();
        if (meta.isOfType(NodeType.TerminologicalVocabulary) || meta.isOfType(NodeType.Schema)) {
            if (meta.attributeExist("priority")) {
                logger.info("Remove priority attribute from metaID::" + meta.getId() + " Domain:"
                        + meta.getDomain().getGraphId().toString());
                meta.removeAttribute("priority");
            }
        } else if (meta.isOfType(NodeType.Concept) || meta.isOfType(NodeType.Schema)) {
            logger.info("Add relatedMatch and usedIn into the  metaID::" + meta.getId() + " Domain:"
                    + meta.getDomain().getGraphId().toString());
            // Add new used- and definedInScheme YTI-1159
            TypeId domain = meta.getDomain();
            if (!meta.attributeExist("usedInScheme")) {
                meta.addAttribute(AttributeIndex.usedInScheme(domain, 15));
            }
            if (!meta.attributeExist("definedInScheme")) {
                meta.addAttribute(AttributeIndex.definedInScheme(domain, 16));
            }
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