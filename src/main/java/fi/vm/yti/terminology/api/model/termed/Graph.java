package fi.vm.yti.terminology.api.model.termed;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.*;
import static java.util.UUID.randomUUID;

public final class Graph {

    private final UUID id;
    private final String code;
    private String uri;

    private final List<String> roles;
    private final Map<String, List<Permission>> permissions;
    private final Map<String, List<Property>> properties;

    // Jackson constructor
    private Graph() {
        this(randomUUID(), "", "", emptyList(), emptyMap(), emptyMap());
    }

    public Graph(UUID id,
                 String code,
                 String uri,
                 List<String> roles,
                 Map<String, List<Permission>> permissions,
                 Map<String, List<Property>> properties) {
        this.id = id;
        this.code = code;
        this.uri = uri;
        this.roles = unmodifiableList(roles);
        this.permissions = unmodifiableMap(permissions);
        this.properties = unmodifiableMap(properties);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Map<String, List<Permission>> getPermissions() {
        return permissions;
    }

    public Map<String, List<Property>> getProperties() {
        return properties;
    }
}
