package fi.vm.yti.terminology.api.model.termed;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import static java.util.Collections.emptyMap;
import static java.util.UUID.randomUUID;

import java.util.ArrayList;

public final class GenericNode implements Node {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID id = null;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID usedInScheme = null;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code = null;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String uri = null;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long number = null;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String createdBy = null;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date createdDate = null;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String lastModifiedBy = null;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date lastModifiedDate = null;

    private final TypeId type;

    private final Map<String, List<Attribute>> properties;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, List<Identifier>> references = null;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, List<Identifier>> referrers = null;

    // Jackson constructor
    private GenericNode() {
        this(randomUUID(), null, null, 0L, null, new Date(), null, new Date(), TypeId.placeholder(), emptyMap(), emptyMap(), emptyMap());
    }

    public GenericNode(UUID id,
                       String code,
                       String uri,
                       Long number,
                       String createdBy,
                       Date createdDate,
                       String lastModifiedBy,
                       Date lastModifiedDate,
                       TypeId type,
                       Map<String, List<Attribute>> properties,
                       Map<String, List<Identifier>> references,
                       Map<String, List<Identifier>> referrers) {
        this.id = id;
        this.code = code;
        this.uri = uri;
        this.number = number;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.lastModifiedBy = lastModifiedBy;
        this.lastModifiedDate = lastModifiedDate;
        this.type = type;
        this.properties = properties;
        this.references = references;
        this.referrers = referrers;
    }

    /**
     * Used for import. No given UUID so random uuid is created on upon call
     *
     * @param code
     * @param uri
     * @param number
     * @param createdBy
     * @param createdDate
     * @param lastModifiedBy
     * @param lastModifiedDate
     * @param type
     * @param properties
     * @param references
     * @param referrers
     */
    public GenericNode(String code,
                       String uri,
                       Long number,
                       String createdBy,
                       Date createdDate,
                       String lastModifiedBy,
                       Date lastModifiedDate,
                       TypeId type,
                       Map<String, List<Attribute>> properties,
                       Map<String, List<Identifier>> references,
                       Map<String, List<Identifier>> referrers) {
        this.id = randomUUID();
        this.code = code;
        this.uri = uri;
        this.number = number;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.lastModifiedBy = lastModifiedBy;
        this.lastModifiedDate = lastModifiedDate;
        this.type = type;
        this.properties = properties;
        this.references = references;
        this.referrers = referrers;
    }

    /**
     * Simplified creator
     *
     * @param type       typeId containing  vocabulary-id
     * @param properties Attributes as Property-map
     * @param references References as Identifier-map
     */
    public GenericNode(TypeId type,
                       Map<String, List<Attribute>> properties,
                       Map<String, List<Identifier>> references
    ) {
        this.id = randomUUID();
        this.type = type;
        this.properties = properties;
        this.references = references;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Long getNumber() {
        return number;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date dt) {
        createdDate = dt;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Date getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Date dt) {
        lastModifiedDate = dt;
    }

    public TypeId getType() {
        return type;
    }

    public Map<String, List<Attribute>> getProperties() {
        return properties;
    }

    public Map<String, List<Identifier>> getReferences() {
        return references;
    }

    public Map<String, List<Identifier>> getReferrers() {
        return referrers;
    }

    public void setReferrers(Map<String, List<Identifier>> referrers) {
        this.referrers = referrers;
    }

    public void setReferences(Map<String, List<Identifier>> references) {
        this.references = references;
    }
    /**
     * Utility function to switch parent terminology
     */
    public void addDefinedInScheme(UUID terminologyId, TypeId typeId){
        Map<String, List<Identifier>> references = getReferences();
        Identifier definedInScheme = new Identifier(terminologyId, typeId);
        List<Identifier> disList = new ArrayList<>();
        disList.add(definedInScheme);
        if(references == null){
            // No references, create one
            System.out.println("no references part");
            references = new HashMap<>();
            references.put("definedInScheme", disList);
            setReferences(references);
        } else {
            // Try to find definedInScheme
            if(references.get("definedInScheme") != null) {
                // Replace 
                System.out.println("replacing definedInScheme");
                references.put("definedInScheme", disList);
            } else {
                // references found but missing definedInScheme
                references.put("definedInScheme", disList);
            }
        }
    }

    public GenericNode copyToGraph(UUID graphId) {

        TypeId newType = type.copyToGraph(graphId);

        return new GenericNode(id, code, uri, number, createdBy, createdDate, lastModifiedBy, lastModifiedDate, newType, properties, references, referrers);
    }
}
