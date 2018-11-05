package fi.vm.yti.terminology.api.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.frontend.FrontendGroupManagementService;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.model.integration.ConceptSuggestion;
import fi.vm.yti.terminology.api.model.termed.Attribute;
import fi.vm.yti.terminology.api.model.termed.GenericDeleteAndSave;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.GenericNodeInlined;
import fi.vm.yti.terminology.api.model.termed.Graph;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.security.AuthorizationManager;
import fi.vm.yti.terminology.api.util.JsonUtils;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@Service
public class IntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationService.class);
    private final TermedRequester termedRequester;
    private final FrontendGroupManagementService groupManagementService;
    private final FrontendTermedService termedService;
    private final AuthenticatedUserProvider userProvider;
    private final AuthorizationManager authorizationManager;
    private final String namespaceRoot;

    /**
     * Map containing metadata types. used  when creating nodes.
     */
    private HashMap<String, MetaNode> typeMap = new HashMap<>();

    @Autowired
    public IntegrationService(TermedRequester termedRequester,
                              FrontendGroupManagementService groupManagementService,
                              FrontendTermedService frontendTermedService,
                              AuthenticatedUserProvider userProvider,
                              AuthorizationManager authorizationManager,
                              @Value("${namespace.root}") String namespaceRoot) {
        this.termedRequester = termedRequester;
        this.groupManagementService = groupManagementService;
        this.termedService = frontendTermedService;
        this.userProvider = userProvider;
        this.authorizationManager = authorizationManager;
        this.namespaceRoot = namespaceRoot;
    }

    /**
     * Initialize importer.
     * - Read given vocabularity for meta-types, cache them
     * - Read all existing nodes and cache their URI/UUID-values
     *
     * @param vocabularityId UUID of the vocabularity
     */
    private void initImport(UUID vocabularityId) {
        // Get metamodel types for given vocabularity
        List<MetaNode> metaTypes = termedService.getTypes(vocabularityId);
        metaTypes.forEach(t -> {
            typeMap.put(t.getId(), t);
        });
    }

    /**
     * Executes import operation. Reads incoming xml and process it
     *
     * @param vocabularityId
     * @return
     */
    ResponseEntity handleConceptSuggestion(String vocabularityId,
                                           ConceptSuggestion incomingConcept) {
        if (logger.isDebugEnabled())
            logger.debug("POST /vocabulary/{vocabularyId}/concept requested. creating Concept for " + JsonUtils.prettyPrintJsonAsString(incomingConcept));
        UUID activeVocabulary;
        // Concept reference-map
        Map<String, List<Identifier>> conceptReferences = new HashMap<>();

        // Check rights
/*
        if(!authorizationManager.userHasRightsForCodelistConceptSuggestion()){
            return new ResponseEntity<>("Created Concept suggestion failed for "+ vocabularityId+". Not enought rights. \n", HttpStatus.UNAUTHORIZED);
        }
*/
        // Get vocabularies and match code with name
        List<Graph> vocs = termedService.getGraphs();
        // Filter given code as result
        List<IdCode> vocabularies = vocs.stream().filter(o -> o.getCode().equalsIgnoreCase(vocabularityId)).map(o -> {
            return new IdCode(o.getCode(), o.getId());
        }).collect(Collectors.toList());
        if (vocabularies.size() > 1) {
            return new ResponseEntity<>("Created Concept suggestion failed for " + vocabularityId + ". Multiple matches for vocabulary. \n", HttpStatus.NOT_FOUND);
        } else if (vocabularies.size() == 1) {
            // found, set UUID
            activeVocabulary = vocabularies.get(0).id;
        } else {
            // It may be UUID, so try to convert that
            // Try if it is UUID
            try {
                activeVocabulary = UUID.fromString(vocabularityId);
            } catch (IllegalArgumentException ex) {
                // Not UUID, error.
                return new ResponseEntity<>("Created Concept suggestion failed for " + vocabularityId + ". Vocabulary not found. \n", HttpStatus.NOT_FOUND);
            }
        }

        // Try to fetch it just to ensure it exist
        GenericNodeInlined vocabularyNode = termedService.getVocabulary(activeVocabulary);
        if (vocabularyNode == null) {
            return new ResponseEntity<>("Created Concept suggestion failed for UUID:" + vocabularityId + ". Vocabulary not found. \n", HttpStatus.NOT_FOUND);
        }

        // get metamodel for vocabulary
        initImport(activeVocabulary);
        // Create new Term
        GenericNode term = CreateTerm(vocabularyNode, incomingConcept, conceptReferences);
        // Create new Concept
        GenericNode concept = CreateConcept(vocabularyNode, incomingConcept, conceptReferences);
        if (term != null && concept != null) {
            incomingConcept.setVocabulary(vocabularyNode.getId());
            if (userProvider.getUser() != null && userProvider.getUser().getId() != null) {
                incomingConcept.setCreator(userProvider.getUser().getId().toString());
            }
            // Publish them to server
            List<GenericNode> addNodeList = new ArrayList<>();
            addNodeList.add(term);
            addNodeList.add(concept);
            GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(), addNodeList);
            termedService.bulkChangeWithoutAuthorization(operation, true, UUID.fromString(incomingConcept.getCreator()));
            if (logger.isDebugEnabled())
                logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
            // Fetch created concept and get it's URI, set it to the returned json
            GenericNode createdConcept = termedService.getConceptNode(activeVocabulary, concept.getId());
            incomingConcept.setUri(createdConcept.getUri());
        }
        return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(incomingConcept), HttpStatus.OK);
    }

    private GenericNode CreateTerm(GenericNodeInlined vocabulary,
                                   ConceptSuggestion incoming,
                                   Map<String, List<Identifier>> parentReferences) {
        GenericNode node = null;
        UUID termId = UUID.randomUUID();
        String code = termId.toString();

        // Populate term
        Map<String, List<Attribute>> properties = new HashMap<>();
        addProperty("prefLabel", properties, incoming.getPrefLabel());
        Attribute att = new Attribute("", "SUGGESTED");
        addProperty("status", properties, att);
        // Create Concept
        TypeId typeId = typeMap.get("Term").getDomain();
        node = new GenericNode(
            typeId,
            properties,
            emptyMap());

        // Add term as prefLabel for paren  concept.
        List<Identifier> ref;
        if (parentReferences.get("prefLabelXl") != null)
            ref = parentReferences.get("prefLabelXl");
        else
            ref = new ArrayList<>();
        ref.add(new Identifier(node.getId(), typeId));
        parentReferences.put("prefLabelXl", ref);
        return node;
    }

    /**
     * Add individual named attribute to property list
     *
     * @param attributeName like prefLabel
     * @param properties    Propertylist where attribute is added
     * @param att           Attribute to be added
     */
    private void addProperty(String attributeName,
                             Map<String, List<Attribute>> properties,
                             Attribute att) {
        if (!properties.containsKey(attributeName)) {
            List<Attribute> a = new ArrayList<>();
            a.add(att);
            properties.put(attributeName, a);
        } else
            properties.get(attributeName).add(att);
    }

    private GenericNode CreateConcept(GenericNodeInlined vocabulary,
                                      ConceptSuggestion incoming,
                                      Map<String, List<Identifier>> conceptReferences) {
        GenericNode node = null;
        UUID conceptId = UUID.randomUUID();
        String code = conceptId.toString();
        Map<String, List<Attribute>> properties = new HashMap<>();
        addProperty("definition", properties, incoming.getDefinition());
        Attribute att = new Attribute("", "SUGGESTED");
        addProperty("status", properties, att);

        // Create Concept
        TypeId typeId = typeMap.get("Concept").getDomain();
        // Note! Autogenerated UUID
        node = new GenericNode(
            typeId,
            properties,
            conceptReferences);
        return node;
    }


    /**
     * Inner class for vocabulary-UUID pair.
     */
    private class IdCode {

        String code;
        UUID id;

        public IdCode(String code,
                      UUID id) {
            this.code = code;
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

    }
}
