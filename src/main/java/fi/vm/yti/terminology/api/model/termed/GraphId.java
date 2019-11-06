package fi.vm.yti.terminology.api.model.termed;

import java.util.UUID;

import static java.util.UUID.randomUUID;

public final class GraphId {

    private  UUID id;

    // Jackson constructor
    private GraphId() {
        this(randomUUID());
    }

    public GraphId(UUID id) {
        this.id = id;
    }

    public static GraphId placeholder() {
        return new GraphId();
    }

    public UUID getId() {
        return id;
    }

    public void  setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphId graphId = (GraphId) o;

        return id.equals(graphId.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
