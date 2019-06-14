package fi.vm.yti.terminology.api.resolve;

import fi.vm.yti.terminology.api.TermedContentType;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.exception.ResourceNotFoundException;
import fi.vm.yti.terminology.api.exception.VocabularyNotFoundException;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.Graph;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.resolve.ResolvedResource.Type;
import fi.vm.yti.terminology.api.util.Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpMethod.GET;

@Service
public class ResolveService {
    private static final Logger logger = LoggerFactory.getLogger(ResolveService.class);
    private final TermedRequester termedRequester;
    private final String namespaceRoot;

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(?<prefix>\\w+)/$");
    private static final Pattern PREFIX_AND_RESOURCE_PATTERN = Pattern.compile("^(?<prefix>\\w+)/(?<resource>[\\w\\-]+)$");

    @Autowired
    ResolveService(TermedRequester termedRequester,
                   @Value("${namespace.root}") String namespaceRoot) {
        this.termedRequester = termedRequester;
        this.namespaceRoot = namespaceRoot;
    }

    ResolvedResource resolveResource(String uri) {

        if (!uri.startsWith(namespaceRoot)) {
            logger.error("Unsupported URI namespace URI: " + uri);
            throw new RuntimeException("Unsupported URI namespace: " + uri);
        }

        String uriWithoutParameters = uri.replaceFirst("\\?.*$", "");
        String path = uriWithoutParameters.substring(namespaceRoot.length());

        Matcher prefixMatcher = PREFIX_PATTERN.matcher(path);

        if (prefixMatcher.matches()) {

            String prefix = prefixMatcher.group("prefix");
            UUID graphId = findGraphIdForPrefix(prefix);

            return new ResolvedResource(graphId, ResolvedResource.Type.VOCABULARY);
        }

        Matcher prefixAndResourceMatcher = PREFIX_AND_RESOURCE_PATTERN.matcher(path);

        if (prefixAndResourceMatcher.matches()) {

            String prefix = prefixAndResourceMatcher.group("prefix");
            String resource = prefixAndResourceMatcher.group("resource");

            UUID graphId = findGraphIdForPrefix(prefix);
            UUID conceptId = findConceptId(graphId, resource);

            if (conceptId != null) {
                return new ResolvedResource(graphId, ResolvedResource.Type.CONCEPT, conceptId);
            } else {
                UUID collectionId = findCollectionId(graphId, resource);

                if (collectionId != null) {
                    return new ResolvedResource(graphId, ResolvedResource.Type.COLLECTION, collectionId);
                } else {
                    logger.error("Resource not found URI: " + uri);
                    throw new ResourceNotFoundException(prefix, resource);
                }
            }
        }

        logger.error("Unsupported URI: " + uri);
        throw new RuntimeException("Unsupported URI: " + uri);
    }

    private @Nullable UUID findConceptId(UUID graphId, String code) {
        return findNodeId(graphId, NodeType.Concept, code);
    }

    private @Nullable UUID findCollectionId(UUID graphId, String code) {
        return findNodeId(graphId, NodeType.Collection, code);
    }

    private @Nullable UUID findNodeId(UUID graphId, NodeType nodeType, String code) {

        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "type");
        params.add("select", "code");
        params.add("where", "graph.id:" + graphId);
        params.add("where", "type.id:" + nodeType.name());
        params.add("where", "code:" + code);
        params.add("max", "-1");

        List<GenericNode> result =
                requireNonNull(termedRequester.exchange("/node-trees", GET, params, new ParameterizedTypeReference<List<GenericNode>>() {}));

        if (result.isEmpty()) {
            return null;
        } else {
            return result.get(0).getId();
        }
    }

    // FIXME inefficient implementation but termed doesn't provide better way (afaik)
    private @NotNull UUID findGraphIdForPrefix(String prefix) {

        Parameters params = new Parameters();
        params.add("max", "-1");

        return requireNonNull(termedRequester.exchange("/graphs", GET, params, new ParameterizedTypeReference<List<Graph>>() {}))
                .stream().filter(g -> g.getCode().equalsIgnoreCase(prefix))
                .findFirst().orElseThrow(() -> new VocabularyNotFoundException(prefix))
                .getId();
    }

    String getResource(@NotNull UUID graphId, @NotNull List<NodeType> types, TermedContentType contentType, @Nullable UUID resourceId) {

        Parameters params = new Parameters();
        params.add("select", "*");
        params.add("select", "properties.*");
        params.add("select", "references.*");
        params.add("where", formatWhereClause(graphId, types, resourceId));

        return requireNonNull(termedRequester.exchange("/node-trees", GET, params, String.class, contentType));
    }

    private static @NotNull String formatWhereClause(@NotNull UUID graphId, @NotNull List<NodeType> types, @Nullable UUID resourceId) {

        if (types.size() == 0) {
            throw new IllegalArgumentException("Must include at least one type");
        }

        String typeClause = types.stream().map(t -> "type.id:" + t.name()).collect(Collectors.joining(" OR "));

        return "graph.id:" + graphId +
                " AND (" + typeClause + ")" + (resourceId != null ? " AND id:" + resourceId : "");
    }
}
