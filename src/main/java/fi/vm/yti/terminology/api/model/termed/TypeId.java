package fi.vm.yti.terminology.api.model.termed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.UUID;

public final class TypeId {

    private final NodeType id;
    private final GraphId graph;
    @JsonInclude(Include.NON_EMPTY) 
    private final String uri;

    // Jackson constructor
    private TypeId() {
        this(NodeType.placeholder(), GraphId.placeholder());
    }

    public TypeId(NodeType id, GraphId graph) {
        this(id, graph, "");
    }

    public TypeId(NodeType id, GraphId graph, String uri) {
        this.id = id;
        this.graph = graph;
        this.uri = uri;
    }

    public static TypeId placeholder() {
        return new TypeId();
    }

    public NodeType getId() {
        return id;
    }

    public GraphId getGraph() {
        return graph;
    }

    @JsonIgnore
    public UUID getGraphId() {
        return graph != null ? graph.getId() : null;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeId that = (TypeId) o;

        return id.equals(that.id) && graph.equals(that.graph);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + graph.hashCode();
        return result;
    }

    public TypeId copyToGraph(UUID graphId) {
        return new TypeId(id, new GraphId(graphId), uri);
    }
}
