package fi.vm.yti.terminology.api.migration.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.DomainIndex;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.migration.ReferenceIndex;
import fi.vm.yti.terminology.api.model.termed.GenericDeleteModifyAndSave;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.Graph;
import fi.vm.yti.terminology.api.model.termed.GraphId;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;
import fi.vm.yti.terminology.api.util.JsonUtils;

/**
 * Migration for YTI-1159, New common metamodel ToDo:
 * <p>
 * * LinkNode poisto ja siihen liittyvät assiaatiot + Poistetaan
 * createTerminologicalConceptLinkMeta() + Poistetaan
 * ReferenceIndex.relatedMatch +
 * Muutetaan viittaamaan Concept-nodeen:
 * ReferenceIndex.exactMatch ja ReferenceIndex.closeMatch
 * <p>
 * <p>
 * Concept nodelle usedInScheme joka viittaa sanastonodeen. Syntyy silloin kun
 * käsite on "lainattu" toiseen sanastoon
 * <p>
 * PrefLabel: "Käytössä sanastossa" / "Used in scheme"
 * <p>
 * Id: usedInScheme
 * <p>
 * URI: skos:inScheme
 * <p>
 * <p>
 * * Concept nodelle definedInScheme joka viittaa sanastonodeen. Syntyy
 * käsitettä luotaessa
 * <p>
 * PrefLabel: "Määritelty sanastossa" / "Defined in scheme"
 * <p>
 * Id: definedInScheme
 * <p>
 * URI: skos:inScheme
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
        // Add defined- and usedInScheme
        addSchemesToRootMeta();
        // update all metamodels
        migrationService.updateTypes(VocabularyNodeType.TerminologicalVocabulary, meta -> {
            updateTypes(meta);
        });

        // Delete concept-link YTI-330
        deleteConceptLinksFromGraphs();
        // delete concept-link items
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
                logger.info(
                    "Terminology:" + n.getId() + " uri:" + n.getUri() + " graph:" + n.getType().getGraphId());
                UUID gid = n.getType().getGraphId();
                UUID terminologyId = n.getId();
                // If vocabulary graph id is not yet under main graph, update references
                if (DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID.compareTo(gid) != 0) {
                    // Just in case, generate new id:s for whole graph so they are unique.
                    logger.info("Call regenerate id:s for " + id);
                    migrationService.regeneratieIds(gid);
                    // Graph id changed, so resolve new one before modifying links. Important for definedInScheme relation
                    GenericNode gn = migrationService.getNode(n.getUri());
                    if(gn != null && gn.getId() != null){
                        terminologyId = gn.getId();
                        // add definedInScheme and update graph-id links
                        ModifyLinks(gid, terminologyId);
                    } else {
                        logger.error("Can't find new id for "+n.getUri());
                    }

                    // Then delete original graph.
                    migrationService.deleteGraph(gid);
                } else {
                    logger.warn("Not terminology:" + n.getUri());
                }
            }
        });

        // After migration, modify meta again. This time we  have only root node so
        modifyRootMeta();
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

    private GenericNode modifyConceptLinks(GenericNode o,
                                           UUID terminologyId) {
        // GenericNode gn = migrationService.getNode(o.getId());
        // Just replace all graph id's with root graph id.
        GenericNode gn = migrationService.replaceIdRef(o.getId(), o.getType().getGraphId(),
            DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        logger.debug("Replace graph references for <" + o.getUri() + "> id:" + o.getId() + " code:" + gn.getCode());
        // Add definedInScheme reference link
        gn.addDefinedInScheme(terminologyId, new TypeId(NodeType.TerminologicalVocabulary, new GraphId(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID)));
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
        logger.debug(
            "Replace graph references for term <" + o.getUri() + "> id:" + o.getId() + " code:" + gn.getCode());
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
        logger.debug(
            "Replace graph references for term <" + o.getUri() + "> id:" + o.getId() + " code:" + gn.getCode());
        gn.setCode(null);
        // Remove dates
        gn.setCreatedDate(null);
        gn.setLastModifiedDate(null);
        gn.setReferrers(null);
        return gn;
    }

    private void ModifyLinks(UUID graph,
                             UUID terminologyId) {
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
//            JsonUtils.prettyPrintJson(updateNodeList);
            migrationService.updateAndDeleteInternalNodes(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID,
                new GenericDeleteModifyAndSave(Collections.<Identifier>emptyList(), updateNodeList,
                    Collections.<GenericNode>emptyList()));
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
        logger.info("UpdateTypes  metaID::" + domainName + " Domain:"
            + meta.getDomain().getGraphId().toString());
        if (meta.isOfType(NodeType.TerminologicalVocabulary) || meta.isOfType(NodeType.Schema)) {
            if (meta.attributeExist("priority")) {
                logger.info("Remove priority attribute from metaID::" + meta.getId() + " Domain:"
                    + meta.getDomain().getGraphId().toString());
                meta.removeAttribute("priority");
            }
        } else if (meta.isOfType(NodeType.Concept)) {
            // Add new used- and definedInScheme YTI-1159
            TypeId domain = meta.getDomain();

            if (!meta.referenceExist("usedInScheme")) {
                meta.addReference(ReferenceIndex.usedInScheme(domain, 18));
                logger.info("Adding usedInScheme");
            } else {
                logger.info("usedInScheme exist");
                meta.addReference(ReferenceIndex.usedInScheme(domain, 18));
            }

            if (!meta.referenceExist("definedInScheme")) {
                meta.addReference(ReferenceIndex.definedInScheme(domain, 19));
                logger.info("Adding definedInScheme");
            } else {
                logger.info("definedInScheme exist");
                meta.addReference(ReferenceIndex.definedInScheme(domain, 19));
            }
            rv = true;
        }
        return rv;
    }

    private void modifyRootMeta() {

        List<MetaNode> metaList = migrationService.getTypes(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        List<MetaNode> metaModel = new ArrayList<>();

        logger.info("Meta nodes:");
        metaList.forEach(meta -> {
            String domainName = meta.getDomain().getId().name();
            logger.info("ModifyRootMeta  metaID:" + domainName);
            if (meta.isOfType(NodeType.Concept)) {
                logger.info("Add relatedMatch and usedIn into the  metaID:" + meta.getId());
                TypeId domain = meta.getDomain();
                // Remove reference-attribute relatedMatch because ConceptLinkMeta is retired
                if (meta.referenceExist("relatedMatch")) {
                    logger.info("Remove root relatedMatch from Meta");
                    meta.removeReference("relatedMatch");
                }
                // Add it again.
                meta.addReference(ReferenceIndex.relatedMatch(domain, 16));

                if (meta.referenceExist("exactMatch")) {
                    logger.info("Remove root exactMatch from Meta");
                    meta.removeReference("exactMatch");
                }
                // Add it again.
                meta.addReference(ReferenceIndex.exactMatch(domain, 17));

                if (meta.referenceExist("closeMatch")) {
                    logger.info("Remove root closeMatch from Meta");
                    meta.removeReference("closeMatch");
                }
                // Add it again.
                meta.addReference(ReferenceIndex.closeMatch(domain, 18));

                if (!meta.referenceExist("usedInScheme")) {
                    meta.addReference(ReferenceIndex.usedInScheme(domain, 18));
                    logger.info("Adding usedInScheme");
                } else {
                    logger.info("usedInScheme exist");
                    meta.addReference(ReferenceIndex.usedInScheme(domain, 18));
                }

                if (!meta.referenceExist("definedInScheme")) {
                    meta.addReference(ReferenceIndex.definedInScheme(domain, 19));
                    logger.info("Adding definedInScheme");
                } else {
                    logger.info("definedInScheme exist");
                    meta.addReference(ReferenceIndex.definedInScheme(domain, 19));
                }

            }
            // Add all other meta types except concept  link
            if (!meta.isOfType(NodeType.ConceptLink)) {
                metaModel.add(meta);
            }
        });
        migrationService.updateTypes(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID, metaModel);
    }

    private void addSchemesToRootMeta() {

        List<MetaNode> metaList = migrationService.getTypes(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        List<MetaNode> metaModel = new ArrayList<>();

        logger.info("Meta nodes:");
        metaList.forEach(meta -> {
            String domainName = meta.getDomain().getId().name();
            logger.info("ModifyRootMeta  metaID:" + domainName);
            TypeId domain = meta.getDomain();
            if (meta.isOfType(NodeType.Concept)) {
                logger.info("Add definedIn- and usedInScheme into the  metaID:" + meta.getId());
                if (!meta.referenceExist("usedInScheme")) {
                    meta.addReference(ReferenceIndex.usedInScheme(domain, 18));
                    logger.info("Adding usedInScheme");
                } else {
                    logger.info("usedInScheme exist");
                    meta.addReference(ReferenceIndex.usedInScheme(domain, 18));
                }

                if (!meta.referenceExist("definedInScheme")) {
                    meta.addReference(ReferenceIndex.definedInScheme(domain, 19));
                    logger.info("Adding definedInScheme");
                } else {
                    logger.info("definedInScheme exist");
                    meta.addReference(ReferenceIndex.definedInScheme(domain, 19));
                }

            }
            // Add all other meta types except concept  link
            if (!meta.isOfType(NodeType.ConceptLink)) {
                metaModel.add(meta);
            }
        });
        migrationService.updateTypes(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID, metaModel);
    }
}
