package fi.vm.yti.terminology.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.frontend.FrontendGroupManagementService;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.index.IndexElasticSearchService;
import fi.vm.yti.terminology.api.integration.containers.ContainersResponse;
import fi.vm.yti.terminology.api.integration.containers.Description;
import fi.vm.yti.terminology.api.integration.containers.PrefLabel;
import fi.vm.yti.terminology.api.model.integration.ConceptSuggestion;
import fi.vm.yti.terminology.api.model.termed.*;
import fi.vm.yti.terminology.api.security.AuthorizationManager;
import fi.vm.yti.terminology.api.util.JsonUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@Service
public class IntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationService.class);
    private final TermedRequester termedRequester;
    private final FrontendGroupManagementService groupManagementService;
    private final FrontendTermedService termedService;
    private final IndexElasticSearchService elasticSearchService;
    private final AuthenticatedUserProvider userProvider;
    /**
     * Map containing metadata types. used when creating nodes.
     */
    private HashMap<String, MetaNode> typeMap = new HashMap<>();

    @Autowired
    public IntegrationService(TermedRequester termedRequester, FrontendGroupManagementService groupManagementService,
            FrontendTermedService frontendTermedService, IndexElasticSearchService elasticSearchService,
            AuthenticatedUserProvider userProvider) {
        this.termedRequester = termedRequester;
        this.groupManagementService = groupManagementService;
        this.termedService = frontendTermedService;
        this.elasticSearchService = elasticSearchService;
        this.userProvider = userProvider;
    }

    ResponseEntity<String> handleContainers(String language, int pageSize, int from, String statusEnum, String after,
            boolean includeMeta) {
        if (logger.isDebugEnabled())
            logger.debug("GET /containers requested. status=" + statusEnum);

        // Response item list
        List<ContainersResponse> resp = new ArrayList<>();
        /**
         * Elastic query, returns 10k results from index and filter out items without URI
         */
        String query="{ \"query\" : {\"bool\":{\"must\": {\"match_all\" : {}},\"filter\": {\"exists\": { \"field\": \"uri\"} }}},\"size\":\"10000\",\"_source\":[\"id\",\"properties.prefLabel\",\"properties.description\",\"lastModifiedDate\",\"properties.status\",\"uri\"]}";
        if(logger.isDebugEnabled()){
            logger.debug("HandleVocabularies() query=" + query);
        }
        JsonNode result = elasticSearchService.freeSearchFromIndex(query, "vocabularies");
        JsonNode nodes = result.get("hits");
        if (nodes != null) {
            nodes = nodes.get("hits");
            nodes.forEach(hit -> {
                JsonNode source = hit.get("_source");
                if (source != null) {
                    // Some   vocabularies  has no status at all
                    String stat = null;
                    if (source.findPath("status") != null) {
                        stat = source.findPath("status").findPath("value").asText();
                    }

                    String modifiedDate =null;
                    if(source.get("lastModifiedDate") != null) {
                        modifiedDate = source.get("lastModifiedDate").asText();
                    }
                    // http://uri.suomi.fi/terminology/2/terminological-vocabulary-0
                    // Get uri and remove last part after /
                    String uri = null;
                    if(source.get("uri")!= null ){
                        uri = source.get("uri").asText();
                        // Remove code from uri so
                        uri = uri.substring(0,uri.lastIndexOf("/"))+"/";
                    }
                    ContainersResponse respItem = new ContainersResponse();
                    respItem.setUri(uri);
                    if(stat!=null && !stat.isEmpty()){
                        respItem.setStatus(stat);
                    }
                    if (modifiedDate != null) {
                        // Curently returns 2019-01-07T09:16:32.432+02:00
                        // use only first 19 chars
                        respItem.setModified(modifiedDate.substring(0, 19));
                    }
                    JsonNode label = source.findPath("prefLabel");
                    if (label != null) {
                        PrefLabel plab = new PrefLabel();
                        label.forEach(lb->{
                            String lan=null;
                            String val=null;
                            if(lb.findPath("lang") != null){
                                lan=lb.findPath("lang").asText();
                            }
                            if(lb.findPath("value") != null){
                                val=lb.findPath("value").asText();
                            }
                            if(lan != null){
                                if(lan.equalsIgnoreCase("fi")){
                                    plab.setFi(val);
                                } else if(lan.equalsIgnoreCase("en")){
                                    plab.setEn(val);
                                } else if(lan.equalsIgnoreCase("sv")){
                                    plab.setSv(val);
                                }
                            }
                        });
                        respItem.setPrefLabel(plab);
                    }

                    JsonNode description = source.findPath("description");
                    if (description != null) {
                        Description desc = new Description();
                        description.forEach(de->{
                            String lan=null;
                            String val=null;
                            if(de.findPath("lang") != null){
                                lan=de.findPath("lang").asText();
                            }
                            if(de.findPath("value") != null){
                                val=de.findPath("value").asText();
                                val=Jsoup.clean(val, Whitelist.none());
                            }
                            if(lan != null){
                                if(lan.equalsIgnoreCase("fi")){
                                    desc.setFi(val);
                                } else if(lan.equalsIgnoreCase("en")){
                                    desc.setEn(val);
                                } else if(lan.equalsIgnoreCase("sv")){
                                    desc.setSv(val);
                                }
                            }
                        }); 
                        respItem.setDescription(desc);
                    }
                    resp.add(respItem);
                } else {
                    logger.error("hit=" + hit);
                }
            });
        }
        return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(resp), HttpStatus.OK);
    }

    ResponseEntity<String> handleResources(String url) {
        if (logger.isDebugEnabled())
            logger.debug("GET /resources requested. URL=" + url);

        UUID id = null;
        List<Graph> vocs = termedService.getGraphs();
        for (Graph g : vocs) {
            if (g.getUri() != null && !g.getUri().isEmpty() && g.getUri().equals(url)) {
                id = g.getId();
            }
        }
        if (id == null) {
            return new ResponseEntity<>("{}", HttpStatus.NOT_FOUND);
        }
        // Id resolved, fetch vocabulary and filter out  vocabularies without URI
        /**
         * Elastic query, returns 10k results from index
         * 
         * {
         *     "query" : {
         *     	"bool":{
         *     		"must": {
         * 	            "match":  {
         * 	            	"vocabulary.id":"cd8fed1b-7f1c-4e2d-b307-a7662286f713"
         * 	            }
         *     		},
         *     		"filter": {
         *     			"exists": { "field": "uri"} 
         *     		}
         *     	}
         *     },
         *     "size":"10000",
         *     "_source":["id","properties.prefLabel","properties.description","lastModifiedDate","properties.status","uri"]
         * }
         * 
         * GET /_search
         */
        String query = "{\"query\": { \"bool\":{ \"must\":{ \"match\": { \"vocabulary.id\":\"" + id
                + "\" } },\"filter\": {\"exists\": {\"field\": \"uri\"}}}},\"size\":\"10000\", \"_source\":[\"id\",\"label\",\"definition\",\"modified\", \"status\",\"uri\"]}";

        JsonNode result = elasticSearchService.freeSearchFromIndex(query);
        // Response item list
        List<ContainersResponse> resp = new ArrayList<>();
        JsonNode nodes = result.get("hits");
        if (nodes != null) {
            nodes = nodes.get("hits");
            nodes.forEach(hit -> {
                JsonNode source = hit.get("_source");
                if (source != null) {
                    String stat = source.get("status").asText();
                    String modifiedDate = source.get("modified").asText();
                    String uri = source.get("uri").asText();

                    ContainersResponse respItem = new ContainersResponse();
                    respItem.setUri(uri);
                    respItem.setStatus(stat);
                    if (modifiedDate != null) {
                        // Curently returns 2019-01-07T09:16:32.432+02:00
                        // use only first 19 chars
                        respItem.setModified(modifiedDate.substring(0, 19));
                    }
                    JsonNode label = source.get("label");
                    if (label != null) {
                        PrefLabel plab = new PrefLabel();
                        // fi
                        JsonNode lan = label.get("fi");
                        if (lan != null) {
                            plab.setFi(Jsoup.clean(lan.get(0).asText(), Whitelist.none()));
                        }
                        // en
                        lan = label.get("en");
                        if (lan != null) {
                            plab.setEn(Jsoup.clean(lan.get(0).asText(), Whitelist.none()));
                        }
                        // sv
                        lan = label.get("sv");
                        if (lan != null) {
                            plab.setSv(Jsoup.clean(lan.get(0).asText(), Whitelist.none()));
                        }
                        respItem.setPrefLabel(plab);
                    }

                    JsonNode description = source.get("definition");
                    if (description != null) {
                        Description desc = new Description();
                        // fi
                        JsonNode d = description.get("fi");
                        if (d != null) {
                            desc.setFi(Jsoup.clean(d.get(0).asText(), Whitelist.none()));
                        }
                        // en
                        d = label.get("en");
                        if (d != null) {
                            desc.setEn(Jsoup.clean(d.get(0).asText(), Whitelist.none()));
                        }
                        // sv
                        d = label.get("sv");
                        if (d != null) {
                            desc.setSv(Jsoup.clean(d.get(0).asText(), Whitelist.none()));
                        }
                        respItem.setDescription(desc);
                    }
                    resp.add(respItem);
                } else {
                    System.out.println("hit=" + hit);
                }
            });
        }
        return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(resp), HttpStatus.OK);
    }

    /**
     * Initialize cached META-model. - Read given vocabularity for meta-types, cache
     * them
     *
     * @param vocabularityId UUID of the vocabularity
     */
    private void initMetaModel(UUID vocabularityId) {
        // Get metamodel types for given vocabularity
        List<MetaNode> metaTypes = termedService.getTypes(vocabularityId);
        metaTypes.forEach(t -> {
            typeMap.put(t.getId(), t);
        });
    }

    /**
     * Executes concept suggestion operation. Reads incoming json and process it
     *
     * @param vocabularityId
     * @return
     */
    ResponseEntity<String> handleConceptSuggestion(String vocabularityId, ConceptSuggestion incomingConcept) {
        if (logger.isDebugEnabled())
            logger.debug("POST /vocabulary/{vocabularyId}/concept requested. creating Concept for "
                    + JsonUtils.prettyPrintJsonAsString(incomingConcept));
        UUID activeVocabulary;
        // Concept reference-map
        Map<String, List<Identifier>> conceptReferences = new HashMap<>();

        // Get vocabularies and match code with name
        List<Graph> vocs = termedService.getGraphs();
        // Filter given code as result
        List<IdCode> vocabularies = vocs.stream().filter(o -> o.getCode().equalsIgnoreCase(vocabularityId)).map(o -> {
            return new IdCode(o.getCode(), o.getId());
        }).collect(Collectors.toList());
        if (vocabularies.size() > 1) {
            return new ResponseEntity<>(
                    "Created Concept suggestion failed for " + vocabularityId + ". Multiple matches for vocabulary. \n",
                    HttpStatus.NOT_FOUND);
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
                return new ResponseEntity<>(
                        "Created Concept suggestion failed for " + vocabularityId + ". Vocabulary not found. \n",
                        HttpStatus.NOT_FOUND);
            }
        }

        // Try to fetch it just to ensure it exist
        GenericNodeInlined vocabularyNode = termedService.getVocabulary(activeVocabulary);
        if (vocabularyNode == null) {
            return new ResponseEntity<>(
                    "Created Concept suggestion failed for UUID:" + vocabularityId + ". Vocabulary not found. \n",
                    HttpStatus.NOT_FOUND);
        }

        // get metamodel for vocabulary
        initMetaModel(activeVocabulary);
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
            termedService.bulkChangeWithoutAuthorization(operation, true,
                    UUID.fromString(incomingConcept.getCreator()));
            if (logger.isDebugEnabled())
                logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
            // Fetch created concept and get it's URI, set it to the returned json
            // Return also it's UUID
            GenericNode createdConcept = termedService.getConceptNode(activeVocabulary, concept.getId());
            incomingConcept.setUri(createdConcept.getUri());
            incomingConcept.setIdentifier(createdConcept.getId());
        }
        return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(incomingConcept), HttpStatus.OK);
    }

    private GenericNode CreateTerm(GenericNodeInlined vocabulary, ConceptSuggestion incoming,
            Map<String, List<Identifier>> parentReferences) {
        GenericNode node = null;
        // Populate term
        Map<String, List<Attribute>> properties = new HashMap<>();
        addProperty("prefLabel", properties, incoming.getPrefLabel());
        Attribute att = new Attribute("", "SUGGESTED");
        addProperty("status", properties, att);
        // Create Concept
        TypeId typeId = typeMap.get("Term").getDomain();
        node = new GenericNode(typeId, properties, emptyMap());

        // Add term as prefLabel for paren concept.
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
    private void addProperty(String attributeName, Map<String, List<Attribute>> properties, Attribute att) {
        if (!properties.containsKey(attributeName)) {
            List<Attribute> a = new ArrayList<>();
            a.add(att);
            properties.put(attributeName, a);
        } else
            properties.get(attributeName).add(att);
    }

    private GenericNode CreateConcept(GenericNodeInlined vocabulary, ConceptSuggestion incoming,
            Map<String, List<Identifier>> conceptReferences) {
        GenericNode node = null;
        Map<String, List<Attribute>> properties = new HashMap<>();
        addProperty("definition", properties, incoming.getDefinition());
        Attribute att = new Attribute("", "SUGGESTED");
        addProperty("status", properties, att);

        // Create Concept
        TypeId typeId = typeMap.get("Concept").getDomain();
        // Note! Autogenerated UUID
        node = new GenericNode(typeId, properties, conceptReferences);
        return node;
    }

    /**
     * Inner class for vocabulary-UUID pair.
     */
    private class IdCode {

        String code;
        UUID id;

        public IdCode(String code, UUID id) {
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
