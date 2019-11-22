package fi.vm.yti.terminology.api.model.termed;

import static fi.vm.yti.terminology.api.migration.PropertyUtil.prefLabel;
import static fi.vm.yti.terminology.api.util.CollectionUtils.mapToList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import fi.vm.yti.terminology.api.migration.PropertyUtil;

@JsonIgnoreProperties(ignoreUnknown=true)
public final class MetaNode {

    private final String id;
    private String uri;
    private final Long index;
    private final GraphId graph;
    private final Map<String, List<Permission>> permissions;
    private final Map<String, List<Property>> properties;
    private final List<AttributeMeta> textAttributes;
    private final List<ReferenceMeta> referenceAttributes;

    // Jackson constructor
    private MetaNode() {
        this("", "", 0L, GraphId.placeholder(), emptyMap(), emptyMap(), emptyList(), emptyList());
    }

    public MetaNode(String id,
                    String uri,
                    Long index,
                    GraphId graph,
                    Map<String, List<Permission>> permissions,
                    Map<String, List<Property>> properties,
                    List<AttributeMeta> textAttributes,
                    List<ReferenceMeta> referenceAttributes) {
        this.id = id;
        this.uri = uri;
        this.index = index;
        this.graph = graph;
        this.permissions = permissions;
        this.properties = properties;
        this.textAttributes = textAttributes;
        this.referenceAttributes = referenceAttributes;
    }

    public String getId() {
        return id;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public TypeId getDomain() {
        return new TypeId(NodeType.valueOf(id), graph, uri);
    }

    public Long getIndex() {
        return index;
    }

    public GraphId getGraph() {
        return graph;
    }

    public Map<String, List<Permission>> getPermissions() {
        return permissions;
    }

    public Map<String, List<Property>> getProperties() {
        return properties;
    }

    public List<AttributeMeta> getTextAttributes() {
        return textAttributes;
    }

    public AttributeMeta getAttribute(String name) {
        return textAttributes.stream()
                .filter(x -> x.getId().equals(name))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Attribute not found with name: " + name));
    }

    public boolean attributeExist(String name) {
        boolean rv = false;
        Optional<AttributeMeta> o = textAttributes.stream()
                .filter(x -> x.getId().equals(name))
                .findAny();
        if(o.isPresent())
            rv = true;
        return rv;
    }

    public void addAttribute(AttributeMeta attributeToAdd) {

        if (attributeToAdd.getIndex() != null) {
            incrementIndicesAfter(attributeToAdd.getIndex());
        }

        this.textAttributes.add(attributeToAdd);
        sortAttributes();
    }

    public void removeAttribute(String name) {

        AttributeMeta attributeToRemove = getAttribute(name);

        if (attributeToRemove.getIndex() != null) {
            decrementIndicesAfter(attributeToRemove.getIndex());
        }

        textAttributes.remove(attributeToRemove);
        sortAttributes();
    }

    public void changeAttributeIndex(String name, long newIndex) {

        AttributeMeta attribute = getAttribute(name);

        if (attribute.getIndex() != null) {
            decrementIndicesAfter(attribute.getIndex());
        }

        incrementIndicesAfter(newIndex);
        attribute.setIndex(newIndex);
        sortAttributes();
    }

    public List<ReferenceMeta> getReferenceAttributes() {
        return referenceAttributes;
    }

    public ReferenceMeta getReference(String name) {
        return referenceAttributes.stream()
                .filter(x -> x.getId().equals(name))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Reference not found with name: " + name));
    }

    public void addReference(ReferenceMeta referenceToAdd) {

        if (referenceToAdd.getIndex() != null) {
            incrementIndicesAfter(referenceToAdd.getIndex());
        }

        this.referenceAttributes.add(referenceToAdd);
        sortReferences();
    }

    public void removeReference(String name) {

        ReferenceMeta referenceToRemove = getReference(name);

        if (referenceToRemove.getIndex() != null) {
            decrementIndicesAfter(referenceToRemove.getIndex());
        }

        referenceAttributes.remove(referenceToRemove);
        sortReferences();
    }

    public void changeReferenceIndex(String name, long newIndex) {

        ReferenceMeta reference = getReference(name);

        if (reference.getIndex() != null) {
            decrementIndicesAfter(reference.getIndex());
        }

        incrementIndicesAfter(newIndex);
        reference.setIndex(newIndex);
        sortReferences();
    }

    private void incrementIndicesAfter(long index) {
        for (AttributeMeta attribute : textAttributes) {
            if (attribute.getIndex() != null && attribute.getIndex() >= index) {
                attribute.incrementIndex();
            }
        }

        for (ReferenceMeta reference : referenceAttributes) {
            if (reference.getIndex() != null && reference.getIndex() >= index) {
                reference.incrementIndex();
            }
        }
    }

    private void decrementIndicesAfter(long index) {
        for (AttributeMeta attribute : textAttributes) {
            if (attribute.getIndex() != null && attribute.getIndex() >= index) {
                attribute.decrementIndex();
            }
        }

        for (ReferenceMeta reference : referenceAttributes) {
            if (reference.getIndex() != null && reference.getIndex() >= index) {
                reference.decrementIndex();
            }
        }
    }

    private void sortAttributes() {
        this.textAttributes.sort(Comparator.comparing(AttributeMeta::getIndex));
    }

    private void sortReferences() {
        this.referenceAttributes.sort(Comparator.comparing(ReferenceMeta::getIndex));
    }

    public void updateLabel(String fi, String en) {
        updateProperties(prefLabel(fi, en));
    }

    public void updateProperties(Map<String, List<Property>> updatedProperties) {

        Map<String, List<Property>> newProperties = PropertyUtil.merge(this.properties, updatedProperties);

        properties.clear();
        properties.putAll(newProperties);
    }

    public MetaNode copyToGraph(UUID graphId) {

        List<AttributeMeta> newAttributes = mapToList(textAttributes, textAttribute -> textAttribute.copyToGraph(graphId));
        List<ReferenceMeta> newReferences = mapToList(referenceAttributes, referenceAttribute -> referenceAttribute.copyToGraph(graphId));

        return new MetaNode(id, uri, index, new GraphId(graphId), permissions, properties, newAttributes, newReferences);
    }

    public boolean isOfType(NodeType nodeType) {
        return this.id.equals(nodeType.name());
    }
}
