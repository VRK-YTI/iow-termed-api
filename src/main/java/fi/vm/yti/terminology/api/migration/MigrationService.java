package fi.vm.yti.terminology.api.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import fi.vm.yti.terminology.api.TermedContentType;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.model.termed.GenericDeleteAndSave;
import fi.vm.yti.terminology.api.model.termed.GenericDeleteModifyAndSave;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.Graph;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.Type;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;
import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.util.Parameters;
import static fi.vm.yti.terminology.api.migration.DomainIndex.SCHEMA_GRAPH_ID;
import static fi.vm.yti.terminology.api.util.CollectionUtils.filterToList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.HttpMethod.*;

@Service
/*
 * NOTE: usage of node-trees api is not probably a good idea here because termed
 * index might not have been initialized yet when running migrations
 */
public class MigrationService {

    private static Logger log = LoggerFactory.getLogger(MigrationService.class);
    private final TermedRequester termedRequester;
    private final ObjectMapper objectMapper;

    @Autowired
    MigrationService(TermedRequester termedRequester, ObjectMapper objectMapper) {
        this.termedRequester = termedRequester;
        this.objectMapper = objectMapper;
    }

    public void createGraph(Graph graph) {
        termedRequester.exchange("/graphs", POST, Parameters.empty(), String.class, graph);
    }

    public void deleteVocabularyGraph(UUID graphId) {
        log.info("deleteVocabularyGraph: graphId=" + graphId);
        if (findGraph(graphId) != null) {
            deleteGraph(graphId);
        }
    }

    public void deleteGraph(UUID graphId) {
        log.debug("Delete graph:" + graphId + " nodes");
        // First delete nodes
        Parameters params = new Parameters();
        params.add("disconnect", "true");
        termedRequester.exchange("/graphs/" + graphId + "/nodes", DELETE, Parameters.empty(), String.class);
        log.debug("Delete graph:" + graphId + " types");
        termedRequester.exchange("/graphs/" + graphId + "/types", DELETE, Parameters.empty(), String.class);
        // Then graph
        log.debug("Delete graph:" + graphId);
        termedRequester.exchange("/graphs/" + graphId, DELETE, Parameters.empty(), String.class);
    }

    private @NotNull List<Identifier> getAllNodeIdentifiers(UUID graphId) {
        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("where", "graph.id:" + graphId);
        params.add("max", "-1");

        return requireNonNull(termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<Identifier>>() {
                }));
    }

    public List<Identifier> getAllNamedReferences(UUID graphId, String type) {
        List<Identifier> rv = null;
        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("where", "type.id:" + type);
        params.add("max", "-1");
        if (graphId == null) {
            rv = termedRequester.exchange("/node-trees/", GET, params,
                    new ParameterizedTypeReference<List<Identifier>>() {
                    });
        } else {
            // id given, so get objects under it
            rv = termedRequester.exchange("/graphs/" + graphId + "/node-trees/", GET, params,
                    new ParameterizedTypeReference<List<Identifier>>() {
                    });
        }
        return rv;
    }

    /**
     * @param sync
     * @param disconnect
     * @param graphId
     * @param identifiers
     */
    public void removeNodes(boolean sync, boolean disconnect, UUID graphId, List<Identifier> identifiers) {
        Parameters params = new Parameters();
        params.add("batch", "true");
        params.add("disconnect", Boolean.toString(disconnect));
        params.add("sync", Boolean.toString(sync));
        List<UUID> idlist = identifiers.stream().map(Identifier::getId).collect(Collectors.toList());
        log.info("RemoveNodes graph:" + graphId + " identifiers:" + idlist);
        identifiers.forEach(id -> {
            if (id.getType() != null && id.getId() != null) {
                GenericNode n = getNode(id.getId());
                if (n == null) {
                    log.info("Graph:" + graphId + " Remove node:" + id.getId().toString() + " failed,  node not found");
                } else {
                    log.info("Graph:" + graphId + " Remove node:" + id.getId().toString());
                }
            }
        });
        termedRequester.exchange("/nodes", DELETE, params, String.class, identifiers, "admin", "user");
    }

    private void removeTypes(UUID graphId, List<MetaNode> metaNodes) {

        Parameters params = new Parameters();
        params.add("batch", "true");

        termedRequester.exchange("/graphs/" + graphId + "/types", DELETE, params, String.class, metaNodes);
    }

    public void updateAndDeleteInternalNodes(GenericDeleteAndSave deleteAndSave) {
        Parameters params = new Parameters();
        params.add("changeset", "true");
        params.add("sync", "false");

        this.termedRequester.exchange("/nodes", POST, params, String.class, deleteAndSave);
    }

    /**
     * DELETE /api/graphs/{graphId}/node-ids tai vain yhdelle tyypille DELETE
     * /api/graphs/{graphId}/types/{typeId}/node-ids
     */
    public void regeneratieIds(UUID graphId) {

        Parameters params = new Parameters();

        String rv = null;
        String path = "/graphs/" + graphId + "/node-ids";
        termedRequester.exchange(path, DELETE, params, String.class, rv);
        log.info("regenerateIds for " + graphId.toString() + " returned:" + rv);
    }

    public @Nullable MetaNode findType(TypeId typeId) {
        Parameters params = new Parameters();
        params.add("max", "-1");
        String path = "/graphs/" + typeId.getGraphId() + "/types/" + typeId.getId().name();
        JsonUtils.prettyPrintJson(typeId);
        log.info("findType path=" + path);
        return termedRequester.exchange(path, GET, params, MetaNode.class);
    }

    public @Nullable MetaNode findGraph(UUID graphId) {
        Parameters params = new Parameters();
        params.add("max", "-1");
        String path = "/graphs/" + graphId;
        return termedRequester.exchange(path, GET, params, MetaNode.class);
    }

    public @Nullable Graph getGraph(UUID graphId) {
        Parameters params = new Parameters();
        params.add("max", "-1");
        String path = "/graphs/" + graphId;
        return termedRequester.exchange(path, GET, params, Graph.class);
    }

    public void updateTypes(VocabularyNodeType vocabularyNodeType, Consumer<MetaNode> modifier) {
        log.info("migrationService.UpdateTypes: " + vocabularyNodeType.toString());
        findTerminologyGraphList().forEach(graphId -> updateTypes(graphId, modifier));
    }

    public void updateTypes(VocabularyNodeType vocabularyNodeType, NodeType nodeType, Consumer<MetaNode> modifier) {
        log.info("migrationService.UpdateTypes2:" + VocabularyNodeType.values());
        findTerminologyGraphList().forEach(graphId -> updateTypes(graphId, nodeType, modifier));
    }

    public void updateTypes(VocabularyNodeType vocabularyNodeType, Predicate<MetaNode> filter,
            Consumer<MetaNode> modifier) {
        log.info("migrationService.UpdateTypes3:" + VocabularyNodeType.values());
        findTerminologyGraphList().forEach(graphId -> updateTypes(graphId, filter, modifier));
    }

    public void updateTypes(UUID graphId, Consumer<MetaNode> modifier) {
        updateTypes(graphId, type -> true, modifier);
    }

    public void updateTypes(UUID graphId, NodeType nodeType, Consumer<MetaNode> modifier) {
        updateTypes(graphId, type -> type.isOfType(nodeType), modifier);
    }

    public void updateTypes(UUID graphId, Predicate<MetaNode> filter, Consumer<MetaNode> modifier) {

        List<MetaNode> types = filterToList(getTypes(graphId), filter);

        for (MetaNode metaNode : types) {
            modifier.accept(metaNode);
        }

        updateTypes(graphId, types);
    }

    public void updateTypes(UUID graphId, List<MetaNode> metaNodes) {

        Parameters params = new Parameters();
        params.add("batch", "true");
        if (log.isDebugEnabled()) {
            log.debug("UpdateTypes-send into termed graphId=" + graphId.toString());
        }
        termedRequester.exchange("/graphs/" + graphId + "/types", POST, params, String.class, metaNodes);
    }

    public void deleteTypes(VocabularyNodeType vocabularyNodeType, String typeName) {
        log.info("DeleteTypes id:" + vocabularyNodeType.values() + " type:" + typeName);
        findTerminologyIdList().forEach(graphId -> deleteType(graphId, typeName));
    }

    public void deleteType(UUID graphId, String typeName) {
        log.info("DeleteType id:" + graphId.toString() + " type:" + typeName);
        termedRequester.exchange("/graphs/" + graphId + "/types/" + typeName, DELETE, Parameters.empty(), String.class);
    }

    public @NotNull List<MetaNode> getTypes(UUID graphId) {
        Parameters params = new Parameters();
        params.add("max", "-1");

        String path = graphId != null ? "/graphs/" + graphId + "/types" : "/types";
        log.debug("getTypes() PATH=" + path);
        return requireNonNull(
                termedRequester.exchange(path, GET, params, new ParameterizedTypeReference<List<MetaNode>>() {
                }));
    }

    public @NotNull MetaNode getType(TypeId typeId) {
        return requireNonNull(findType(typeId));
    }

    /**
     * Get list of terminologicalVocabularyId's sample query
     * http://localhost:9102/api/node-trees?select=id,uri&where=type.id:TerminologicalVocabulary
     *
     * @param vocabularyType
     * @return
     */
    public List<UUID> findTerminologyIdList() {

        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("where", "type.id:TerminologicalVocabulary");
        params.add("max", "-1");

        List<Identifier> idList = termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<Identifier>>() {
                });

        List<UUID> termVocs = idList.stream().map(Identifier::getId).collect(toList());
        return termVocs;
    }

    public List<UUID> findTerminologyGraphList() {

        List<UUID> rv = new ArrayList<>();
        Parameters params = new Parameters();
        params.add("select", "type");
        params.add("where", "type.id:TerminologicalVocabulary");
        params.add("max", "-1");

        List<Type> ids = termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<Type>>() {
                });

        ids.forEach(o -> {
            rv.add(o.getType().getGraphId());
        });
        log.info("FindTerminologyGraphList ids=" + rv);
        return rv;
    }

    /**
     * Get list of nodes containing priority-field
     * http://localhost:9102/api/node-trees?select=id&where=where=properties.priority:*
     *
     * @param vocabularyType
     * @return
     */
    public List<UUID> findPrioritynodes() {
        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("where", "properties.priority:*");
        params.add("max", "-1");

        List<Identifier> idList = termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<Identifier>>() {
                });

        List<UUID> termVocs = idList.stream().map(Identifier::getId).collect(toList());
        return termVocs;
    }

    public List<Graph> getGraphs() {

        Parameters params = new Parameters();
        params.add("max", "-1");
        return requireNonNull(
                termedRequester.exchange("/graphs", GET, params, new ParameterizedTypeReference<List<Graph>>() {
                }));
    }

    public boolean updateGraph(Graph g) {
        boolean rv = true;
        Parameters params = new Parameters();
        params.add("changeset", "true");
        try {
            this.termedRequester.exchange("/graphs/" + g.getId(), PUT, params, String.class, g);
        } catch (HttpServerErrorException ex) {
            log.error("Incoming update list contains:" + g.getUri() + " id:" + g.getId());
            log.error("Update failed:" + ex.getResponseBodyAsString());
            rv = false;
        }
        return rv;
    }

    public void updateNodesWithJson(Resource resource) {
        try {
            updateNodesWithJson(objectMapper.readTree(resource.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateNodesWithJson(GenericNode node) {
        try {
            JsonNode jn = objectMapper.readTree(objectMapper.writeValueAsString(node));
            updateNodesWithJson(jn);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateNodesWithJson(JsonNode json) {

        Parameters params = new Parameters();
        params.add("batch", "true");

        this.termedRequester.exchange("/nodes", POST, params, String.class, json);
    }

    public void patchNodesWithNode(GenericNode gn) {
        Parameters params = new Parameters();
        // Replace given values or lists.
        params.add("append", "false");
        String message;
        try {
            message = new String(objectMapper.writeValueAsString(gn).getBytes(), StandardCharsets.UTF_8);
            String path = "/graphs/"+gn.getType().getGraphId()+"/types/"+gn.getType().getId()+"/nodes/"+gn.getId()+"/";
            log.debug("PATH("+path+") message:" + message);
            this.termedRequester.exchange(path, PATCH, params, String.class, message);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void patchNodeWithJson(UUID graphId, NodeType type, UUID nodeId, String message) {
        Parameters params = new Parameters();
        // Replace given values or lists.
        params.add("append", "false");
        String path = "/graphs/"+graphId+"/types/"+type+"/nodes/"+nodeId+"/";
        log.debug("PATH("+path+") message:" + message);
        this.termedRequester.exchange(path, PATCH, params, String.class, message);
    }

    public boolean isSchemaInitialized() {
        log.info("Checking if schema is initialized!");
        return termedRequester.exchange("/graphs/" + SCHEMA_GRAPH_ID, GET, Parameters.empty(), Graph.class) != null;
    }

    public List<GenericNode> getNodes(TypeId domain) {
        String url = "/graphs/" + domain.getGraphId() + "/types/" + domain.getId().name() + "/nodes/";
        List<GenericNode> result = termedRequester.exchange(url, GET, Parameters.empty(),
                new ParameterizedTypeReference<List<GenericNode>>() {
                });

        return result != null ? result : emptyList();
    }

    public List<GenericNode> getAllNodes(UUID graphId) {
        Parameters params = new Parameters();
        params.add("max", "-1");
        String url = "/graphs/" + graphId + "/nodes/";
        List<GenericNode> result = termedRequester.exchange(url, GET, params,
                new ParameterizedTypeReference<List<GenericNode>>() {
                });

        return result != null ? result : emptyList();
    }

    public List<GenericNode> getTerminologyNode(UUID graphId) {
        return getAllTypedNodes(graphId, "TerminologicalVocabulary");
    }

    public List<GenericNode> getAllTerms(UUID graphId) {
        return getAllTypedNodes(graphId, "Term");
    }

    public List<GenericNode> getAllConcepts(UUID graphId) {
        return getAllTypedNodes(graphId, "Concept");
    }

    public List<GenericNode> getAllTypedNodes(UUID graphId, String type) {
        String url = "/graphs/" + graphId + "/types/" + type + "/nodes/";
        List<GenericNode> result = termedRequester.exchange(url, GET, Parameters.empty(),
                new ParameterizedTypeReference<List<GenericNode>>() {
                });
        return result != null ? result : emptyList();
    }

    public GenericNode getNode(TypeId domain, UUID id) {
        String url = "/graphs/" + domain.getGraphId() + "/types/" + domain.getId().name() + "/nodes/" + id;
        return requireNonNull(termedRequester.exchange(url, GET, Parameters.empty(), GenericNode.class));
    }

    public GenericNode getNode(UUID id) {
        GenericNode node = null;
        Parameters params = new Parameters();
        params.add("select", "*");
        params.add("where", "id:" + id.toString());
        List<GenericNode> nodes = termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<GenericNode>>() {
                });
        if (!nodes.isEmpty() && nodes.size() == 1) {
            node = nodes.get(0);
        }
        return node;
    }

    public GenericNode getNode(String namespace) {
        GenericNode node = null;
        Parameters params = new Parameters();
        params.add("select", "*");
        params.add("where", "uri:" + namespace);
        List<GenericNode> nodes = termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<GenericNode>>() {
                });
        if (!nodes.isEmpty() && nodes.size() == 1) {
            node = nodes.get(0);
        }
        return node;
    }

    public JsonNode getNodeAsJson(UUID id) {
        Parameters params = new Parameters();
        params.add("select", "*");
        params.add("where", "id:" + id.toString());
        JsonNode node = termedRequester.exchange("/node-trees", GET, params, JsonNode.class);
        return node;
    }

    public GenericNode replaceIdRef(UUID id, UUID source, UUID target) {
        GenericNode node = null;
        Parameters params = new Parameters();
        params.add("select", "*");
        params.add("where", "id:" + id.toString());
        String nodeStr = termedRequester.exchange("/node-trees", GET, params, String.class);
        if (nodeStr != null && !nodeStr.isEmpty() && nodeStr.length() > 2) {
            nodeStr = nodeStr.replaceAll(source.toString(), target.toString());
            // Remove array brackets from response
            nodeStr = nodeStr.substring(1, nodeStr.length() - 1);
            ObjectMapper mapper = new ObjectMapper();
            try {
                node = mapper.readValue(nodeStr, GenericNode.class);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return node;
    }

    public boolean updateAndDeleteInternalNodes(GenericDeleteModifyAndSave operation, boolean sync) {
        boolean rv = true;
        Parameters params = new Parameters();
        params.add("changeset", "true");
        params.add("sync", String.valueOf(sync));
        try {
            log.debug("Update:" + objectMapper.writeValueAsString(operation));
            this.termedRequester.exchange("/nodes", POST, params, String.class, objectMapper.writeValueAsString(operation),
                    TermedContentType.JSON);
        } catch (HttpServerErrorException ex) {
            log.error("Incoming update list contains:" + operation.getSave().size() + " items");
            log.error("Update failed:" + ex.getResponseBodyAsString());
            rv = false;
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            log.error("Incoming update list contains:" + operation.getPatch().size() + " items");
            log.error("Update failed:" + e.getMessage());
            rv = false;
        }
        return rv;
    }

    public boolean updateAndDeleteInternalNodes(UUID graphId, String nameSpace, GenericDeleteModifyAndSave operation) {
        boolean rv = true;
        Parameters params = new Parameters();
        params.add("changeset", "true");
        params.add("uriNamespace", nameSpace);
        try {
            log.debug("Update graph:" + graphId + "  value:" + objectMapper.writeValueAsString(operation));
            String path = ("/graphs/" + graphId + "/nodes/");
            HttpMethod method = POST;
            String body = new String(objectMapper.writeValueAsString(operation).getBytes(), StandardCharsets.UTF_8);
            TermedContentType contentType = TermedContentType.JSON;
            termedRequester.exchange(path, method, params, String.class, body, contentType);
        } catch (HttpServerErrorException ex) {
            log.error("Incoming update list contains:" + operation.getSave().size() + " items");
            log.error("Update failed:" + ex.getResponseBodyAsString());
            rv = false;
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            log.error("Incoming update list contains:" + operation.getPatch().size() + " items");
            log.error("Update failed:" + e.getMessage());
            rv = false;
        }

        return rv;
    }

}
