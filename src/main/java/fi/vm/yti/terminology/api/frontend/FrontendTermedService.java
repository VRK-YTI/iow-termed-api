package fi.vm.yti.terminology.api.frontend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.security.YtiUser;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.exception.NodeNotFoundException;
import fi.vm.yti.terminology.api.exception.VocabularyNotFoundException;
import fi.vm.yti.terminology.api.integration.IntegrationService;
import fi.vm.yti.terminology.api.model.termed.*;
import fi.vm.yti.terminology.api.security.AuthorizationManager;
import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.util.Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static fi.vm.yti.security.AuthorizationException.check;
import static fi.vm.yti.terminology.api.migration.DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID;
import static fi.vm.yti.terminology.api.model.termed.VocabularyNodeType.TerminologicalVocabulary;
import static fi.vm.yti.terminology.api.model.termed.VocabularyNodeType.Vocabulary;
import static fi.vm.yti.terminology.api.util.CollectionUtils.mapToList;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@Service
public class FrontendTermedService {

    private static final Logger logger = LoggerFactory.getLogger(FrontendTermedService.class);

    private static final String USER_PASSWORD = "user";
    private static final Pattern UUID_PATTERN = Pattern
            .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Logger LOGGER = LoggerFactory.getLogger(FrontendTermedService.class);
    private static final Object USER_LOCK = new Object();

    private final TermedRequester termedRequester;
    private final FrontendGroupManagementService groupManagementService;
    private final AuthenticatedUserProvider userProvider;
    private final AuthorizationManager authorizationManager;
    private final String namespaceRoot;

    @Autowired
    public FrontendTermedService(TermedRequester termedRequester, FrontendGroupManagementService groupManagementService,
            AuthenticatedUserProvider userProvider, AuthorizationManager authorizationManager,
            @Value("${namespace.root}") String namespaceRoot) {
        this.termedRequester = termedRequester;
        this.groupManagementService = groupManagementService;
        this.userProvider = userProvider;
        this.authorizationManager = authorizationManager;
        this.namespaceRoot = namespaceRoot;
    }

    boolean isNamespaceInUse(String prefix) {

        String namespace = formatNamespace(prefix);

        for (Graph graph : getGraphs()) {
            if (prefix.equals(graph.getCode()) || namespace.equals(graph.getUri())) {
                return true;
            }
        }

        return false;
    }

    public @NotNull GenericNode getNode(UUID terminologyId) {
        
        String path = "/graphs/" + TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID + "/types/"+NodeType.TerminologicalVocabulary+"/nodes/"+ terminologyId;
        logger.info("getNode path="+path);
        return requireNonNull(termedRequester.exchange(path, GET, Parameters.empty(), GenericNode.class));
    }

    public @NotNull GenericNodeInlined getVocabulary(UUID graphId) {

        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("select", "code");
        params.add("select", "uri");
        params.add("select", "createdBy");
        params.add("select", "createdDate");
        params.add("select", "lastModifiedBy");
        params.add("select", "lastModifiedDate");
        params.add("select", "properties.*");
        params.add("select", "references.*");

        params.add("where", "graph.id:" + graphId + " AND (type.id:" + Vocabulary + " OR type.id:"
                + TerminologicalVocabulary + ")");

        params.add("max", "-1");

        List<GenericNodeInlined> result = requireNonNull(termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<GenericNodeInlined>>() {
                }));

        if (result.size() == 0) {
            throw new NodeNotFoundException(graphId, asList(NodeType.Vocabulary, NodeType.TerminologicalVocabulary));
        } else {
            return userNameToDisplayName(result.get(0), new UserIdToDisplayNameMapper());
        }
    }

    @NotNull JsonNode getVocabularyList(boolean incomplete) {

        if(userProvider.getUser() != null){ 
            System.err.println("getVocabularyList:"+userProvider.getUser().getUsername() );
        }
        List<UUID> orgList = new ArrayList<>();

        YtiUser user = userProvider.getUser();
        // Resolve current organizations for filtering
        Map<UUID,?> rolesAndOrgs = user.getRolesInOrganizations();
        rolesAndOrgs.forEach((k,v)->{
            orgList.add(k);
        });
        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("select", "code");
        params.add("select", "uri");
        params.add("select", "properties.*");
        params.add("select", "references.contributor");
        params.add("select", "references.inGroup");

        params.add("where",
            "type.id:" +  TerminologicalVocabulary );
        params.add("max", "-1");
        // Execute full search
        JsonNode rv =  requireNonNull(termedRequester.exchange("/node-trees", GET, params, JsonNode.class));
        // Super-user sees all
        if(user.isSuperuser()){
            return requireNonNull(rv);            
        }
        // normal users sees filtered
        List<JsonNode> nodes = new ArrayList<>();

        for(int x=0;x<rv.size();x++){
            JsonNode n = rv.get(x);
            UUID id=UUID.fromString(n.at("/references/contributor/0/id").textValue());
            String status=n.at("/properties/status/0/value").textValue();            
            if(status != null && 
               status.equalsIgnoreCase("INCOMPLETE")) {
                if(incomplete && 
                   orgList.contains(id)){
                    nodes.add(n);
                } else {
                    if(logger.isDebugEnabled()){ 
                        logger.debug("Dropped INCOMPLETE vocabulary "+id.toString());
                    }
                }
            } else {
                nodes.add(n);
            }  
        }
        rv = null;
        ObjectMapper mapper = new ObjectMapper();        
        return requireNonNull(mapper.convertValue(nodes,JsonNode.class));
    }

    void createVocabulary(UUID templateGraphId, String prefix, GenericNode vocabularyNode, UUID graphId, boolean sync) {

        check(authorizationManager.canCreateVocabulary(vocabularyNode));
        String uri = vocabularyNode.getUri();
        if (uri != null && !uri.isEmpty() && uri.contains("terminological-vocabulary")) {
            // Remove last part of the uri
            uri = uri.substring(0, uri.lastIndexOf("/"));
            vocabularyNode.setUri(uri);
        } else {
            logger.error("Missing incoming URI");
            vocabularyNode.setUri("http://uri.suomi.fi/terminology/"+prefix);
        }
 
        List<MetaNode> templateMetaNodes = getTypes(templateGraphId);
        List<Property> prefLabel = mapToList(vocabularyNode.getProperties().get("prefLabel"), Attribute::asProperty);

        logger.debug("Handling nodes for \"" + prefix + "\"");       
        JsonUtils.prettyPrintJson(vocabularyNode);
        updateAndDeleteInternalNodes(
                new GenericDeleteAndSave(emptyList(), singletonList(vocabularyNode.copyToGraph(templateGraphId))), sync, null);
        logger.debug("Finished for \"" + prefix + "\"");
    }

    void deleteVocabulary(UUID graphId) {

        check(authorizationManager.canDeleteVocabulary(graphId));

        removeNodes(true, false, getAllNodeIdentifiers(graphId));
        removeTypes(graphId, getTypes(graphId));
        deleteGraph(graphId);
    }

    @NotNull
    GenericNodeInlined getConcept(UUID graphId, UUID conceptId) {
        return getConcept(conceptId);
    }

    @NotNull
    GenericNodeInlined getConcept(UUID conceptId) {

        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("select", "code");
        params.add("select", "uri");
        params.add("select", "createdBy");
        params.add("select", "createdDate");
        params.add("select", "lastModifiedBy");
        params.add("select", "lastModifiedDate");
        params.add("select", "properties.*");
        params.add("select", "references.*");
        params.add("select", "references.prefLabelXl:2");
        params.add("select", "referrers.*");
        params.add("select", "referrers.prefLabelXl:2");
        params.add("where", "graph.id:" + TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        params.add("where", "id:" + conceptId);
        params.add("max", "-1");

        List<GenericNodeInlined> result = requireNonNull(termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<GenericNodeInlined>>() {
                }));

        if (result.size() == 0) {
            throw new NodeNotFoundException(TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID, conceptId);
        } else {
            return userNameToDisplayName(result.get(0), new UserIdToDisplayNameMapper());
        }
    }

    @NotNull
    GenericNodeInlined getCollection(UUID graphId, UUID collectionId) {

        // 1 graph change, don't use graphId
        return getCollection(collectionId);
    }

    @NotNull
    GenericNodeInlined getCollection(UUID collectionId) {

        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("select", "code");
        params.add("select", "uri");
        params.add("select", "createdBy");
        params.add("select", "createdDate");
        params.add("select", "lastModifiedBy");
        params.add("select", "lastModifiedDate");
        params.add("select", "properties.*");
        params.add("select", "references.*");
        params.add("select", "references.prefLabelXl:2");
        params.add("where", "graph.id:" + TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        params.add("where", "id:" + collectionId);
        params.add("max", "-1");

        List<GenericNodeInlined> result = requireNonNull(termedRequester.exchange("/node-trees", GET, params,
                new ParameterizedTypeReference<List<GenericNodeInlined>>() {
                }));

        if (result.size() == 0) {
            throw new NodeNotFoundException(TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID, collectionId);
        } else {
            return userNameToDisplayName(result.get(0), new UserIdToDisplayNameMapper());
        }
    }

    @NotNull
    JsonNode getCollectionList(UUID terminologyId) {

        // 
        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("select", "code");
        params.add("select", "uri");
        params.add("select", "properties.prefLabel");
        params.add("select", "properties.status");
        params.add("select", "lastModifiedDate");
        params.add("where", "graph.id:" + TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        params.add("where", "type.id:" + "Collection");
        params.add("where", "references.definedInScheme.id:" + terminologyId);
        params.add("max", "-1");

        return requireNonNull(termedRequester.exchange("/node-trees", GET, params, JsonNode.class));
    }

    @NotNull
    JsonNode getCollectionList() {

        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("select", "code");
        params.add("select", "uri");
        params.add("select", "properties.prefLabel");
        params.add("select", "properties.status");
        params.add("select", "lastModifiedDate");
        params.add("where", "graph.id:" + TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
        params.add("where", "type.id:" + "Collection");
        params.add("max", "-1");

        return requireNonNull(termedRequester.exchange("/node-trees", GET, params, JsonNode.class));
    }

    public @NotNull List<GenericNode> getNodes(UUID graphId) {
        Parameters params = new Parameters();
        params.add("max", "-1");

        String path = graphId != null ? "/graphs/" + graphId + "/nodes" : "/nodes";

        return requireNonNull(
                termedRequester.exchange(path, GET, params, new ParameterizedTypeReference<List<GenericNode>>() {
                }));
    }

    public @NotNull List<GenericNode> getTerminologyNodes(UUID terminologyId) {
        Parameters params = new Parameters();
        params.add("select", "*");
        params.add("where","references.definedInScheme.id:"+terminologyId);
        params.add("max", "-1");

        return requireNonNull(
                termedRequester.exchange("/node-trees", GET, params, new ParameterizedTypeReference<List<GenericNode>>() {
                }));
    }

    public @NotNull GenericNode getConceptNode(UUID graphId, UUID conceptId) {
        Parameters params = new Parameters();
        params.add("max", "-1");
        // One graph
        return getConceptNode(conceptId);
    }

    public @NotNull GenericNode getConceptNode(UUID conceptId) {
        Parameters params = new Parameters();
        params.add("max", "-1");
        String path = "/graphs/" + TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID + "/types/Concept/nodes/" + conceptId ;
        return requireNonNull(
                termedRequester.exchange(path, GET, params, new ParameterizedTypeReference<GenericNode>() {
                }));
    }

    @NotNull
    JsonNode getNodeListWithoutReferencesOrReferrers(NodeType nodeType) {

        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("select", "code");
        params.add("select", "uri");
        params.add("select", "properties.*");
        params.add("where", "type.id:" + nodeType);
        params.add("max", "-1");

        return requireNonNull(termedRequester.exchange("/node-trees", GET, params, JsonNode.class));
    }

    public void bulkChange(GenericDeleteAndSave deleteAndSave, boolean sync) {

        check(authorizationManager.canModifyNodes(deleteAndSave.getSave()));
        check(authorizationManager.canRemoveNodes(deleteAndSave.getDelete()));

        updateAndDeleteInternalNodes(deleteAndSave, sync, null);
    }

    public void bulkChangeWithoutAuthorization(GenericDeleteAndSave deleteAndSave, boolean sync, UUID externalUserId) {
        updateAndDeleteInternalNodes(deleteAndSave, sync, externalUserId);
    }

    void removeNodes(boolean sync, boolean disconnect, List<Identifier> identifiers) {

        check(authorizationManager.canRemoveNodes(identifiers));

        Parameters params = new Parameters();
        params.add("batch", "true");
        params.add("disconnect", Boolean.toString(disconnect));
        params.add("sync", Boolean.toString(sync));

        UUID username = ensureTermedUser(null);

        termedRequester.exchange("/nodes", HttpMethod.DELETE, params, String.class, identifiers, username.toString(),
                USER_PASSWORD);
    }

    public @NotNull List<MetaNode> getTypes(UUID graphId) {

        Parameters params = new Parameters();
        params.add("max", "-1");

        String path = graphId != null ? "/graphs/" + graphId + "/types" : "/types";

        return requireNonNull(
                termedRequester.exchange(path, GET, params, new ParameterizedTypeReference<List<MetaNode>>() {
                }));
    }

    public @NotNull List<MetaNode> getMetaTypes() {
        return getTypes(TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID);
    }

    public @NotNull List<Graph> getGraphs() {

        Parameters params = new Parameters();
        params.add("max", "-1");

        return requireNonNull(
                termedRequester.exchange("/graphs", GET, params, new ParameterizedTypeReference<List<Graph>>() {
                }));
    }

    public @NotNull Graph getGraph(UUID graphId) {
        return requireNonNull(termedRequester.exchange("/graphs/" + graphId, GET, Parameters.empty(), Graph.class));
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

    private void createGraph(String prefix, List<Property> prefLabel, UUID graphId) {

        String code = prefix;
        String uri = formatNamespace(prefix);
        List<String> roles = emptyList();
        Map<String, List<Permission>> permissions = emptyMap();
        Map<String, List<Property>> properties = singletonMap("prefLabel", prefLabel);

        Graph graph = new Graph(graphId, code, uri, roles, permissions, properties);

        termedRequester.exchange("/graphs", POST, Parameters.empty(), String.class, graph);
    }

    private void updateAndDeleteInternalNodes(GenericDeleteAndSave deleteAndSave, boolean sync, UUID externalUserId) {

        Parameters params = new Parameters();
        params.add("changeset", "true");
        params.add("sync", String.valueOf(sync));

        UUID username = ensureTermedUser(externalUserId);

        this.termedRequester.exchange("/nodes", POST, params, String.class, deleteAndSave, username.toString(),
                USER_PASSWORD);
    }

    private void deleteGraph(UUID graphId) {
        termedRequester.exchange("/graphs/" + graphId, HttpMethod.DELETE, Parameters.empty(), String.class);
    }

    private void updateTypes(UUID graphId, List<MetaNode> metaNodes) {

        Parameters params = new Parameters();
        params.add("batch", "true");

        termedRequester.exchange("/graphs/" + graphId + "/types", POST, params, String.class, metaNodes);
    }

    private void removeTypes(UUID graphId, List<MetaNode> metaNodes) {

        Parameters params = new Parameters();
        params.add("batch", "true");

        termedRequester.exchange("/graphs/" + graphId + "/types", HttpMethod.DELETE, params, String.class, metaNodes);
    }

    private UUID ensureTermedUser(UUID externalUserId) {

        YtiUser user = userProvider.getUser();

        if (user.isAnonymous() && externalUserId == null) {
            throw new RuntimeException("Logged in user needed for the operation");
        }

        if (findTermedUser(user) == null) {
            synchronized (USER_LOCK) {
                if (findTermedUser(user) == null) {
                    createTermedUser(user, externalUserId);
                }
            }
        }

        if (externalUserId != null) {
            return externalUserId;
        } else {
            return user.getId();
        }
    }

    private @Nullable TermedUser findTermedUser(YtiUser user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        Parameters params = Parameters.single("username", user.getId().toString());
        return termedRequester.exchange("/users", GET, params, TermedUser.class);
    }

    private void createTermedUser(YtiUser user, UUID externalUserId) {
        Parameters params = Parameters.single("sync", "true");
        String userIdForTermedUser = externalUserId == null ? user.getId().toString() : externalUserId.toString();
        TermedUser termedUser = new TermedUser(userIdForTermedUser, USER_PASSWORD, "ADMIN");
        termedRequester.exchange("/users", POST, params, String.class, termedUser);
    }

    private String formatNamespace(@NotNull String prefix) {
        return this.namespaceRoot + prefix + '/';
    }

    private GenericNodeInlined userNameToDisplayName(GenericNodeInlined node,
            UserIdToDisplayNameMapper userIdToDisplayNameMapper) {

        return new GenericNodeInlined(node.getId(), node.getCode(), node.getUri(), node.getNumber(),
                userIdToDisplayNameMapper.map(node.getCreatedBy()), node.getCreatedDate(),
                userIdToDisplayNameMapper.map(node.getLastModifiedBy()), node.getLastModifiedDate(), node.getType(),
                node.getProperties(),
                mapMapValues(node.getReferences(), x -> userNameToDisplayName(x, userIdToDisplayNameMapper)),
                mapMapValues(node.getReferrers(), x -> userNameToDisplayName(x, userIdToDisplayNameMapper)));
    }

    private GenericNode userNameToDisplayName(GenericNode node, UserIdToDisplayNameMapper userIdToDisplayNameMapper) {

        return new GenericNode(node.getId(), node.getCode(), node.getUri(), node.getNumber(),
                userIdToDisplayNameMapper.map(node.getCreatedBy()), node.getCreatedDate(),
                userIdToDisplayNameMapper.map(node.getLastModifiedBy()), node.getLastModifiedDate(), node.getType(),
                node.getProperties(), node.getReferences(), node.getReferrers());
    }

    private static <K, V> Map<K, List<V>> mapMapValues(Map<K, List<V>> map, Function<V, V> mapper) {
        return map.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> mapToList(e.getValue(), mapper)));
    }

    private static boolean isUUID(String s) {
        return UUID_PATTERN.matcher(s).matches();
    }

    // XXX consider cache spanning to multiple requests
    private class UserIdToDisplayNameMapper {

        private final Map<String, String> cache = new HashMap<>();

        private String map(String userId) {
            return cache.computeIfAbsent(userId, this::mapRemote);
        }

        private String mapRemote(String userId) {

            if (!isUUID(userId)) {
                return "";
            }

            GroupManagementUser user = groupManagementService.findUser(userId);

            if (user != null) {
                return user.getDisplayName();
            } else {
                return "";
            }
        }
    }
}
