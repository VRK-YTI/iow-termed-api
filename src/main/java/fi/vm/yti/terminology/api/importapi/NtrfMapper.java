package fi.vm.yti.terminology.api.importapi;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.frontend.FrontendGroupManagementService;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.importapi.ImportStatusMessage.Level;
import fi.vm.yti.terminology.api.importapi.ImportStatusResponse.Status;
import fi.vm.yti.terminology.api.migration.DomainIndex;
import fi.vm.yti.terminology.api.model.ntrf.BCON;
import fi.vm.yti.terminology.api.model.ntrf.CLAS;
import fi.vm.yti.terminology.api.model.ntrf.DEF;
import fi.vm.yti.terminology.api.model.ntrf.DIAG;
import fi.vm.yti.terminology.api.model.ntrf.EQUI;
import fi.vm.yti.terminology.api.model.ntrf.GRAM;
import fi.vm.yti.terminology.api.model.ntrf.LANG;
import fi.vm.yti.terminology.api.model.ntrf.LINK;
import fi.vm.yti.terminology.api.model.ntrf.NCON;
import fi.vm.yti.terminology.api.model.ntrf.NOTE;
import fi.vm.yti.terminology.api.model.ntrf.RCON;
import fi.vm.yti.terminology.api.model.ntrf.RECORD;
import fi.vm.yti.terminology.api.model.ntrf.REF;
import fi.vm.yti.terminology.api.model.ntrf.REFERENCES;
import fi.vm.yti.terminology.api.model.ntrf.REFTEXT;
import fi.vm.yti.terminology.api.model.ntrf.REMK;
import fi.vm.yti.terminology.api.model.ntrf.SCOPE;
import fi.vm.yti.terminology.api.model.ntrf.SOURF;
import fi.vm.yti.terminology.api.model.ntrf.SUBJ;
import fi.vm.yti.terminology.api.model.ntrf.TERM;
import fi.vm.yti.terminology.api.model.ntrf.Termcontent;
import fi.vm.yti.terminology.api.model.ntrf.VOCABULARY;
import fi.vm.yti.terminology.api.model.termed.Attribute;
import fi.vm.yti.terminology.api.model.termed.GenericDeleteAndSave;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.Graph;
import fi.vm.yti.terminology.api.model.termed.GraphId;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.security.AuthorizationManager;
import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.util.Parameters;
import static fi.vm.yti.terminology.api.util.CollectionUtils.mapToList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpMethod.POST;

@Component
public class NtrfMapper {

    private static final String USER_PASSWORD = "user";
    private static final Pattern UUID_PATTERN = Pattern
        .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private final UUID NULL_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final TermedRequester termedRequester;
    private final FrontendGroupManagementService groupManagementService;
    private final FrontendTermedService termedService;
    private final AuthenticatedUserProvider userProvider;
    private final AuthorizationManager authorizationManager;
    private final String namespaceRoot;
    private final YtiMQService ytiMQService;

    // JMS-client
    private JmsMessagingTemplate jmsMessagingTemplate;

    /**
     * Map containing metadata types. used when creating nodes.
     */
    private HashMap<String, MetaNode> typeMap = new HashMap<>();
    /**
     * Map containing node.code or node.uri as a key and UUID as a value. Used for
     * matching existing items and updating them instead of creating new ones
     */
    private HashMap<String, UUID> idMap = new HashMap<>();
    private HashMap<UUID, String> reverseIdMap = new HashMap<>();
    /**
     * Map containing node.code or node.uri as a key and UUID as a value. Used for
     * reference resolving after all concepts and terms are created
     */
    private HashMap<String, UUID> createdIdMap = new HashMap<>();

    /**
     * Map binding together reference string and external URL fromn ntrf
     * SOURF-element
     */
    private HashMap<String, HashMap<String, String>> referenceMap = new HashMap<>();

    /**
     * Map for NCON/RCON-reference cache. Operation targetId,
     * type(generic/partitive), broaderConceptId
     */
    private Map<String, List<ConnRef>> nconList = new LinkedHashMap<>();
    private Map<String, List<ConnRef>> rconList = new LinkedHashMap<>();
    private Map<String, List<ConnRef>> bconList = new LinkedHashMap<>();

    private String currentRecord;
    // private Map<String, StatusMessage> statusList = new LinkedHashMap<>();
    private List<StatusMessage> statusList = new ArrayList<>();

    int errorCount = 0;

    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);

    // Enable for async operations
    @Autowired
    private TaskExecutor taskExecutor;
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    public NtrfMapper(TermedRequester termedRequester,
                      FrontendGroupManagementService groupManagementService,
                      FrontendTermedService frontendTermedService,
                      AuthenticatedUserProvider userProvider,
                      AuthorizationManager authorizationManager,
                      YtiMQService ytiMQService,
                      JmsMessagingTemplate jmsMessagingTemplate,
                      @Value("${namespace.root}") String namespaceRoot) {
        this.termedRequester = termedRequester;
        this.groupManagementService = groupManagementService;
        this.termedService = frontendTermedService;
        this.userProvider = userProvider;
        this.authorizationManager = authorizationManager;
        this.ytiMQService = ytiMQService;
        this.jmsMessagingTemplate = jmsMessagingTemplate;
        this.namespaceRoot = namespaceRoot;
    }

    private boolean updateAndDeleteInternalNodes(UUID userId,
                                                 GenericDeleteAndSave deleteAndSave,
                                                 boolean sync) {

        boolean rv = true;
        Parameters params = new Parameters();
        params.add("changeset", "true");
        params.add("sync", String.valueOf(sync));
        try {
            this.termedRequester.exchange("/nodes", POST, params, String.class, deleteAndSave, userId.toString(),
                USER_PASSWORD);
        } catch (HttpServerErrorException ex) {
            String error = ex.getResponseBodyAsString();
            logger.error("Termed status:" + error);
            Pattern pairRegex = Pattern
                .compile("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");
            Matcher matcher = pairRegex.matcher(error);
            List<UUID> reflist = new ArrayList<>();
            while (matcher.find()) {
                String a = matcher.group(0);
                reflist.add(UUID.fromString(a));
            }
            if (reflist.size() > 1) {
                logger.error("Failed UUID=" + reflist.get(1) + " Code:" + reverseIdMap.get(reflist.get(1)));
            }

            // statusList.put("Termed block-operation:" + errorCount,
            // new StatusMessage(Level.ERROR, currentRecord, "Termed error:" +
            // ex.getResponseBodyAsString()));
            statusList
                .add(new StatusMessage(Level.ERROR, currentRecord, "Termed error:" + ex.getResponseBodyAsString()));
            errorCount++;
            rv = false;
        }
        return rv;
    }

    /**
     * Executes import operation. Reads incoming xml and process it
     *
     * @param terminologyId
     * @param ntrfDocument
     * @return
     */
    String mapNtrfDocument(String jobtoken,
                           UUID terminologyId,
                           VOCABULARY ntrfDocument,
                           UUID userId) {
        GenericNode terminology = null;
        logger.info("mapNtRfDocument: Vocabularity Id:" + terminologyId);
        System.out.println("mapNtRfDocument: Vocabularity Id:" + terminologyId);
        Long startTime = new Date().getTime();

        idMap.clear();
        reverseIdMap.clear();
        createdIdMap.clear();
        nconList.clear();
        bconList.clear();
        rconList.clear();

        // Get terminology
        try {
            terminology = termedService.getNode(terminologyId);
        } catch (NullPointerException nex) {
            // Vocabularity not found
            logger.error("Vocabulary:<" + terminologyId + "> not found");
            return "Vocabulary:<" + terminologyId + "> not found";
        }

        if (!initImport(terminologyId)) {
            return "Vocabulary:<" + terminologyId + "> initialization error";
        }

        // Get statistic of terms
        List<?> l = ntrfDocument.getRECORDAndHEADAndDIAG();
        logger.info("Incoming objects count=" + l.size());

        // Get all reference-elements and build reference-url-map
        List<REFERENCES> externalReferences = l.stream().filter(o -> o instanceof REFERENCES).map(o -> (REFERENCES) o)
            .collect(Collectors.toList());
        handleReferences(externalReferences, referenceMap);
        logger.info("Incoming reference count=" + externalReferences.size());

        // Get all records (mapped to terms) from incoming ntrf-document. Check object
        // type and typecast matching objects to list<>
        List<RECORD> records = l.stream().filter(o -> o instanceof RECORD).map(o -> (RECORD) o)
            .collect(Collectors.toList());
        logger.info("Incoming records count=" + records.size());
        List<GenericNode> addNodeList = new ArrayList<>();
        List<Identifier> deleteNodeList = new ArrayList<>();

        ImportStatusResponse response = new ImportStatusResponse();
        response.setStatus(Status.PROCESSING);
        response.addStatusMessage(new ImportStatusMessage("Vocabulary", "Import started"));
        response.setProcessingTotal(records.size());
        response.setProcessingProgress(0);

        ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, jobtoken, userId.toString(), terminology.getUri(),
            response.toString());
        int flushCount = 0;
        int currentCount = 0;

        for (RECORD o : records) {
            currentRecord = o.getNumb();
            handleRECORD(terminology, o, addNodeList, deleteNodeList);
            flushCount++;
            currentCount++;
            response.setStatus(Status.PROCESSING);
            response.clearStatusMessages(); // Forget previous
            response.addStatusMessage(new ImportStatusMessage("Vocabulary", "Processing records"));
            response.setProcessingProgress(currentCount);
            response.setResultsError(errorCount);
            ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, jobtoken, userId.toString(), terminology.getUri(),
                response.toString());
            // Flush datablock to the termed
            if (flushCount > 100) {
                flushCount = 0;
                GenericDeleteAndSave operation = new GenericDeleteAndSave(deleteNodeList, addNodeList);
//                GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(), addNodeList);
                if (logger.isDebugEnabled())
                    logger.debug(JsonUtils.prettyPrintJsonAsString(operation));

                response.setStatus(Status.PROCESSING);
                response.clearStatusMessages(); // Forget previous
                if (!updateAndDeleteInternalNodes(userId, operation, true)) {
                    response.addStatusMessage(new ImportStatusMessage("Vocabulary",
                        "Processing records, import failed for " + currentRecord));
                    errorCount++;
                } else {
                    response.addStatusMessage(new ImportStatusMessage("Vocabulary", "Processing records"));
                    // Import successfull, add id:s to resolved one.
                    addNodeList.forEach(node -> {
                        // Add id for reference resolving
                        createdIdMap.put(node.getCode(), node.getId());
                    });
                }
                response.setProcessingProgress(currentCount);
                ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, jobtoken, userId.toString(), terminology.getUri(),
                    response.toString());

                addNodeList.clear();
            }
        }
        JsonUtils.prettyPrintJson(addNodeList);
        GenericDeleteAndSave operation = new GenericDeleteAndSave(deleteNodeList, addNodeList);
//        GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(), addNodeList);
        if (logger.isDebugEnabled())
            logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
        if (!updateAndDeleteInternalNodes(userId, operation, true)) {
            response.addStatusMessage(
                new ImportStatusMessage("Vocabulary", "Processing records, import failed for " + currentRecord));
        } else {
            // Import successfull, add id:s to resolved one.
            addNodeList.forEach(v -> {
                // Add id for reference resolving
                createdIdMap.put(v.getCode(), v.getId());
            });
        }
        addNodeList.clear();
        // ReInitialize caches and after that, resolve rcon- and ncon-references
        idMap.clear();
        typeMap.clear();
        initImport(terminologyId);
        // Just add reverse map
        idMap.forEach((k, v) -> {
            reverseIdMap.put(v, k);
        });

        handleLinks(userId, jobtoken, terminology);
        /*
         * idMap.clear(); typeMap.clear(); initImport(vocabularyId);
         */
        // Handle DIAG-elements and create collections from them
        List<DIAG> DIAGList = l.stream().filter(o -> o instanceof DIAG).map(o -> (DIAG) o).collect(Collectors.toList());
        if(logger.isDebugEnabled()){
            logger.debug("DIAG-count=" + DIAGList.size());
        }
        for (DIAG o : DIAGList) {
            handleDIAG(terminology, o, addNodeList);
        }
        response.setStatus(Status.PROCESSING);
        response.addStatusMessage(new ImportStatusMessage("Vocabulary", "Processing DIAG number=" + DIAGList.size()));
        response.setProcessingProgress(records.size());
        ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, jobtoken, userId.toString(), terminology.getUri(),
            response.toString());
        // Add DIAG-list to vocabulary
        operation = new GenericDeleteAndSave(emptyList(), addNodeList);
        if (logger.isDebugEnabled())
            logger.debug(JsonUtils.prettyPrintJsonAsString(operation));

        if (!updateAndDeleteInternalNodes(userId, operation, true)) {
            logger.error("Diag termed error");
        }

        Long endTime = new Date().getTime();
        logger.info("Operation  took " + (endTime - startTime) / 1000 + "s");
        logger.info("NTRF-imported " + records.size() + " concepts.");
        response.clearStatusMessages();
        // Add all status lines as individual members before
      
        statusList.forEach(v -> {
            StatusMessage m = (StatusMessage) v;
            response.addStatusMessage(new ImportStatusMessage(m.getLevel(), m.getRecord(), m.getMessage().toString()));
            logger.info("Item : " + m.getRecord() + " value : " + m.getMessage().toString());
        });

        response.setProcessingTotal(records.size());
        response.setProcessingProgress(records.size());
        response.setResultsWarning(statusList.size());
        response.setResultsError(errorCount);

        if (errorCount > 0) {
            response.setStatus(Status.FAILURE);

        } else if (statusList.size() > 0) {
            response.setStatus(Status.SUCCESS_WITH_ERRORS);
        } else {
            response.setStatus(Status.SUCCESS);
        }
        // ImportStatusResponse
        // test=ImportStatusResponse.fromString(JsonUtils.prettyPrintJsonAsString(response));
        ytiMQService.setStatus(YtiMQService.STATUS_READY, jobtoken, userId.toString(), terminology.getUri(),
            response.toString());
        statusList.clear();
        return response.toString();
    }

    private void addConMap(Map<String, List<ConnRef>> conMap,
                           String connType,
                           UUID userId,
                           String jobtoken,
                           GenericNode terminology) {
        List<GenericNode> addNodeList = new ArrayList<>();
        conMap.forEach((k, v) -> {
            String key = (String) k;
            List<ConnRef> rlist = (List<ConnRef>) v;
            // Resolve source id.
            UUID sourceId = createdIdMap.get(key);
            if (sourceId != null) {
                // Fetch node for update
                GenericNode gn = null;
                try {
                    gn = termedService.getConceptNode(sourceId);
//                    gn = termedService.getConceptNode(terminology.getId(), sourceId);
                } catch (NullPointerException nex) {
                    logger.warn("Can't found concept node:" + key + " id:" + sourceId + " in vocabulary:"
                        + terminology.getId().toString());
                }
                if (gn != null) {
                    Map<String, List<Identifier>> refMap = gn.getReferences();
                    List<Identifier> idref = null;
                    if(logger.isDebugEnabled()){
                        logger.debug("Add " + connType + " list to " + key + " size:" + rlist.size());
                    }
                    // Iterate through list and add them to node
                    // BCON broader / isPartOf (generic/partitive) default is broader
                    // RCON no reftype-definition so always related
                    // NCON narrower/hasPart (generic/partitive) default is narrover
                    String refListName = "";
                    for (ConnRef ref : rlist) {
                        if (connType.equalsIgnoreCase("BCON")) {
                            // Default is generic -> broader
                            idref = refMap.get("broader");
                            refListName = "broader";
                            if (ref.getType() != null && ref.getType().equalsIgnoreCase("partitive")) {
                                idref = refMap.get("isPartOf");
                                refListName = "isPartOf";
                            }
                        } else if (connType.equalsIgnoreCase("NCON")) {
                            // default is generic->narrower
                            idref = refMap.get("narrower");
                            refListName = "narrower";
                            if (ref.getType() != null && ref.getType().equalsIgnoreCase("partitive")) {
                                idref = refMap.get("hasPart");
                                refListName = "hasPart";
                            }
                        } else {
                            // RCON
                            idref = refMap.get("related");
                            refListName = "related";
                        }

                        if (idref == null) {
                            idref = new ArrayList<>();
                        }
                        // Use name and resolve target id using it.
                        UUID refId = idMap.get(ref.getReferenceString());
                        if (refId != null) {
                            ref.setTargetId(refId);
                        } else {
                            logger.error("Can't resolve id for " + ref.getReferenceString());
                        }

                        // @TODO! Go through refList and add only if missing.

                        if (!ref.getTargetId().equals(NULL_ID)) {
                            if (sourceId.equals(ref.getTargetId())) {
                                logger.error("Self-reference removed from " + key + " id:" + sourceId);
                                statusList.add(new StatusMessage(key,
                                    "Self-reference removed from " + key + " id:" + sourceId));
                            } else {
                                idref.add(new Identifier(ref.getTargetId(), typeMap.get("Concept").getDomain()));
                                // Put back int the correct list
                                refMap.put(refListName, idref);
                                logger.error(refListName + "->" + ref.getReferenceString() + "  "
                                    + ref.getTargetId().toString());
                            }
                        } else {
                            logger.error("Ref-target-id not found for :" + ref.getCode());
                            // statusList.put(currentRecord,
                            // new StatusMessage(currentRecord, connType + " Ref-target-id not found for : "
                            // + ref.getCode()));
                            statusList.add(new StatusMessage(currentRecord,
                                connType + " Ref-target-id not found for : " + ref.getCode()));
                        }
                    }
                    // Add it back to termed
                    addNodeList.add(gn);
                } else {
                    logger.warn("Cant' resolve following! " + key + " type:" + connType + "=" + sourceId + "-- vocab="
                        + terminology.getId().toString());
                    // statusList.put(currentRecord,
                    // new StatusMessage(currentRecord, connType + " reference match failed. for " +
                    // key));
                    statusList.add(new StatusMessage(currentRecord, connType + " reference match failed. for " + key));
                }
            } else {
                logger.error("Can't find source id:" + key);
            }
        });
        if (addNodeList.size() > 0) {
            // add (N/B/R)CON-changes as one big block

            GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(), addNodeList);
            if (logger.isDebugEnabled())
                logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
            if (!updateAndDeleteInternalNodes(userId, operation, true)) {
                logger.error("CONN link adding: Termed error ");
            }
        }
        addNodeList.clear();
    }

    /**
     * After Concept and Term creation, add missing references
     *
     * @param userId
     * @param jobtoken
     * @param terminology
     */
    private void handleLinks(UUID userId,
                             String jobtoken,
                             GenericNode terminology) {
        if (nconList != null) {
            addConMap(nconList, "NCON", userId, jobtoken, terminology);
        }
        if (rconList != null) {
            if (rconList.size() > 0) {
                addConMap(rconList, "RCON", userId, jobtoken, terminology);
            }
        }
        if (bconList != null) {
            if (bconList.size() > 0) {
                addConMap(bconList, "BCON", userId, jobtoken, terminology);
            }
        }
    }

    /**
     * Initialize importer. - Read given vocabularity for meta-types, cache them -
     * Read all existing nodes and cache their URI/UUID-values
     *
     * @param vocabularityId UUID of the vocabularity
     */
    private boolean initImport(UUID terminologyId) {
        // Get metamodel types for given vocabularity
        List<MetaNode> metaTypes = termedService.getMetaTypes();
        metaTypes.forEach(t -> typeMap.put(t.getId(), t));

        // Create hashmap to store information between code/URI and UUID so that we can
        // update values upon same vocabularity
        List<GenericNode> nodeList = termedService.getTerminologyNodes(terminologyId);
        if (nodeList != null) {
            nodeList.forEach(o -> {
                if (logger.isDebugEnabled())
                    logger.debug(" Code:" + o.getCode() + " UUID:" + o.getId().toString() + " URI:" + o.getUri());
                if (o.getCode() != null && !o.getCode().isEmpty()) {
                    idMap.put(o.getCode(), o.getId());
                }
                if (o.getUri() != null && !o.getUri().isEmpty()) {
                    idMap.put(o.getUri(), o.getId());
                }
            });
            return true;
        }
        return false;
    }

    private void handleDIAG(GenericNode terminology,
                            DIAG diag,
                            List<GenericNode> addNodeList) {
        String code = diag.getNumb();
        if (logger.isDebugEnabled())
            logger.debug("DIAG Name=" + diag.getName() + " Code=" + code);

        UUID collectionId = idMap.get(code);
        // Generate new if not update
        if (collectionId == null)
            collectionId = UUID.randomUUID();
        // references list for colection member concepts
        Map<String, List<Identifier>> references = new HashMap<>();
        // Construct empty member list
        List<Identifier> memberRef = new ArrayList<>();
        // Construct properties map and add name as preflabel
        Map<String, List<Attribute>> properties = new HashMap<>();
        // Default lang is finnish at the time
        addProperty("prefLabel", properties, new Attribute("fi", diag.getName()));

        // get links and add them to references/member map

        List<LINK> l = diag.getLINK();
        l.forEach(li -> {
            String linkTarget = li.getHref();
            currentRecord = diag.getNumb();
            // Remove #
            if (linkTarget.startsWith("#"))
                linkTarget = linkTarget.substring(1);

            UUID targetUUID = idMap.get(linkTarget);
            if (targetUUID == null) {
                // try original
                targetUUID = idMap.get(li.getHref());
            }
            if (targetUUID != null && !targetUUID.equals(NULL_ID)) {
                memberRef.add(new Identifier(targetUUID, typeMap.get("Concept").getDomain()));
                references.put("member", memberRef);
            } else {
                logger.warn("DIAG:" + diag.getNumb() + " LINK-target " + li.getHref() + " <"
                    + parseHrefText(li.getContent()) + "> not added into the collection");
                // statusList.put(currentRecord, new StatusMessage(currentRecord, "DIAG:" +
                // diag.getNumb()
                // + " LINK-target " + li.getHref() + " <" + li.getContent() + "> not added into
                // the collection"));
                statusList.add(new StatusMessage(currentRecord, "DIAG:" + diag.getNumb() + " LINK-target "
                    + li.getHref() + " <" + parseHrefText(li.getContent()) + "> not added into the collection"));
            }

        });

        // Construct Node as Collection-type
        String uri = terminology.getUri().endsWith("/")? terminology.getUri() +"/"+ code: terminology.getUri() + code;
        GenericNode node = new GenericNode(collectionId, code, uri, 0L,
            userProvider.getUser().getUsername(), new Date(), "", new Date(), typeMap.get("Collection").getDomain(),
            properties, references, emptyMap());
        // Just add it
        addNodeList.add(node);
    }

    /**
     * TSK-NTRF packs external references as REFERENCES-items. Here we go through
     * them and cache values for later usage
     *
     * @param referencesTypeList
     * @param refMap
     */
    private void handleReferences(List<REFERENCES> referencesTypeList,
                                  HashMap<String, HashMap<String, String>> refMap) {
        referencesTypeList.forEach(reftypes -> {
            List<REF> refs = reftypes.getREFOrREFHEAD().stream().filter(o -> o instanceof REF).map(o -> (REF) o)
                .collect(Collectors.toList());
            refs.forEach(r -> {
                // Filter all JAXBElements

                List<JAXBElement> elems = null;
                HashMap<String, String> fields = new HashMap<>();
                String name = r.getREFNAME();
                String url = r.getREFLINK();
                String text = "";

                // Text is a structured component
                // In DTD this is a string or REMK.... <!ELEMENT REFTEXT (#PCDATA | REMK | B | I
                // | BR | LINK)*>
                REFTEXT rtx = r.getREFTEXT();
                elems = rtx.getContent().stream().filter(o -> o instanceof JAXBElement).map(o -> (JAXBElement) o)
                    .collect(Collectors.toList());
                for (JAXBElement o : elems) {
                    if (o.getName().toString().equalsIgnoreCase("REFNAME")) {
                        name = o.getValue().toString();
                    }
                    if (o.getName().toString().equalsIgnoreCase("REFTEXT")) {
                        REFTEXT rt = (REFTEXT) o.getValue();
                        // Can be String | REMK | B | I | BR | LINK
                        // @TODO! add handling
                        text = rt.getContent().toString();
                        fields.put("text", text);
                    }
                    if (o.getName().toString().equalsIgnoreCase("REFLINK")) {
                        if (!o.getValue().toString().isEmpty()) {
                            url = o.getValue().toString();
                            fields.put("url", url);
                        }
                    }
                    if (logger.isDebugEnabled())
                        logger.debug(" Cache incoming external references: field=" + fields);
                }
                ;
                // add fields to referenceMap
                if (name != null) {
                    refMap.put(name, fields);
                }
            });
        });
    }

    /**
     * Remove orphan terms under given concept and graph
     */
    private void cleanTerms(UUID graphId,
                            UUID conceptId,
                            List<Identifier> deleteNodeList) {
        logger.info("clearTerm from:" + graphId + " concept:" + conceptId.toString());
        // Get concept
        GenericNode node = termedService.getConceptNode(conceptId);
        if (node != null) {
            // get references and delete terms and synonyms
            Map<String, List<Identifier>> references = node.getReferences();
            List<Identifier> terms = references.get("prefLabelXl");
            if (terms != null) {
                terms.forEach(id -> {
                    deleteNodeList.add(id);
                });
            }
            // Synonyms
            terms = references.get("altLabelXl");
            if (terms != null) {
                terms.forEach(id -> {
                    deleteNodeList.add(id);
                });
            }
            // notRecommendedSynonym
            terms = references.get("notRecommendedSynonym");
            if (terms != null) {
                terms.forEach(id -> {
                    deleteNodeList.add(id);
                });
            }
        }
    }

    /**
     * Handle mapping of RECORD. NTRF-models all concepts as RECORD-fields. It
     * contains actual terms and references to other concepts and external links.
     * See following example incomimg NTRF:
     * <RECORD numb="tmpOKSAID116" upda="Riina Kosunen, 2018-03-16">
     * <LANG value="fi"> <TE> <TERM>kasvatus</TERM> <SOURF>SRemes</SOURF> </TE>
     * <DEF>vuorovaikutukseen perustuva toiminta, jonka tavoitteena on kehittää
     * yksilöstä eettisesti vastuukykyinen yhteiskunnan jäsen<SOURF>wikipedia + rk +
     * pikkuryhma_01 + tr45 + tr49 + ssu + vaka_tr_01 + vaka_tr_02 +
     * tr63</SOURF></DEF> <NOTE>Kasvatuksen myötä kulttuuriset arvot, tavat ja
     * normit välittyvät ja muovautuvat. Osaltaan kasvatuksen tavoite on siirtää
     * kulttuuriperintöä sekä tärkeinä pidettyjä arvoja ja traditioita seuraavalle
     * sukupolvelle, mutta kasvatuksen avulla halutaan myös uudistaa ajattelu- ja
     * toimintatapoja. Kasvatuksen sivistystehtävänä on tietoisesti ohjata
     * yksilöllisen identiteetin muotoutumista ja huolehtia, että muotoutuminen
     * tapahtuu sosiaalisesti hyväksyttävällä tavalla.<SOURF>vaka_tr_02 +
     * tr63</SOURF></NOTE> <NOTE>Varhaiskasvatuksella tarkoitetaan
     * varhaiskasvatuslain (<LINK href=
     * "https://www.finlex.fi/fi/laki/ajantasa/1973/19730036">36/1973</LINK>) mukaan
     * lapsen suunnitelmallista ja tavoitteellista kasvatuksen,
     * <RCON href="#tmpOKSAID117">opetuksen (1)</RCON> ja hoidon muodostamaa
     * kokonaisuutta, jossa painottuu pedagogiikka.<SOURF>vk-peruste + ssu +
     * vaka_tr_02</SOURF></NOTE>
     * <NOTE><RCON href="#tmpOKSAID452">Perusopetuksella</RCON> on
     * opetustavoitteiden lisäksi kasvatustavoitteita. Perusopetuslain (<LINK href=
     * "https://www.finlex.fi/fi/laki/ajantasa/1998/19980628">628/1998</LINK>)
     * mukaan perusopetuksella pyritään kasvattamaan oppilaita ihmisyyteen ja
     * eettisesti vastuukykyiseen yhteiskunnan jäsenyyteen sekä antamaan heille
     * elämässä tarpeellisia tietoja ja taitoja.<SOURF>rk + pikkuryhma_01 +
     * tr45</SOURF></NOTE> <NOTE>Yliopistolain (<LINK href=
     * "https://www.finlex.fi/fi/laki/ajantasa/2009/20090558">558/2009</LINK>)
     * mukaan <RCON href="#tmpOKSAID162">yliopistojen</RCON> tehtävänä on edistää
     * vapaata tutkimusta sekä tieteellistä ja taiteellista sivistystä, antaa
     * tutkimukseen perustuvaa ylintä opetusta (1) sekä kasvattaa
     * <RCON href="#tmpOKSAID227">opiskelijoita</RCON> palvelemaan isänmaata ja
     * ihmiskuntaa.<SOURF>558/2009 + tr45</SOURF></NOTE> <NOTE>Englannin käsite
     * education on laajempi kuin suomen kasvatus, niin että termillä education
     * viitataan kasvatuksen lisäksi muun muassa
     * <RCON href="#tmpOKSAID117">opetukseen (1)</RCON>,
     * <RCON href="#tmpOKSAID121">koulutukseen (1)</RCON> ja sivistykseen.<SOURF>rk
     * + KatriSeppala</SOURF></NOTE> <NOTE>Käsitteen tunnus: tmpOKSAID116</NOTE>
     * </LANG> <LANG value="sv"> <TE> <TERM>fostran</TERM> <SOURF>36/1973_sv +
     * kielityoryhma_sv_04</SOURF> </TE> </LANG> <LANG value="en"> <TE>
     * <EQUI value="broader"></EQUI> <TERM>education</TERM> <HOGR>1</HOGR>
     * <SOURF>ophall_sanasto</SOURF> </TE> <SY> <TERM>upbringing</TERM>
     * <SOURF>MOT_englanti</SOURF> </SY> </LANG>
     * <BCON href="#tmpOKSAID122" typr="generic">koulutus (2)</BCON>
     * <NCON href="#tmpOKSAID123" typr="generic">koulutuksen toteutus</NCON>
     * <CLAS>yleinen/yhteinen</CLAS> <CHECK>hyväksytty</CHECK> </RECORD>
     *
     * @param vocabularity Graph-node from termed. It contains base-uri where esck
     *                     Concept and Term is bound
     * @param r
     */
    void handleRECORD(GenericNode terminology,
                      RECORD r,
                      List<GenericNode> addNodeList,
                      List<Identifier> deleteNodeList) {
        String code = "";
        UUID currentId = null;
        String createdBy;
        LocalDate createdDate;
        String lastModifiedBy = null;
        LocalDate lastModifiedDate = LocalDate.now();
        // Attributes are stored to property-list
        Map<String, List<Attribute>> properties = new HashMap<>();
        // references synomyms and preferred tems and so on
        Map<String, List<Identifier>> references = new HashMap<>();

        code = r.getNumb();
        // Check whether id exist and create id
        if (idMap.get(code) != null) {
            if (logger.isDebugEnabled()) {
                logger.debug(" UPDATE operation!!!!!!!!! " + code);
            }
            currentId = idMap.get(code);
            // Delete terms from existing concept before updating content
            cleanTerms(terminology.getId(), currentId, deleteNodeList);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug(" CREATE NEW  operation!!!!!!!!! " + code);
            }
            currentId = UUID.randomUUID();
        }

        // Default creator is importing user
        createdBy = userProvider.getUser().getUsername();

        logger.info("Record id:" + code);
        // Add info to editorial note
        String editorialNote = "";

        // Stat can be 'vanhentunut', 'aputermi', 'ulottuvuus'
        if (r.getStat() != null) {
            editorialNote = r.getStat();
            // Drop ulottuvuus and continue
            if (r.getStat().equalsIgnoreCase("Ulottuvuus")) {
                logger.warn("Dropping 'ulottuvuus' type node");
                // statusList.put(currentRecord, new StatusMessage(currentRecord, "Dropping
                // 'ulottuvuus' type record"));
                statusList.add(new StatusMessage(currentRecord, "Dropping 'ulottuvuus' type record"));
                return;
            }
        }
        if (r.getUpda() != null) {
            // Store that information to the modificationHistory
            String updater = r.getUpda();
            String upd[] = r.getUpda().split(",");
            if (upd.length == 2) {
                DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                // User, update date
                lastModifiedBy = upd[0].trim();
                try {
                    lastModifiedDate = LocalDate.parse(upd[1].trim(), df);
                    // editorialNote = editorialNote+" -"+lastModifiedBy+", "+lastModifiedDate;
                    editorialNote = editorialNote + " - Viimeksi muokattu, " + lastModifiedDate;
                } catch (DateTimeParseException dex) {
                    // statusList.put(currentRecord,
                    // new StatusMessage(currentRecord, "Parse error for date" + dex.getMessage()));
                    statusList.add(new StatusMessage(currentRecord, "Parse error for date" + dex.getMessage()));
                    logger.error("Parse error for date" + dex.getMessage());
                }
            }
        }
        if (!editorialNote.isEmpty()) {
            Attribute att = new Attribute("fi", editorialNote);
            addProperty("editorialNote", properties, att);
        }

        // Resolve terms and collect list of them for insert.
        List<GenericNode> terms = new ArrayList<>();
        // Needs to be final for lambda
        final UUID concept = currentId;
        // Filter LANG elemets as list.
        List<LANG> langs = r.getLANG();
        langs.forEach(o -> {
            // RECORD/LANG/TE/TERM -> prefLabel
            hadleLANG(concept, terms, o, properties, references, terminology);
        });

        // Handle Subject
        List<SUBJ> subjs = r.getSUBJ();
        subjs.forEach(o -> {
            handleSUBJ(o, properties);
        });

        // Filter CLAS elemets as list
        List<CLAS> clas = r.getCLAS();
        clas.forEach(o -> {
            // RECORD/CLAS ->
            handleCLAS(o, properties);
        });
        // Filter CHECK elemets as list
        if (r.getCHECK() != null && !r.getCHECK().isEmpty()) {
            // RECORD/CHECK ->
            handleCHECK(r.getCHECK(), r.getStat(), properties);
        }

        // stat-attribute overrides CHECK
        if (r.getStat() != null && !r.getStat().isEmpty()) {
            handleStat(r.getStat(), properties);
        }
        if (r.getREMK() != null) {
            r.getREMK().forEach(o -> {
                handleREMK("", o, properties, terminology);
            });
        }
        // Filter BCON elemets as list
        List<BCON> bcon = r.getBCON();
        for (BCON o : bcon) {
            // RECORD/BCON
            handleBCON(currentId, o);
        }
        ;
        List<RCON> rcon = r.getRCON();
        for (RCON o : rcon) {
            // RECORD/RCON
            handleRCON(currentId, o);
        }
        ;
        // Filter NCON elemets as list
        List<NCON> ncon = r.getNCON();
        for (NCON o : ncon) {
            handleNCON(currentId, o);
        }
        TypeId typeId = null;
        typeId = typeMap.get("Concept").getDomain();
        GenericNode node = null;
        String uri = terminology.getUri().endsWith("/")? terminology.getUri()+code:terminology.getUri()+"/"+code ;
        node = new GenericNode(currentId, code, uri, 0L, createdBy, new Date(), "", new Date(),
            typeId, properties, references, emptyMap());
        // Add parent  terminology for  node 
        node.addDefinedInScheme(terminology.getId(), new TypeId(NodeType.TerminologicalVocabulary, new GraphId(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID)));
        // Send item to termed-api
        // First add terms
        terms.forEach(t -> {
            addNodeList.add(t);
        });
        // then concept itself
        addNodeList.add(node);
    }

    /**
     * Handle mapping of individual Lang-element. This contains Terms which needs to
     * be created and definition and some other information which should be stored
     * under parent concept prefLabel value is found under LANG/TE/TERM
     * parent.definition is under LANG/TE/DEF parent.source-elements are found under
     * LANG/TE/SOURF, LANG/TE/DEF/SOURF and LANG/TE/NOTE/SOURF all of them are
     * mapped to same source-list Incoming NTRF: <LANG value="fi"> <TE>
     * <TERM>opetus</TERM> <HOGR>1</HOGR> <SOURF>harmon + tr45</SOURF> </TE>
     * <DEF>vuorovaikutukseen perustuva toiminta, jonka tavoitteena on
     * <RCON href="#tmpOKSAID118">oppiminen</RCON><SOURF>wikipedia + rk +
     * tr45</SOURF></DEF> <NOTE>Opetuksella (1) ja
     * <RCON href="#tmpOKSAID116">kasvatuksella</RCON> on osin yhteneväisiä
     * tavoitteita.<SOURF>vaka_tr_02 + tr63</SOURF></NOTE> <NOTE>Englannin käsite
     * education on laajempi kuin suomen opetus (1), niin että termillä education
     * viitataan opetuksen (1) lisäksi muun muassa
     * <RCON href="#tmpOKSAID116">kasvatukseen</RCON>,
     * <RCON href="#tmpOKSAID121">koulutukseen (1)</RCON> ja sivistykseen.<SOURF>rk
     * + KatriSeppala</SOURF></NOTE> <NOTE>Käsitteen tunnus: tmpOKSAID117</NOTE>
     * </LANG> <LANG value="en"> <TE> <EQUI value="broader"></EQUI>
     * <TERM>education</TERM> <HOGR>1</HOGR> <SOURF>ophall_sanasto</SOURF> </TE>
     * <SY> <TERM>upbringing</TERM> <SOURF>MOT_englanti</SOURF> </SY> </LANG>
     *
     * @param o            LANGType containing incoming NTRF-block
     * @param terminology Graph-element containing information of parent
     *                     vocabularity like id and base-uri
     */
    private int hadleLANG(UUID currentConcept,
                          List<GenericNode> termsList,
                          LANG o,
                          Map<String, List<Attribute>> parentProperties,
                          Map<String, List<Identifier>> parentReferences,
                          GenericNode terminology) {
        // generate random UUID as a code and use it as part if the generated URI
        String code = UUID.randomUUID().toString();

        if (logger.isDebugEnabled())
            logger.debug("Handle LANG:" + o.getValue());

        // Attributes are stored to property-list
        Map<String, List<Attribute>> properties = new HashMap<>();

        // Filter TE elemets as list and add mapped elements as properties under node
        if (o.getTE() != null) {
            // TE/TERM TE/TERM/GRAM
            // TE/SOURF
            // TE/REMK
            // TE/HOGR
            // TE/EQUI
            // TE/SCOPE
            // TE/ADD
            handleTE(o.getTE(), o.getValue().value(), // lang
                properties, parentProperties, terminology);
        }

        // DEFINITION
        List<DEF> def = o.getDEF();
        // Definition is complex multi-line object which needs to be resolved
        for (DEF d : def) {
            handleDEF(currentConcept, d, o.getValue().value(), parentProperties, parentReferences, properties,
                terminology);
        }
        // NOTE
        List<NOTE> notes = o.getNOTE();
        for (NOTE n : notes) {
            handleNOTE(currentConcept, n, o.getValue().value(), parentProperties, parentReferences, properties,
                terminology);
        }

        // SY (synonym) is just like TE
        List<Termcontent> synonym = o.getSY();
        synonym.forEach(obj -> {
            GenericNode n = handleSY(obj, o.getValue().value(), parentProperties, parentReferences, terminology);
            if (n != null) {
                termsList.add(n);
                List<Identifier> ref;
                if (parentReferences.get("altLabelXl") != null)
                    ref = parentReferences.get("altLabelXl");
                else
                    ref = new ArrayList<>();
                ref.add(new Identifier(n.getId(), typeMap.get("Term").getDomain()));
                parentReferences.put("altLabelXl", ref);
            }
        });

        // STE Search-terms
        List<Termcontent> ste = o.getSTE();
        ste.forEach(obj -> {
            // Handle like synonym
            GenericNode n = handleSY(obj, o.getValue().value(), parentProperties, parentReferences, terminology);
            // and then add as searchTerm
            if (n != null) {
                termsList.add(n);
                List<Identifier> ref;
                if (parentReferences.get("searchTerm") != null)
                    ref = parentReferences.get("searchTerm");
                else
                    ref = new ArrayList<>();
                ref.add(new Identifier(n.getId(), typeMap.get("Term").getDomain()));
                parentReferences.put("searchTerm", ref);
            }
        });

        // DTEA = notRecommendedSynonym term
        List<Termcontent> dtea = o.getDTEA();
        dtea.forEach(obj -> {
            // Handle like synonym
            GenericNode n = handleSY(obj, o.getValue().value(), parentProperties, parentReferences, terminology);
            // and then add as not Recommended Synonym
            if (n != null) {
                termsList.add(n);
                List<Identifier> ref;
                if (parentReferences.get("notRecommendedSynonym") != null)
                    ref = parentReferences.get("notRecommendedSynonym");
                else
                    ref = new ArrayList<>();
                ref.add(new Identifier(n.getId(), typeMap.get("Term").getDomain()));
                parentReferences.put("notRecommendedSynonym", ref);
            }
        });
        // DTE = notRecommendedSynonym term
        List<Termcontent> dte = o.getDTE();
        dte.forEach(obj -> {
            // Handle like synonym
            GenericNode n = handleSY(obj, o.getValue().value(), parentProperties, parentReferences, terminology);
            // and then add as not Recommended Synonym
            if (n != null) {
                termsList.add(n);
                List<Identifier> ref;
                if (parentReferences.get("notRecommendedSynonym") != null)
                    ref = parentReferences.get("notRecommendedSynonym");
                else
                    ref = new ArrayList<>();
                ref.add(new Identifier(n.getId(), typeMap.get("Term").getDomain()));
                parentReferences.put("notRecommendedSynonym", ref);
            }
        });

        // DTEB = retired term missing currently from META
        // handled now as DTEA
        List<Termcontent> dteb = o.getDTEB();
        dteb.forEach(obj -> {
            // Handle like synonym
            GenericNode n = handleSY(obj, o.getValue().value(), parentProperties, parentReferences, terminology);
            // and then add as not Recommended Synonym
            if (n != null) {
                termsList.add(n);
                List<Identifier> ref;
                if (parentReferences.get("notRecommendedSynonym") != null)
                    ref = parentReferences.get("notRecommendedSynonym");
                else
                    ref = new ArrayList<>();
                ref.add(new Identifier(n.getId(), typeMap.get("Term").getDomain()));
                parentReferences.put("notRecommendedSynonym", ref);
            }
        });

        TypeId typeId = typeMap.get("Term").getDomain();
        // Uri is parent-uri/term-'code'
        GenericNode node = null;
        String uri = terminology.getUri().endsWith("/")? terminology.getUri() + "term-" + code: terminology.getUri() + "/term-" + code; 
        if (idMap.get(code) != null) {
            if (logger.isDebugEnabled())
                logger.debug("Update Term");
            node = new GenericNode(idMap.get(code), code, uri, 0L, "", new Date(),
                "", new Date(), typeId, properties, emptyMap(), emptyMap());
        } else {
            node = new GenericNode(code, uri, 0L, "", new Date(), "", new Date(),
                typeId, properties, emptyMap(), emptyMap());
            // Set just created term as preferred term for concept

            List<Identifier> ref;
            if (parentReferences.get("prefLabelXl") != null)
                ref = parentReferences.get("prefLabelXl");
            else
                ref = new ArrayList<>();
            ref.add(new Identifier(node.getId(), typeId));
            parentReferences.put("prefLabelXl", ref);
        }
        termsList.add(node);
        // Add id for reference resolving
        createdIdMap.put(node.getCode(), node.getId());
        return termsList.size();
    }

    private void handleTERM(TERM term,
                            String lang,
                            Map<String, List<Attribute>> properties) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handle Term:" + term.toString());
        }
        String termName = "";
        List<Object> content = term.getContent();
        for (Object li : content) {
            if (li instanceof String) {
                termName = termName.concat(li.toString().trim() + " ");
            } else if (li instanceof GRAM && li != null) {
                handleGRAM((GRAM) li, properties);
                String prefLabel = ((GRAM) li).getContent();
                // Add actual pref-label for term
                logger.info("Handle Term with GRAM:" + prefLabel);
                termName = termName.concat(prefLabel+" ");
            } else {
                logger.error(" TERM: unhandled contentclass=" + li.getClass().getName() + " value=" + li.toString());
            }
        }
        if (!termName.isEmpty()) {
            Attribute att = new Attribute(lang, termName.trim());
            addProperty("prefLabel", properties, att);
        }
    }

    private void handleTE(Termcontent tc,
                          String lang,
                          Map<String, List<Attribute>> properties,
                          Map<String, List<Attribute>> parentProperties,
                          GenericNode terminology) {
        if (logger.isDebugEnabled())
            logger.debug("Handle Te:" + tc.toString());
        // If GEOG used
        if (tc.getGEOG() != null) {
            String lng = (lang.toLowerCase() + "-" + tc.getGEOG().toUpperCase());
            lang = lng;
        }
        // LANG/TE/TERM
        if (tc.getTERM() != null) {
            handleTERM(tc.getTERM(), lang, properties);
        }

        // LANG/TE/SOURF
        if (tc.getSOURF() != null) {
            handleSOURF(tc.getSOURF(), null, parentProperties, terminology);
        }
        // LANG/TE/HOGR
        if (tc.getHOGR() != null && !tc.getHOGR().isEmpty()) {
            Attribute att = new Attribute(null, tc.getHOGR());
            addProperty("termHomographNumber", properties, att);
        }

        // LANG/TE/SCOPE
        if (tc.getSCOPE() != null) {
            handleSCOPE(lang, tc.getSCOPE(), properties);
        }
        // LANG/TE/EQUI
        if (tc.getEQUI() != null) {
            handleEQUI(lang, tc.getEQUI(), properties);
        }
        // LANG/TE/REMK
        if (tc.getREMK() != null) {
            handleREMK(lang, tc.getREMK(), properties, terminology);
        }
        if (tc.getADD() != null) {
            handleADD(lang, tc.getADD(), properties);
        }
    }

    private String getAttributeContent(List<Serializable> li) {
        String value = null;
        if (!li.isEmpty()) { // if value exist
            value = li.get(0).toString();
        }
        return value;
    }

    private void handleREMK(String lang,
                            REMK remk,
                            Map<String, List<Attribute>> properties,
                            GenericNode terminology) {
        List<Attribute> eNotes = properties.get("editorialNote");
        if (eNotes == null)
            eNotes = new ArrayList<Attribute>();

        List<?> content = remk.getContent();
        String editorialNote = "";
        for (Object o : content) {
            if (o instanceof String) {
                editorialNote = editorialNote + ((String) o).toString();
            } else if (o instanceof JAXBElement) {
                JAXBElement elem = (JAXBElement) o;
                editorialNote = editorialNote + elem.getValue().toString();
            } else if (o instanceof LINK) {
                LINK l = (LINK) o;
                String linkRef = parseLinkRef(l, terminology);
                editorialNote = editorialNote
                    .concat("<a href='" + l.getHref() + "' data-type='external'>" + linkRef + "</a>");
            } else if (o instanceof SOURF) {
                // handleSOURF((SOURF)o,lang,properties,vocabularity);
                handleSOURF((SOURF) o, null, properties, terminology);
            } else {
                // statusList.put(currentRecord, new StatusMessage(currentRecord,
                // " REMK: unhandled contentclass=" + o.getClass().getName() + " value=" +
                // o.toString()));
                statusList.add(new StatusMessage(currentRecord,
                    " REMK: unhandled contentclass=" + o.getClass().getName() + " value=" + o.toString()));
                logger.error(" REMK: unhandled contentclass=" + o.getClass().getName() + " value=" + o.toString());
            }

        }
        ;
        if (!editorialNote.isEmpty()) {
            if (logger.isDebugEnabled())
                logger.debug("REMK  Editorial note!!!" + editorialNote);
            Attribute att = new Attribute("fi", editorialNote);
            addProperty("editorialNote", properties, att);
        }
    }

    private void handleEQUI(String lang,
                            EQUI equi,
                            Map<String, List<Attribute>> properties) {
        // Attribute string value = broader | narrower | near-equivalent
        String eqvalue = "=";
        if (equi.getValue().equalsIgnoreCase("broader"))
            eqvalue = ">";
        if (equi.getValue().equalsIgnoreCase("narrower"))
            eqvalue = "<";
        if (equi.getValue().equalsIgnoreCase("near-equivalent"))
            eqvalue = "~";
        Attribute att = new Attribute(null, equi.getValue());
        // Attribute att = new Attribute(lang, equi.getValue());
        addProperty("termEquivalency", properties, att);
    }

    private void handleADD(String lang,
                           String add,
                           Map<String, List<Attribute>> properties) {
        Attribute att = new Attribute(null, add);
        addProperty("termInfo", properties, att);
    }

    private void handleSCOPE(String lang,
                             SCOPE scope,
                             Map<String, List<Attribute>> properties) {
        if(logger.isDebugEnabled()){
            logger.debug("HandleScope = " + scope.getContent().toString());
        }
        scope.getContent().forEach(li -> {
            if (li instanceof String) {
                Attribute att = new Attribute(null, li.toString());
                // Attribute att = new Attribute(lang, li.toString());
                addProperty("scope", properties, att);
            } else if (li instanceof LINK) {
                // <SCOPE>yliopistolain <LINK
                // href="https://www.finlex.fi/fi/laki/kaannokset/2009/en20090558_20160644.pdf">558/2009
                // käännöksessä</LINK></SCOPE>
                logger.info("Unimplemented SCOPE WITH LINK");
                // @TODO! Make impl
            }
        });

    }

    private void handleGRAM(GRAM gt,
                            Map<String, List<Attribute>> properties) {
        if (logger.isDebugEnabled())
            logger.debug("Grammatical specification");

        // termConjugation (single, plural)
        if (gt.getValue() != null && gt.getValue().equalsIgnoreCase("pl")) {
            // Currently not localized
            Attribute att = new Attribute("fi", "monikko");
            addProperty("termConjugation", properties, att);
        } else if (gt.getValue() != null && gt.getValue().equalsIgnoreCase("n pl")) {
            // Currently not localized plural and neutral
            Attribute att = new Attribute("fi", "monikko");
            addProperty("termConjugation", properties, att);
            att = new Attribute("fi", "neutri");
            addProperty("termFamily", properties, att);
        } else if (gt.getValue() != null && gt.getValue().equalsIgnoreCase("f pl")) {
            // Currently not localized plural and neutral
            Attribute att = new Attribute("fi", "monikko");
            addProperty("termConjugation", properties, att);
            att = new Attribute("fi", "feminiini");
            addProperty("termFamily", properties, att);
        }
        // termFamily
        if (gt.getGend() != null && gt.getGend().equalsIgnoreCase("f")) {
            // feminiini
            // Currently not localized
            Attribute att = new Attribute("fi", "feminiini");
            addProperty("termFamily", properties, att);
        } else if (gt.getGend() != null && gt.getGend() != null && gt.getGend().equalsIgnoreCase("m")) {
            // maskuliiini
            Attribute att = new Attribute("fi", "maskuliini");
            addProperty("termFamily", properties, att);
        } else if (gt.getGend() != null && gt.getGend().equalsIgnoreCase("n")) {
            // Neutri
            Attribute att = new Attribute("fi", "neutri");
            addProperty("termFamily", properties, att);
        }
        // wordClass
        if (gt.getPos() != null && !gt.getPos().isEmpty()) {
            // Currently not localized, just copy wordClass as such
            Attribute att = new Attribute(null, gt.getPos());
            addProperty("wordClass", properties, att);
        }
    }

    /**
     * Handle CHECK->status-property mapping
     *
     * @param o          CHECK-field
     * @param properties Propertylist where status is added
     */
    private Attribute handleCHECK(String o,
                                  String stat,
                                  Map<String, List<Attribute>> properties) {
        String status = "DRAFT";
        /*
         * keskeneräinen | 'INCOMPLETE' korvattu | 'SUPERSEDED' odottaa hyväksyntää |
         * 'SUBMITTED' | 'RETIRED' | 'INVALID' hyväksytty | 'VALID' | 'SUGGESTED'
         * luonnos | 'DRAFT'
         */
        if (o.equalsIgnoreCase("hyväksytty"))
            status = "DRAFT";

        if (stat != null && !stat.isEmpty() && stat.equalsIgnoreCase("vanhentunut"))
            status = "RETIRED";
        Attribute att = new Attribute(null, status);
        addProperty("status", properties, att);
        return att;
    }

    private void handleStat(String stat,
                            Map<String, List<Attribute>> properties) {
        String status = "DRAFT";
        /*
         * keskeneräinen | 'INCOMPLETE' korvattu | 'SUPERSEDED' odottaa hyväksyntää |
         * 'SUBMITTED' | 'RETIRED' | 'INVALID' hyväksytty | 'VALID' | 'SUGGESTED'
         * luonnos | 'DRAFT'
         */
        if (stat != null && !stat.isEmpty() && stat.equalsIgnoreCase("vanhentunut")) {
            status = "RETIRED";
            Attribute att = new Attribute(null, status);
            addProperty("status", properties, att);
        }
    }

    private void handleSUBJ(SUBJ subj,
                            Map<String, List<Attribute>> properties) {
        if (subj != null) {
            subj.getContent().forEach(o -> {
                if (o instanceof String) {
                    Attribute att = new Attribute(null, o.toString());
                    // Attribute att = new Attribute(lang, o.toString());
                    addProperty("conceptScope", properties, att);
                } else {
                    logger.error("SUBJ unknown instance type:" + o.getClass().getName());
                    // statusList.put(currentRecord,
                    // new StatusMessage(currentRecord, "SUBJS unknown instance type:" +
                    // o.getClass().getName()));
                    statusList.add(
                        new StatusMessage(currentRecord, "SUBJS unknown instance type:" + o.getClass().getName()));
                }
            });
        }

        /*
         * String status = "DRAFT"; if (stat != null && !stat.isEmpty() &&
         * stat.equalsIgnoreCase("vanhentunut")) { status = "RETIRED"; Attribute att =
         * new Attribute(null, status); addProperty("status", properties, att); }
         */
    }

    /**
     * NTRF Broader-concept parsing Can be direct hierarchical or partitive
     * reference <BCON href="#tmpOKSAID122" typr="generic">koulutus (2)</BCON>
     * <BCON href="#tmpOKSAID148" typr="partitive">toimipisteen</BCON>
     *
     * @param o
     * @param references
     */
    private void handleBCON(UUID currentConcept,
                            BCON o) {
        if (logger.isDebugEnabled())
            logger.debug("handleBCON:" + o.getHref());
        String brefId = o.getHref();
        // Remove #
        if (brefId.startsWith("#"))
            brefId = o.getHref().substring(1);

        logger.info("handleBCON add item from source record:" + currentRecord + "--> target:" + brefId + " Type"
            + o.getTypr());
        ConnRef conRef = new ConnRef();
        // Use delayed resolving, so save record id for logging purposes
        conRef.setCode(currentRecord);
        conRef.setReferenceString(brefId);
        // Null id, as a placeholder for target
        conRef.setId(currentConcept);
        conRef.setType(o.getTypr());
        conRef.setTargetId(NULL_ID);

        // if not yet defined, create list and populate it
        List<ConnRef> reflist;
        if (bconList.containsKey(currentRecord)) {
            reflist = bconList.get(currentRecord);
        } else {
            reflist = new ArrayList<>();
        }
        reflist.add(conRef);
        bconList.put(currentRecord, reflist);
    }

    /**
     * NTRF Related-concept parsing Can be direct hierarchical or partitive
     * reference <RCON href="#tmpOKSAID122">koulutus (2)</RCON>
     *
     * @param o
     * @param references
     */
    private void handleRCON(UUID currentConcept,
                            RCON o) {
        if (logger.isDebugEnabled())
            logger.debug("handleRCON:" + o.getHref());
        String brefId = o.getHref();
        // Remove #
        if (brefId.startsWith("#"))
            brefId = o.getHref().substring(1);

        logger.info("handleRCON add item from source record:" + currentRecord + "--> target:" + brefId);
        ConnRef conRef = new ConnRef();
        // Use delayed resolving, so save record id for logging purposes
        conRef.setCode(currentRecord);
        conRef.setReferenceString(brefId);
        // Null id, as a placeholder for target
        conRef.setId(currentConcept);
        conRef.setTargetId(NULL_ID);

        // if not yet defined, create list and populate it
        List<ConnRef> reflist;
        if (rconList.containsKey(currentRecord)) {
            reflist = rconList.get(currentRecord);
        } else {
            reflist = new ArrayList<>();
        }
        reflist.add(conRef);
        rconList.put(currentRecord, reflist);
    }

    /**
     * NTRF Related-concept parsing Can be direct hierarchical or partitive
     * reference <RCON href="#tmpOKSAID564">ylioppilastutkintoa</RCON>
     * <RCON href="#tmpOKSAID436">Eurooppa-koulujen</RCON>
     * <RCON href="#tmpOKSAID456">lukiokoulutuksen</RCON>*
     *
     * @param rc
     * @param references
     */
    private void handleRCONRef(UUID currentConcept,
                               RCON rc,
                               Map<String, List<Identifier>> references) {
        if (logger.isDebugEnabled())
            logger.debug("handleRCON ref:" + rc.getHref());
        String rrefId = rc.getHref();
        // Remove #
        if (rrefId.startsWith("#"))
            rrefId = rc.getHref().substring(1);

        logger.info("handleRCONRef add item from source record:" + currentRecord + "--> target:" + rrefId);
        ConnRef conRef = new ConnRef();
        // Use delayed resolving, so save record id for logging purposes
        conRef.setCode(currentRecord);
        conRef.setReferenceString(rrefId);
        // Null id, as a placeholder for target
        conRef.setId(currentConcept);
        conRef.setType(rc.getTypr());
        conRef.setTargetId(NULL_ID);

        // if not yet defined, create list and populate it
        List<ConnRef> reflist;
        if (rconList.containsKey(currentRecord)) {
            reflist = rconList.get(currentRecord);
        } else {
            reflist = new ArrayList<>();
        }
        reflist.add(conRef);
        rconList.put(currentRecord, reflist);
    }

    /**
     * NTRF Related-concept parsing Can be direct hierarchical or partitive
     * reference <BCON href="#tmpOKSAID564">ylioppilastutkintoa</RCON>
     * <BCON href="#tmpOKSAID436">Eurooppa-koulujen</RCON>
     * <BCON href="#tmpOKSAID456">lukiokoulutuksen</RCON>*
     *
     * @param o
     * @param references
     */
    private void handleBCONRef(UUID currentConcept,
                               BCON bc,
                               Map<String, List<Identifier>> references) {
        if (logger.isDebugEnabled())
            logger.debug("handleBCON ref:" + bc.getHref());
        String rrefId = bc.getHref();
        // Remove #
        if (rrefId.startsWith("#"))
            rrefId = bc.getHref().substring(1);

        logger.info("handleBCONRef add item from source record:" + currentRecord + "--> target:" + rrefId);
        ConnRef conRef = new ConnRef();
        // Use delayed resolving, so save record id for logging purposes
        conRef.setCode(currentRecord);
        conRef.setReferenceString(rrefId);
        // Null id, as a placeholder for target
        conRef.setId(currentConcept);
        conRef.setType(bc.getTypr());
        conRef.setTargetId(NULL_ID);

        // if not yet defined, create list and populate it
        List<ConnRef> reflist;
        if (bconList.containsKey(currentRecord)) {
            reflist = bconList.get(currentRecord);
        } else {
            reflist = new ArrayList<>();
        }
        reflist.add(conRef);
        bconList.put(currentRecord, reflist);
    }

    /**
     * NTRF Related-concept parsing Can be direct hierarchical or partitive
     * reference <NCON href="#tmpOKSAID564">ylioppilastutkintoa</NCON>
     * <NCON href="#tmpOKSAID436">Eurooppa-koulujen</NCON>
     * <NCON href="#tmpOKSAID456">lukiokoulutuksen</NCON>*
     *
     * @param o
     * @param references
     */
    private void handleNCONRef(UUID currentConcept,
                               NCON nc,
                               Map<String, List<Identifier>> references) {
        if (logger.isDebugEnabled())
            logger.debug("handleNCON ref:" + nc.getHref());
        String rrefId = nc.getHref();
        // Remove #
        if (rrefId.startsWith("#"))
            rrefId = nc.getHref().substring(1);

        logger.info("handleNCONRef add item from source record:" + currentRecord + "--> target:" + rrefId);
        ConnRef conRef = new ConnRef();
        // Use delayed resolving, so save record id for logging purposes
        conRef.setCode(currentRecord);
        conRef.setReferenceString(rrefId);
        // Null id, as a placeholder for target
        conRef.setId(currentConcept);
        conRef.setType(nc.getTypr());
        conRef.setTargetId(NULL_ID);

        // if not yet defined, create list and populate it
        List<ConnRef> reflist;
        if (nconList.containsKey(currentRecord)) {
            reflist = nconList.get(currentRecord);
        } else {
            reflist = new ArrayList<>();
        }
        reflist.add(conRef);
        nconList.put(currentRecord, reflist);
    }

    /**
     * Actual NCON reference in body part, so this time it is added
     *
     * @param o
     * @param narroverConceptId
     */
    private void handleNCON(UUID currentConcept,
                            NCON o) {
        if (logger.isDebugEnabled())
            logger.debug("handleNCON:" + o.getHref());
        String nrefId = o.getHref();
        if (nrefId != null) {
            // Remove #
            if (nrefId.startsWith("#")) {
                nrefId = o.getHref().substring(1);
            }
            logger.info("handleNCON add item from source record:" + currentRecord + "--> target:" + nrefId);
            ConnRef conRef = new ConnRef();
            // Use delayed resolving, so save record id for logging purposes
            conRef.setCode(currentRecord);
            conRef.setReferenceString(nrefId);
            // Null id, as a placeholder for target
            conRef.setId(currentConcept);
            conRef.setType(o.getTypr());
            conRef.setTargetId(NULL_ID);

            // if not yet defined, create list and populate it
            List<ConnRef> reflist;
            if (nconList.containsKey(currentRecord)) {
                reflist = nconList.get(currentRecord);
            } else {
                reflist = new ArrayList<>();
            }
            reflist.add(conRef);
            nconList.put(currentRecord, reflist);
        }
    }

    /**
     * Set up ConceptClass with CLAS-element data.
     *
     * @param o          CLAS object containing String list
     * @param properties
     */
    private void handleCLAS(CLAS o,
                            Map<String, List<Attribute>> properties) {
        if (o.getContent().size() > 0) {
            List<String> clasList = new ArrayList<>();
            o.getContent().forEach(obj -> {
                clasList.add(obj.toString());
            });
            Attribute att = new Attribute(null, clasList.toString().substring(1, clasList.toString().length() - 1));
            addProperty("conceptClass", properties, att);
        } else {
            logger.warn("Empty CLAS element.");
        }
    }

    private Attribute handleDEF(UUID currentConcept,
                                DEF def,
                                String lang,
                                Map<String, List<Attribute>> parentProperties,
                                Map<String, List<Identifier>> parentReferences,
                                Map<String, List<Attribute>> termProperties,
                                GenericNode terminology) {
        if (logger.isDebugEnabled())
            logger.debug("handleDEF-part:" + def.getContent());
            logger.info("handleDEF-part:" + def.getContent());

        String defString = "";

        List<?> defItems = def.getContent();
        for (Object de : defItems) {
            if (de instanceof String) {
                String str = (String) de;
                // trim and add space
                if (defString.isEmpty()) {
                    defString = defString.concat(str.trim() + " ");
                } else if (defString.endsWith(" ")) {
                    defString = defString.concat(str.trim() + " ");
                } else { // Add space befor and after if
                    defString = defString.concat(" " + str.trim() + " ");
                }
                System.out.println("--String handleDEF:"+defString);
            } else {
                if (de instanceof RCON) {
                    // <NCON href="#tmpOKSAID122" typr="partitive">koulutuksesta (2)</NCON> ->
                    // <a href="http://uri.suomi.fi/terminology/oksa/tmpOKSAID122"
                    // data-typr="partitive">koulutuksesta (2)</a>
                    // <DEF>suomalaista <RCON href="#tmpOKSAID564">ylioppilastutkintoa</RCON>
                    // vastaava <RCON href="#tmpOKSAID436">Eurooppa-koulujen</RCON> <BCON
                    // href="#tmpOKSAID1401" typr="generic">tutkinto</BCON>, joka suoritetaan
                    // kaksivuotisen <RCON href="#tmpOKSAID456">lukiokoulutuksen</RCON>
                    // päätteeksi<SOURF>opintoluotsi + rk + tr34</SOURF></DEF>
                    RCON rc = (RCON) de;
                    defString = defString.concat("<a href='" + terminology.getUri());
                    // Remove # from uri
                    defString = defString.concat(getCleanRef(rc.getHref(), "related"));
                    String hrefText = parseHrefText(rc.getContent());
                    // Remove newlines
                    hrefText = hrefText.replaceAll("\n", "");

                    hrefText = hrefText.trim();
                    defString = defString.concat(">" + hrefText + "</a>");
                    if (logger.isDebugEnabled())
                        logger.debug("handleDEF RCON:" + defString);
                    // Add also reference
                    handleRCONRef(currentConcept, rc, parentReferences);
                } else if (de instanceof BCON) {
                    // <DEF><RCON href="#tmpOKSAID162">yliopiston</RCON> <BCON href="#tmpOKSAID187"
                    // typr="partitive"
                    // >opetus- ja tutkimushenkilöstön</BCON> osa, jonka tehtävissä suunnitellaan,
                    // koordinoidaan ja johdetaan erittäin laajoja kokonaisuuksia, tehtäviin
                    // sisältyy kokonaisvaltaista
                    // vastuuta organisaation toiminnasta ja taloudesta sekä kansallisen tai
                    // kansainvälisen
                    // tason kehittämistehtävistä ja tehtävissä vaikutetaan huomattavasti koko
                    // tutkimusjärjestelmään
                    // <SOURF>neliport + tr40</SOURF></DEF>

                    BCON bc = (BCON) de;
                    defString = defString.concat("<a href='" + terminology.getUri());
                    String typr = bc.getTypr();
                    if (typr == null) { // default when not set up
                        typr = "broader";
                    } else if (typr.equalsIgnoreCase("partitive")) {
                        typr = "isPartOf";
                    } else { // default
                        typr = "broader";
                    }

                    // Remove # from uri
                    defString = defString.concat(getCleanRef(bc.getHref(), typr));
                    String hrefText = parseHrefText(bc.getContent());
                    defString = defString.concat(">" + hrefText.trim() + "</a>");
                    // Add also reference
                    handleBCONRef(currentConcept, bc, parentReferences);
                } else if (de instanceof NCON) {
                    // TODO! proper support for narrover concepts, currently just rip links
                    // <DEF><RCON href="#tmpOKSAID162">yliopiston</RCON> <BCON href="#tmpOKSAID187"
                    // typr="partitive"
                    // >opetus- ja tutkimushenkilöstön</BCON> osa, jonka tehtävissä suunnitellaan,
                    // koordinoidaan ja johdetaan erittäin laajoja kokonaisuuksia, tehtäviin
                    // sisältyy kokonaisvaltaista
                    // vastuuta organisaation toiminnasta ja taloudesta sekä kansallisen tai
                    // kansainvälisen
                    // tason kehittämistehtävistä ja tehtävissä vaikutetaan huomattavasti koko
                    // tutkimusjärjestelmään
                    // <SOURF>neliport + tr40</SOURF>
                    // <NCON href="#tmpOKSAID450" typr="generic">esiopetusta</NCON></DEF>

                    NCON nc = (NCON) de;
                    defString = defString.concat("<a href='" + terminology.getUri());
                    String typr = nc.getTypr();
                    if (typr == null) { // default when not set up
                        typr = "narrower";
                    } else if (typr.equalsIgnoreCase("partitive")) {
                        typr = "hasPart";
                    } else { // default
                        typr = "narrower";
                    }

                    // Remove # from uri
                    defString = defString.concat(getCleanRef(nc.getHref(), typr));
                    String hrefText = parseHrefText(nc.getContent());
                    defString = defString.concat(">" + hrefText.trim() + "</a>");
                    // Add also reference
                    handleNCONRef(currentConcept, nc, parentReferences);
                } else if (de instanceof SOURF) {
                    handleSOURF((SOURF) de, null, termProperties, terminology);
                    // handleSOURF((SOURF)de, lang, termProperties, vocabularity);
                    // Add refs as sources-part.
                    updateSources(((SOURF) de).getContent(), lang, termProperties);
                } else if (de instanceof REMK) {
                    handleREMK(lang, (REMK) de, termProperties, terminology);
                } else if (de instanceof LINK) {
                    LINK lc = (LINK) de;
                    if (lc.getContent() != null && lc.getContent().size() > 0) {
                        // Remove "href:" from string
                        // "href:https://www.finlex.fi/fi/laki/ajantasa/1973/19730036"
                        String linkRef = lc.getHref();

                        linkRef = parseLinkRef(lc, terminology);
                        if (linkRef.startsWith("href:")) {
                            linkRef = linkRef.substring(5);
                        }
                        defString = defString.concat("<a href='" + linkRef + "' data-type='external'>"
                            + lc.getContent().get(0).toString().trim() + "</a> ");
                    }
                } else if (de instanceof JAXBElement) {
                    // HOGR
                    JAXBElement el = (JAXBElement) de;
                    if (el.getName().toString().equalsIgnoreCase("HOGR")) {
                        defString = defString.trim() + " (" + el.getValue().toString() + ")";
                    } else if (de instanceof String) {
                        defString = defString + (String) de;
                    }
                } else {
                    logger.error("DEF, unhandled CLASS=" + de.getClass().getName());
                    // statusList.put(currentRecord,
                    // new StatusMessage(currentRecord, "DEF, unhandled CLASS=" +
                    // de.getClass().getName()));
                    statusList.add(new StatusMessage(currentRecord, "DEF, unhandled CLASS=" + de.getClass().getName()));
                }
            }
        }
        if (logger.isDebugEnabled())
            logger.debug("Definition=" + defString);
            logger.info("handleDEF=" + defString);
        // Add definition if exist.
        if (!defString.isEmpty()) {
            // clean commans and points
            defString = defString.replaceAll(" , ", ", ");
            defString = defString.replaceAll(" \\. ", ". ");
            Attribute att = new Attribute(lang, defString.trim());
            addProperty("definition", parentProperties, att);
            return att;
        } else
            return null;
    }

    private String parseHrefText(List<Serializable> content) {
        String hrefText = "";
        for (Serializable c : content) {
            if (c instanceof JAXBElement) {
                JAXBElement el = (JAXBElement) c;
                if (el.getName().toString().equalsIgnoreCase("HOGR")) {
                    hrefText = hrefText.trim() + " (" + el.getValue().toString() + ")";
                }
            } else if (c instanceof String) {
                hrefText = hrefText + c;
            }
        }
        // Remove newlines just in case
        hrefText = hrefText.replaceAll("\n", "");
        return hrefText;
    }

    private String parseLinkRef(LINK li,
                                GenericNode terminology) {
        String linkRef = li.getHref();
        // Remove "href:" from string
        if (linkRef.startsWith("href:")) {
            linkRef = linkRef.substring(5);
        }
        if (linkRef.startsWith("#")) {
            // internal reference, generate url for it.
            if (terminology.getUri().endsWith("/")) {
                linkRef = terminology.getUri() + linkRef.substring(1);
            } else {
                linkRef = terminology.getUri() + "/" + linkRef.substring(1);
            }
        }
        return linkRef;
    }

    private String getCleanRef(String refString,
                               String datatype) {
        String ref = "";
        if (refString.startsWith("#")) {
            String hrefid = refString.substring(1);
            ref = ref.concat(hrefid + "'");
        } else
            ref = ref.concat(refString + "'");
        if (datatype != null && !datatype.isEmpty()) {
            ref = ref.concat(" property ='" + datatype + "'");
        }
        return ref;
    }

    private Attribute handleNOTE(UUID currentConcept,
                                 NOTE note,
                                 String lang,
                                 Map<String, List<Attribute>> parentProperties,
                                 Map<String, List<Identifier>> parentReferences,
                                 Map<String, List<Attribute>> termProperties,
                                 GenericNode terminology) {
        if (logger.isDebugEnabled())
            logger.debug("handleNOTE-part" + note.getContent());

        String noteString = "";
        for (Object de : note.getContent()) {
            if (de instanceof String) {
                if (logger.isDebugEnabled())
                    logger.debug("  Parsing note-string:" + de.toString());
                String str = (String) de;
                // trim and add space
                if (noteString.isEmpty()) {
                    noteString = str;
                } else {
                    noteString = noteString.concat(str);
                }
//                noteString = StringEscapeUtils.unescapeXml(noteString);
                // Remove newline from string
                noteString = noteString.replace("\n", "");
            } else if (de instanceof SOURF) {
                if (((SOURF) de).getContent() != null && ((SOURF) de).getContent().size() > 0) {
                    handleSOURF((SOURF) de, null, termProperties, terminology);
                    // handleSOURF((SOURF)de, lang, termProperties,vocabulary);
                    // Don't add sourf-string into the note-field, just add them to the sources-list
                    // noteString=noteString.concat(((SOURF)de).getContent().toString().trim());
                    // Add refs as string and construct lines for sources-part.
                    updateSources(((SOURF) de).getContent(), lang, termProperties);
                }
            } else if (de instanceof RCON) {
                RCON rc = (RCON) de;
                noteString = noteString.concat("<a href='" + terminology.getUri());
                // Remove # from uri
                noteString = noteString.concat(getCleanRef(rc.getHref(), "related"));
                String hrefText = parseHrefText(rc.getContent());
                noteString = noteString.concat(">" + hrefText.trim() + "</a>");
                if (logger.isDebugEnabled())
                    logger.debug("handleNOTE RCON:" + noteString.trim());
                // Add also reference
                handleRCONRef(currentConcept, rc, parentReferences);
            } else if (de instanceof BCON) {
                BCON bc = (BCON) de;
                if (bc.getContent() != null && bc.getContent().size() > 0) {
                    noteString = noteString.concat("<a href='" + terminology.getUri());
                    String typr = bc.getTypr();
                    if (typr == null) { // default when not set up
                        typr = "broader";
                    } else if (typr.equalsIgnoreCase("partitive")) {
                        typr = "isPartOf";
                    } else { // default
                        typr = "broader";
                    }
                    // Remove # from uri
                    noteString = noteString.concat(getCleanRef(bc.getHref(), typr));
                    String hrefText = parseHrefText(bc.getContent());
                    noteString = noteString.concat(">" + hrefText.trim() + "</a>");
                    if (logger.isDebugEnabled())
                        logger.debug("handleDEF BCON:" + noteString);
                    // Add also reference
                    handleBCONRef(currentConcept, bc, parentReferences);
                }
            } else if (de instanceof NCON) {
                NCON nc = (NCON) de;
                if (nc.getContent() != null && nc.getContent().size() > 0) {
                    noteString = noteString.concat("<a href='" + terminology.getUri());
                    String typr = nc.getTypr();
                    if (typr == null) { // default when not set up
                        typr = "narrower";
                    } else if (typr.equalsIgnoreCase("partitive")) {
                        typr = "hasPart";
                    } else { // default
                        typr = "narrower";
                    }

                    // Remove # from uri
                    noteString = noteString.concat(getCleanRef(nc.getHref(), typr));
                    String hrefText = parseHrefText(nc.getContent());
                    noteString = noteString.concat(">" + hrefText.trim() + "</a>");
                    if (logger.isDebugEnabled())
                        logger.debug("handleDEF NCON:" + noteString);
                    // Add also reference
                    handleNCONRef(currentConcept, nc, parentReferences);
                }
            } else if (de instanceof LINK) {
                LINK lc = (LINK) de;
                if (lc.getContent() != null && lc.getContent().size() > 0) {
                    // Remove "href:" from string
                    // "href:https://www.finlex.fi/fi/laki/ajantasa/1973/19730036"
                    String linkRef = lc.getHref();

                    linkRef = parseLinkRef(lc, terminology);
                    if (linkRef.startsWith("href:")) {
                        linkRef = linkRef.substring(5);
                    }
                    noteString = noteString.trim().concat("<a href='" + linkRef + "' data-type='external'>"
                        + lc.getContent().get(0).toString().trim() + "</a>");
                    logger.info("Add LINK:" + linkRef);
                }
            } else if (de instanceof JAXBElement) {
                JAXBElement j = (JAXBElement) de;
                if (logger.isDebugEnabled())
                    logger.debug("  Parsing note-elem:" + j.getName());
                if (j.getName().toString().equalsIgnoreCase("HOGR")) {
                    noteString = noteString.trim() + " (" + j.getValue().toString() + ")";
                } else if (j.getName().toString().equalsIgnoreCase("B")
                    || j.getName().toString().equalsIgnoreCase("I")) {
                    // Remove Bold and Italics
                    noteString = noteString + j.getValue().toString();
                } else if (j.getName().toString().equalsIgnoreCase("BR")) {
                    // Add newline
                    noteString = noteString + "\n";
                } else {
                    logger.error("  JABX-element Unhandled note-class " + j.getName().toString());
                    // statusList.put(currentRecord,
                    // new StatusMessage(currentRecord, "Unhandled note-class " +
                    // j.getName().toString()));
                    statusList.add(new StatusMessage(currentRecord, "Unhandled note-class " + j.getName().toString()));
                }
            } else {
                logger.error("Unhandled note-class " + de.getClass().getTypeName());
            }

            if (logger.isDebugEnabled())
                logger.debug("note-String=" + noteString);
        }

        // Add note if exist.
        if (!noteString.isEmpty()) {
            // Remove extra spaces around
            noteString = noteString.trim();
            // Replace multiple spaces with one
            noteString = noteString.replaceAll("( )+", " ");
            if(logger.isDebugEnabled()) {
                logger.debug("handleNote() Adding note:" + noteString);
            }
            Attribute att = new Attribute(lang, noteString);
            addProperty("note", parentProperties, att);
            return att;
        } else
            return null;
    }

    /**
     * Sample of incoming synonyms <SY>
     * <TERM>examensarbete<GRAM gend="n"></GRAM></TERM> <SCOPE>akademisk</SCOPE>
     * <SOURF>fisv_utbild_ordlista + kielityoryhma_sv</SOURF> </SY> <SY>
     * <EQUI value="near-equivalent"></EQUI> <TERM>vetenskapligt
     * arbete<GRAM gend="n"></GRAM></TERM> <SCOPE>akademisk</SCOPE>
     * <SOURF>fisv_utbild_ordlista + kielityoryhma_sv</SOURF> </SY>
     */

    private GenericNode handleSY(Termcontent synonym,
                                 String lang,
                                 Map<String, List<Attribute>> parentProperties,
                                 Map<String, List<Identifier>> parentReferences,
                                 GenericNode terminology) {
        if (logger.isDebugEnabled())
            logger.debug("handleSY-part:" + synonym.toString());
        // Synonym fields
        String equi = "";
        // Attributes are stored to property-list
        Map<String, List<Attribute>> properties = new HashMap<>();
        if (synonym.getGEOG() != null) {
            String lng = (lang.toLowerCase() + "-" + synonym.getGEOG().toUpperCase());
            // lang=lang+"-"+synonym.getGEOG();
            lang = lng;
        }

        synonym.getEQUI();
        if (synonym.getEQUI() != null) {
            // Attribute string value = broader | narrower | near-equivalent
            EQUI eqt = synonym.getEQUI();
            equi = eqt.getValue();
            String eqvalue = "=";
            if (equi.equalsIgnoreCase("broader"))
                eqvalue = ">";
            if (equi.equalsIgnoreCase("narrower"))
                eqvalue = "<";
            if (equi.equalsIgnoreCase("near-equivalent"))
                eqvalue = "~";

            Attribute att = new Attribute(null, eqvalue);
            // Attribute att = new Attribute(lang, eqvalue);
            addProperty("termEquivalency", properties, att);
        }
        if (synonym.getHOGR() != null) {

            // Attribute att = new Attribute(lang, synonym.getHOGR());
            Attribute att = new Attribute(null, synonym.getHOGR());
            addProperty("termHomographNumber", properties, att);
        }
        if (synonym.getSCOPE() != null) {
            SCOPE sc = synonym.getSCOPE();
            sc.getContent().forEach(o -> {
                if (o instanceof String) {
                    Attribute att = new Attribute(null, o.toString());
                    // Attribute att = new Attribute(lang, o.toString());
                    addProperty("scope", properties, att);
                } else {
                    logger.error("SCOPE unknown instance type:" + o.getClass().getName());
                    // statusList.put(currentRecord,
                    // new StatusMessage(currentRecord, "SCOPE unknown instance type:" +
                    // o.getClass().getName()));
                    statusList.add(
                        new StatusMessage(currentRecord, "SCOPE unknown instance type:" + o.getClass().getName()));
                }
            });
        }
        if (synonym.getSOURF() != null) {
            handleSOURF(synonym.getSOURF(), null, properties, terminology);
            // handleSOURF(synonym.getSOURF(),lang,properties,vocabularity);
        }
        if (synonym.getTERM() != null) {
            handleTERM(synonym.getTERM(), lang, properties);
        }
        if (synonym.getADD() != null) {
            handleADD(lang, synonym.getADD(), properties);
        }

        // create new synonym node (Term)
        TypeId typeId = typeMap.get("Term").getDomain();
        // Uri is parent-uri/term-'code'
        GenericNode node;
        UUID id = UUID.randomUUID();
        String code = "term-" + id.toString();
        String uri = terminology.getUri().endsWith("/")? terminology.getUri() + code:terminology.getUri()+"/" + code; 
        node = new GenericNode(id, code, uri, 0L, "", new Date(), "", new Date(),
            typeId, properties, emptyMap(), emptyMap());
        // Add id for reference resolving
        createdIdMap.put(node.getCode(), node.getId());
        return node;
    }

    /**
     * From
     *
     * @param source
     * @param lang
     * @param properties
     * @param terminology
     * @return
     */
    private Attribute handleSOURF(SOURF source,
                                  String lang,
                                  Map<String, List<Attribute>> properties,
                                  GenericNode terminology) {
        if (logger.isDebugEnabled())
            logger.debug("handleSOURF-part" + source.getContent());

        String sourceString = "";

        List<?> sourceItems = source.getContent();
        for (Object se : sourceItems) {
            if (se instanceof String) {
                sourceString = sourceString.concat(se.toString());
            } else if (se instanceof NCON) {
                NCON rc = (NCON) se;
                sourceString = sourceString.concat("<a href='" + terminology.getUri());
                if (rc.getTypr() != null && !rc.getTypr().isEmpty()) {
                    sourceString = sourceString.concat(" data-typr ='" + rc.getTypr() + "'");
                }
                sourceString = sourceString.concat(">" + rc.getContent().toString() + "</a>");
            } else if (se instanceof BCON) {
                BCON bc = (BCON) se;
                sourceString = sourceString.concat("<a href='" + terminology.getUri());
                if (bc.getTypr() != null && !bc.getTypr().isEmpty()) {
                    sourceString = sourceString.concat(" data-typr ='" + bc.getTypr() + "'");
                }
                sourceString = sourceString.concat(">" + bc.getContent().toString() + "</a>");

            } else if (se instanceof RCON) {
                RCON rc = (RCON) se;
                sourceString = sourceString.concat("<a href='" + terminology.getUri());
                if (rc.getTypr() != null && !rc.getTypr().isEmpty()) {
                    sourceString = sourceString.concat(" data-typr ='" + rc.getTypr() + "'");
                }
                sourceString = sourceString.concat(">" + rc.getContent().toString() + "</a>");
            } else {
                if (se instanceof JAXBElement) {
                    JAXBElement j = (JAXBElement) se;
                    if (j.getName().toString().equalsIgnoreCase("SOURF")) {
                        RCON rc = (RCON) j.getValue();
                        sourceString = sourceString.concat("<a href='" + terminology.getUri());
                        if (rc.getTypr() != null && !rc.getTypr().isEmpty()) {
                            sourceString = sourceString.concat(" data-typr ='" + rc.getTypr() + "'");
                        }
                        sourceString = sourceString.concat(">" + rc.getContent().get(0) + "</a>");
                    } else if (j.getName().toString().equalsIgnoreCase("SOURF")) {
                        SOURF sf = (SOURF) j.getValue();
                        if (sf.getContent() != null && sf.getContent().size() > 0) {
                            sourceString = sourceString.concat(" " + sf.getContent());
                            // Add refs as string and construct lines four sources-part.
                            updateSources(sf.getContent(), lang, properties);
                        }
                    } else {
                        logger.error("  UNKNOWN  SOURF-class" + se.getClass().getName());
                        // statusList.put(currentRecord, new StatusMessage(currentRecord,
                        // "SOURF unknown instance type:" + se.getClass().getName()));
                        statusList.add(new StatusMessage(currentRecord,
                            "SOURF unknown instance type:" + se.getClass().getName()));
                    }
                }
            }
        }
        ;
        // Add definition if exist.

        if (!sourceString.isEmpty()) {
            Attribute att = new Attribute(lang, sourceString);
            addProperty("source", properties, att);
            return att;
        } else
            return null;
    }

    /**
     * Add individual source-elements to the source-list for each individual
     * reference enumerated inside imported SOURF
     *
     * @param srefs
     * @param lang
     * @param properties
     */
    private void updateSources(List<Object> srefs,
                               String lang,
                               Map<String, List<Attribute>> properties) {
        for (Object o : srefs) {
            updateSources(o.toString(), lang, properties);
        }
        ;
    }

    /**
     * Add individual source-elements from give string
     *
     * @param srefs
     * @param lang
     * @param properties
     */
    private void updateSources(String srefs,
                               String lang,
                               Map<String, List<Attribute>> properties) {
        String fields[] = srefs.split("\\+");
        for (String s : fields) {
            s = s.trim();
            String sourcesString = "[" + s + "]";
            Map<String, String> m = referenceMap.get(s);
            if (m != null) {
                if (m.get("text") != null && !m.get("text").isEmpty()) {
                    sourcesString = sourcesString.concat("\n " + m.get("text") + "\n");
                }
                if (m.get("url") != null && !m.get("url").isEmpty()) {
                    sourcesString = sourcesString.concat(m.get("url"));
                }
            } else {
                logger.warn("Not matching reference found for:" + s);
                // statusList.put(currentRecord,
                // new StatusMessage(currentRecord, "Not matching reference found for :" + s));
                statusList.add(new StatusMessage(currentRecord, "Not matching reference found for :" + s));
            }
            if (!sourcesString.isEmpty()) {
                if (logger.isDebugEnabled())
                    logger.debug("ADDING sourf:" + sourcesString);
                Attribute satt = new Attribute(lang, sourcesString);
                addProperty("source", properties, satt);
            }
        }
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

    private static <K, V> Map<K, List<V>> mapMapValues(Map<K, List<V>> map,
                                                       Function<V, V> mapper) {
        return map.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> mapToList(e.getValue(), mapper)));
    }

    private static boolean isUUID(String s) {
        return UUID_PATTERN.matcher(s).matches();
    }

    /**
     * Can be used in with BCON, NCON and RCON references
     */
    private class ConnRef {

        String code;
        String referenceString;
        String type;
        UUID id;
        UUID targetId;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getReferenceString() {
            return referenceString;
        }

        public void setReferenceString(String referenceString) {
            this.referenceString = referenceString;
        }

        public UUID getTargetId() {
            return targetId;
        }

        public void setTargetId(UUID targetId) {
            this.targetId = targetId;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    private class StatusMessage {

        Level level;
        String record;
        List<String> message = new ArrayList<>();

        public StatusMessage(String record,
                             String msg) {
            this.level = Level.WARNING;
            this.record = record;
            this.message.add(msg);
        }

        public StatusMessage(Level level,
                             String record,
                             String msg) {
            this.level = level;
            this.record = record;
            this.message.add(msg);
        }

        public ImportStatusMessage.Level getLevel() {
            return level;
        }

        public void setLevel(Level level) {
            this.level = level;
        }

        public String getRecord() {
            return record;
        }

        public void setRecord(String record) {
            this.record = record;
        }

        public List<String> getMessage() {
            return message;
        }

        public void setMessage(List<String> message) {
            this.message = message;
        }

        public void putMessage(String msg) {
            this.message.add(msg);
        }
    }

    private class ImportState {

        /**
         * Map containing node.code or node.uri as a key and UUID as a value. Used for
         * matching existing items and updating them instead of creating new ones
         */
        public HashMap<String, UUID> idMap = new HashMap<>();
        public HashMap<UUID, String> reverseIdMap = new HashMap<>();
        /**
         * Map containing node.code or node.uri as a key and UUID as a value. Used for
         * reference resolving after all concepts and terms are created
         */
        public HashMap<String, UUID> createdIdMap = new HashMap<>();

        /**
         * Map binding together reference string and external URL fromn ntrf
         * SOURF-element
         */
        public HashMap<String, HashMap<String, String>> referenceMap = new HashMap<>();

        /**
         * Map for NCON/RCON-reference cache. Operation targetId,
         * type(generic/partitive), broaderConceptId
         */
        public Map<String, List<ConnRef>> nconList = new LinkedHashMap<>();
        public Map<String, List<ConnRef>> rconList = new LinkedHashMap<>();
        public Map<String, List<ConnRef>> bconList = new LinkedHashMap<>();

        public String currentRecord;
        public Map<String, StatusMessage> statusList = new LinkedHashMap<>();

        public int errorCount = 0;

    }
}
