package fi.vm.yti.terminology.api.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.model.termed.*;
import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.util.Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.vm.yti.terminology.api.migration.DomainIndex.SCHEMA_GRAPH_ID;
import static fi.vm.yti.terminology.api.util.CollectionUtils.filterToList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.DELETE;

import static fi.vm.yti.terminology.api.migration.DomainIndex.VOCABULARY_TEMPLATE_GRAPH_ID;
import static fi.vm.yti.terminology.api.migration.DomainIndex.SCHEMA_GRAPH_ID;
import static fi.vm.yti.terminology.api.migration.DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID;

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
            removeNodes(true, false, graphId, getAllNodeIdentifiers(graphId));
            log.info("deleteVocabularyGraph: after nodes removed getTypes=" + getTypes(graphId));
            removeTypes(graphId, getTypes(graphId));
            log.info("deleteVocabularyGraph: after types removed");
            deleteGraph(graphId);
        }
    }

    private void deleteGraph(UUID graphId) {
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

    public void removeNodes(boolean sync, boolean disconnect, UUID graphId, List<Identifier> identifiers) {
        Parameters params = new Parameters();
        params.add("batch", "true");
        params.add("disconnect", Boolean.toString(disconnect));
        params.add("sync", Boolean.toString(sync));
        List<UUID> idlist = identifiers.stream().map(Identifier::getId).collect(Collectors.toList());
        log.info("RemoveNodes identifiers:" + idlist);
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
        log.info("findGraph(" + graphId + ") called");
        return termedRequester.exchange(path, GET, params, MetaNode.class);
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
        log.info("UpdateTypes-send into termed graphId=" + graphId.toString());
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

        params = new Parameters();
        params.add("select", "type");
        params.add("where", "type.id:TerminologicalVocabulary");
        params.add("max", "-1");

        List<Type> ids = termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<Type>>() {
                });
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

    public List<Graph> getGraphs() {

        Parameters params = new Parameters();
        params.add("max", "-1");
        return requireNonNull(
                termedRequester.exchange("/graphs", GET, params, new ParameterizedTypeReference<List<Graph>>() {
                }));
    }

    public void updateNodesWithJson(Resource resource) {
        try {
            updateNodesWithJson(objectMapper.readTree(resource.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateNodesWithJson(JsonNode json) {

        Parameters params = new Parameters();
        params.add("batch", "true");

        this.termedRequester.exchange("/nodes", POST, params, String.class, json);
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
        String url = "/graphs/" + graphId + "/nodes/";
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

    public JsonNode getNodeAsJson(UUID id) {
        Parameters params = new Parameters();
        params.add("select", "*");
        params.add("where", "id:" + id.toString());
        JsonNode node = termedRequester.exchange("/node-trees", GET, params,
                JsonNode.class);
        return node;
    }

}
