package fi.vm.yti.terminology.api.model.termed;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;
import static java.util.UUID.randomUUID;

//@JsonIgnoreProperties(value = { "properties", "references", "referrers" })
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public final class Identifier {

    private final UUID id;
    private final TypeId type;
    private String uri = null;
    private String code = null;
    private Map<String, List<Attribute>> properties;

    // Jackson constructor
    private Identifier() {
        this(randomUUID(), TypeId.placeholder());
    }

    public Identifier(UUID id,
                      TypeId type) {
        this.id = id;
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public TypeId getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Identifier that = (Identifier) o;
        return Objects.equals(id, that.id) &&
            Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    public String getUri() {
        return uri;
    }

    /**
     * @param uri the uri to set
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * @return String return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * @param code the code to set
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * @return Map<String, List < Attribute>> return the properties
     */
    public Map<String, List<Attribute>> getProperties() {
        return properties;
    }

    /**
     * @param properties the properties to set
     */
    public void setProperties(Map<String, List<Attribute>> properties) {
        this.properties = properties;
    }

}
