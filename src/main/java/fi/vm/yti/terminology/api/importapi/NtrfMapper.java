package fi.vm.yti.terminology.api.importapi;

import fi.vm.yti.mq.service.YtiMQService;
import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.frontend.FrontendGroupManagementService;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.model.ntrf.*;
import fi.vm.yti.terminology.api.model.termed.*;
import fi.vm.yti.terminology.api.security.AuthorizationManager;
import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.util.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static fi.vm.yti.terminology.api.util.CollectionUtils.mapToList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpMethod.POST;

@Component
public class NtrfMapper {

    private static final String USER_PASSWORD = "user";
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
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
     * Map containing metadata types. used  when creating nodes.
     */
    private HashMap<String,MetaNode> typeMap = new HashMap<>();
    /**
     * Map containing node.code or node.uri as a key and  UUID as a value. Used for matching existing items and updating
     * them instead of creating new ones
     */
    private HashMap<String,UUID> idMap = new HashMap<>();
    /**
     * Map containing node.code or node.uri as a key and  UUID as a value. Used for reference resolving after all
     * concepts and terms are created
     */
    private HashMap<String,UUID> createdIdMap = new HashMap<>();

    /**
     * Map binding together reference string and external URL fromn ntrf SOURF-element
     */
    private HashMap<String,HashMap<String,String>> referenceMap = new HashMap<>();

    /**
     * Map for NCON/RCON-reference cache. Operation targetId, type(generic/partitive), broaderConceptId
     */
    private List<NconRef> nconList = new ArrayList<>();
    private List<RconRef> rconList = new ArrayList<>();

    private String currentRecord;
    private Map<String,StatusMessage> statusList = new LinkedHashMap<>();

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

    private void updateAndDeleteInternalNodes(UUID userId, GenericDeleteAndSave deleteAndSave, boolean sync) {

        Parameters params = new Parameters();
        params.add("changeset", "true");
        params.add("sync", String.valueOf(sync));

        this.termedRequester.exchange("/nodes", POST, params, String.class, deleteAndSave, userId.toString(), USER_PASSWORD);
    }

    /**
     * Executes import operation. Reads incoming xml and process it
     * @param vocabularyId
     * @param ntrfDocument
     * @return
     */
   String mapNtrfDocument( String jobtoken, UUID vocabularyId, VOCABULARY ntrfDocument, UUID userId) {
        Graph vocabulary = null;
        logger.info("mapNtRfDocument: Vocabularity Id:"+vocabularyId);
        Long startTime = new Date().getTime();

        // Get vocabulary
        try {
            vocabulary = termedService.getGraph(vocabularyId);
        } catch ( NullPointerException nex){
            // Vocabularity not found
            logger.error("Vocabulary:<" + vocabularyId + "> not found");
            return "Vocabulary:<" + vocabularyId + "> not found";
        }

        if(!initImport(vocabularyId)){
            return "Vocabulary:<" + vocabularyId + "> initialization error";
        }

        // Get statistic of terms
        List<?> l = ntrfDocument.getRECORDAndHEADAndDIAG();
        logger.info("Incoming objects count=" + l.size());

        // Get all reference-elements and build reference-url-map
        List<REFERENCES> externalReferences = l.stream().filter(o -> o instanceof REFERENCES).map(o -> (REFERENCES) o).collect(Collectors.toList());
        handleReferences(externalReferences,referenceMap);
        logger.info("Incoming reference count=" + externalReferences.size());

        // Get all records (mapped to terms) from incoming ntrf-document. Check object type and typecast matching objects to  list<>
        List<RECORD> records = l.stream().filter(o -> o instanceof RECORD).map(o -> (RECORD) o).collect(Collectors.toList());
        logger.info("Incoming records count=" + records.size());
        System.out.println("    userProvider="+userProvider.getUser());
        List<GenericNode> addNodeList = new ArrayList<>();

        ImportStatusResponse response = new ImportStatusResponse();
        response.setStatus("Import started");
        System.out.println("Active user="+ userId);

        ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, jobtoken, userId.toString(), vocabulary.getUri(),response.toString());
        int flushCount = 0;
        int currentCount = 0;
        for(RECORD o:records){
            currentRecord=o.getNumb();
            handleRECORD(vocabulary, o, addNodeList);
            flushCount++;
            currentCount++;
            if(flushCount >500){
                flushCount=0;
                GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(),addNodeList);
                if(logger.isDebugEnabled())
                    logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
                updateAndDeleteInternalNodes(userId, operation,true);
                addNodeList.clear();
                response.setStatus("Processing records");
                response.setProgress(currentCount+"/"+records.size());
                ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, jobtoken, userId.toString(), vocabulary.getUri(),response.toString());
            }
        }
        GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(),addNodeList);
        if(logger.isDebugEnabled())
            logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
        updateAndDeleteInternalNodes(userId, operation,true);
        addNodeList.clear();

        Long endTime = new Date().getTime();

        // ReInitialize caches and after that, resolve ncon-references
        idMap.clear();
        typeMap.clear();
        initImport(vocabularyId);
        handleLinks(userId, jobtoken, vocabulary);

        // Handle DIAG-elements and create collections from them
        List<DIAG> DIAGList = l.stream().filter(o -> o instanceof DIAG).map(o -> (DIAG) o).collect(Collectors.toList());
        for(DIAG o:DIAGList) {
            handleDIAG(vocabulary, o, addNodeList);
        }

       response.setStatus("Processing DIAG");
       response.setProgress("90/100");
       ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, jobtoken, userId.toString(), vocabulary.getUri(),response.toString());
        // Add DIAG-list to vocabulary
        operation = new GenericDeleteAndSave(emptyList(),addNodeList);
        if(logger.isDebugEnabled())
            logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
        updateAndDeleteInternalNodes(userId, operation, true);
        System.out.println("Operation  took "+(endTime-startTime)/1000+"s");
        logger.info("NTRF-imported "+records.size()+" terms.");
        System.out.println("Status----------------------------------------");
//        JsonUtils.prettyPrintJson(statusList);
       response.setStatus("Ready");
       response.setProgress("100/100");
       response.setPayload("Imported"+records.size()+" terms");
       ytiMQService.setStatus(YtiMQService.STATUS_READY, jobtoken, userId.toString(), vocabulary.getUri(),response.toString());
       return "{\"status\":\"Imported "+records.size()+" terms\",\"Errors\":"+JsonUtils.prettyPrintJsonAsString(statusList)+"\"}";
   }

    private void handleLinks(UUID userId, String jobtoken, Graph vocabulary){
        List<GenericNode> addNodeList = new ArrayList<>();
        if(nconList != null) {
            for(NconRef nref:nconList){
                if (nref.getId() == NULL_ID) {
                    UUID target = idMap.get(nref.referenceString);
                    System.out.println("Resolving originally unresolved id:" + nref.referenceString);
                    if (target != null) {
                        // Update it
                        nref.setId(target);
                    } else {
                        System.out.println("Can't resolve originally unresolved id:" + nref.referenceString);
                    }
                }
                if (nref.getId() != null && !nref.getId().equals(NULL_ID)) {
                    GenericNode gn = null;
                    try {
                        gn = termedService.getConceptNode(vocabulary.getId(), nref.getId());
                    } catch (NullPointerException nex) {
                        logger.warn("Can't found concept node:" + nref.getId() + " in vocabulary:" + vocabulary.getId().toString());
                    }
                    if (gn != null) {
                        // get objects reference list and add
                        Map<String, List<Identifier>> refMap = gn.getReferences();
                        List<Identifier> ref = null;
                        if (nref.getType().equalsIgnoreCase("generic")) {
                            ref = refMap.get("broader");
                        } else
                            ref = refMap.get("isPartOf");
                        if (ref == null)
                            ref = new ArrayList<>();
                        // @TODO! Go through refList and add only if missing.
                        // Generic = broader concept, partitive = related concept
                        ref.add(new Identifier(nref.getTargetId(), typeMap.get("Concept").getDomain()));
                        if (nref.getType().equalsIgnoreCase("generic")) {
                            refMap.put("broader", ref);
                        } else
                            refMap.put("isPartOf", ref);
                        System.out.println("NREF-list="+refMap);
                        addNodeList.add(gn);
                    }
                } else {
                    System.out.println("Cant' resolve following! Nref-id=" + nref.getId() + "-- vocab=" + vocabulary.getId().toString());
                }
            }
            System.out.println("NCON size="+nconList.size());
            if(addNodeList.size()>0) {
                // add NCON-changes as one big block
                GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(), addNodeList);
                if (logger.isDebugEnabled())
                    logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
                updateAndDeleteInternalNodes(userId, operation, true);
            }
        }
        addNodeList.clear();
        System.out.println(" Add resolved RCON-list");
        if(rconList != null) {
            for(RconRef rref:rconList){
                if (rref.getId() == NULL_ID) {
                    UUID target = idMap.get(rref.referenceString);
                    System.out.println("Resolving originally unresolved id:" + rref.referenceString);
                    if (target != null) {
                        // Update it
                        rref.setId(target);
                    } else {
                        System.out.println("Can't resolve originally unresolved id:" + rref.referenceString);
                    }
                }
                if (rref.getId() != null && !rref.getId().equals(NULL_ID)) {
                    GenericNode gn = null;
                    try {
                        gn = termedService.getConceptNode(vocabulary.getId(), rref.getId());
                    } catch (NullPointerException nex) {
                        logger.warn("Can't found concept node:" + rref.getId() + " in vocabulary:" + vocabulary.getId().toString());
                    }
                    if (gn != null) {
                        // get objects reference list and add
                        Map<String, List<Identifier>> refMap = gn.getReferences();
                        List<Identifier> ref = null;
                        if (rref.getType().equalsIgnoreCase("generic")) {
                            ref = refMap.get("broader");
                        } else
                            ref = refMap.get("isPartOf");
                        if (ref == null)
                            ref = new ArrayList<>();
                        // @TODO! Go through refList and add only if missing.
                        // Generic = broader concept, partitive = related concept
                        ref.add(new Identifier(rref.getTargetId(), typeMap.get("Concept").getDomain()));
                        if (rref.getType().equalsIgnoreCase("generic")) {
                            refMap.put("broader", ref);
                        } else
                            refMap.put("isPartOf", ref);
                        System.out.println("RREF-list="+refMap);
                        addNodeList.add(gn);
                    }
                } else {
                    System.out.println("Cant' resolve following! Rref-id=" + rref.getId() + "-- vocab=" + vocabulary.getId().toString());
                }
            }
            System.out.println("RCON size="+rconList.size());
            if(addNodeList.size()>0) {
                // add NCON-changes as one big block
                GenericDeleteAndSave operation = new GenericDeleteAndSave(emptyList(), addNodeList);
                if (logger.isDebugEnabled())
                    logger.debug(JsonUtils.prettyPrintJsonAsString(operation));
                updateAndDeleteInternalNodes(userId, operation, true);
            }
        }
    }

    /**
     * Initialize importer.
     * - Read given vocabularity for meta-types, cache them
     * - Read all existing nodes and cache their URI/UUID-values
     * @param vocabularityId UUID of the vocabularity
     */
    private boolean initImport(UUID vocabularityId){
        // Get metamodel types for given vocabularity
        List<MetaNode> metaTypes = termedService.getTypes(vocabularityId);
        metaTypes.forEach(t-> typeMap.put(t.getId(),t));

        // Create hashmap to store information  between code/URI and UUID so that we can update values upon same vocabularity
        List<GenericNode> nodeList = termedService.getNodes(vocabularityId);
        if(nodeList == null){
            return false;
        } else {
            nodeList.forEach(o -> {
                if (logger.isDebugEnabled())
                    logger.debug(" Code:" + o.getCode() + " UUID:" + o.getId().toString() + " URI:" + o.getUri());
                if (!o.getCode().isEmpty()) {
                    idMap.put(o.getCode(), o.getId());
                }
                if (!o.getUri().isEmpty()) {
                    idMap.put(o.getUri(), o.getId());
                }
            });
        }
        return true;
    }

    private void handleDIAG(Graph vocabularity, DIAG diag, List<GenericNode> addNodeList){
        String code = diag.getNumb();
        if(logger.isDebugEnabled())
            logger.debug("DIAG Name="+diag.getName()+" Code="+code);

        UUID collectionId=idMap.get(code);
        // Generate new if not update
        if(collectionId == null)
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
        l.forEach(li-> {
            String linkTarget = li.getHref();
            // Remove #
            if (linkTarget.startsWith("#"))
                linkTarget = linkTarget.substring(1);

            UUID targetUUID = idMap.get(linkTarget);
            if (targetUUID == null) {
                // try original
                targetUUID = idMap.get(li.getHref());
            }
            if(targetUUID != null && !targetUUID.equals(NULL_ID)) {
                memberRef.add(new Identifier(targetUUID, typeMap.get("Concept").getDomain()));
                references.put("member", memberRef);
            } else {
                logger.warn("DIAG:"+diag.getNumb()+" LINK-target " + li.getHref() +" <"+li.getContent() +"> not added into the collection");
                statusList.put(currentRecord,new StatusMessage(currentRecord,
                        "DIAG:"+diag.getNumb()+" LINK-target " + li.getHref() +" <"+li.getContent() +"> not added into the collection"));
            }

        });

        // Construct Node as Collection-type
        GenericNode node = new GenericNode(collectionId, code, vocabularity.getUri() + code, 0L, userProvider.getUser().getUsername(), new Date(), "", new Date(), typeMap.get("Collection").getDomain(), properties, references, emptyMap());
        // Just add it
        addNodeList.add(node);
    }

    /**
     * TSK-NTRF packs external references as REFERENCES-items. Here we go through them and cache
     * values for later usage
     * @param referencesTypeList
     * @param refMap
     */
    private void handleReferences(List<REFERENCES>referencesTypeList, HashMap<String,HashMap<String,String>> refMap){
        referencesTypeList.forEach(reftypes -> {
            List<REF> refs = reftypes.getREFOrREFHEAD().stream().filter(o -> o instanceof REF).map(o -> (REF) o).collect(Collectors.toList());
            refs.forEach(r -> {
                // Filter all JAXBElements

                List<JAXBElement> elems =null;
                HashMap<String, String> fields = new HashMap<>();
                String name = r.getREFNAME();
                String url = r.getREFLINK();
                String text = "";

                // Text is a structured component
                // In DTD this is a string or REMK.... <!ELEMENT REFTEXT (#PCDATA | REMK | B | I | BR | LINK)*>
                REFTEXT rtx = r.getREFTEXT();
                elems = rtx.getContent().stream().filter(o -> o instanceof JAXBElement).map(o -> (JAXBElement) o).collect(Collectors.toList());
                for(JAXBElement o:elems){
                    if (o.getName().toString().equalsIgnoreCase("REFNAME")) {
                        name = o.getValue().toString();
                    }
                    if (o.getName().toString().equalsIgnoreCase("REFTEXT")) {
                        REFTEXT rt = (REFTEXT) o.getValue();
                        // Can be String | REMK | B | I | BR | LINK
                        //@TODO! add handling
                        text = rt.getContent().toString();
                        fields.put("text", text);
                    }
                    if (o.getName().toString().equalsIgnoreCase("REFLINK")) {
                        if (!o.getValue().toString().isEmpty()) {
                            url = o.getValue().toString();
                            fields.put("url", url);
                        }
                    }
                    if(logger.isDebugEnabled())
                        logger.debug(" Cache incoming external references: field=" +fields);
                };
                // add fields to referenceMap
                if(name != null) {
                    System.out.println("PUT REFMAP id:" + name + " value:" + fields);
                    refMap.put(name, fields);
                }
            });
        });
    }

    /**
     * Handle mapping of RECORD. NTRF-models all concepts as RECORD-fields. It contains actual terms and references
     * to other concepts and external links. See following example
     * incomimg NTRF:
     *   <RECORD numb="tmpOKSAID116" upda="Riina Kosunen, 2018-03-16">
     *     <LANG value="fi">
     *       <TE>
     *         <TERM>kasvatus</TERM>
     *         <SOURF>SRemes</SOURF>
     *       </TE>
     *       <DEF>vuorovaikutukseen perustuva toiminta, jonka tavoitteena on kehittää yksilöstä eettisesti vastuukykyinen yhteiskunnan jäsen<SOURF>wikipedia + rk + pikkuryhma_01 + tr45 + tr49 + ssu + vaka_tr_01 + vaka_tr_02 + tr63</SOURF></DEF>
     *       <NOTE>Kasvatuksen myötä kulttuuriset arvot, tavat ja normit välittyvät ja muovautuvat. Osaltaan kasvatuksen tavoite on siirtää kulttuuriperintöä sekä tärkeinä pidettyjä arvoja ja traditioita seuraavalle sukupolvelle, mutta kasvatuksen avulla halutaan myös uudistaa ajattelu- ja toimintatapoja. Kasvatuksen sivistystehtävänä on tietoisesti ohjata yksilöllisen identiteetin muotoutumista ja huolehtia, että muotoutuminen tapahtuu sosiaalisesti hyväksyttävällä tavalla.<SOURF>vaka_tr_02 + tr63</SOURF></NOTE>
     *       <NOTE>Varhaiskasvatuksella tarkoitetaan varhaiskasvatuslain (<LINK href="https://www.finlex.fi/fi/laki/ajantasa/1973/19730036">36/1973</LINK>) mukaan lapsen suunnitelmallista ja tavoitteellista kasvatuksen, <RCON href="#tmpOKSAID117">opetuksen (1)</RCON> ja hoidon muodostamaa kokonaisuutta, jossa painottuu pedagogiikka.<SOURF>vk-peruste + ssu + vaka_tr_02</SOURF></NOTE>
     *       <NOTE><RCON href="#tmpOKSAID452">Perusopetuksella</RCON> on opetustavoitteiden lisäksi kasvatustavoitteita. Perusopetuslain (<LINK href="https://www.finlex.fi/fi/laki/ajantasa/1998/19980628">628/1998</LINK>) mukaan perusopetuksella pyritään kasvattamaan oppilaita ihmisyyteen ja eettisesti vastuukykyiseen yhteiskunnan jäsenyyteen sekä antamaan heille elämässä tarpeellisia tietoja ja taitoja.<SOURF>rk + pikkuryhma_01 + tr45</SOURF></NOTE>
     *       <NOTE>Yliopistolain (<LINK href="https://www.finlex.fi/fi/laki/ajantasa/2009/20090558">558/2009</LINK>) mukaan <RCON href="#tmpOKSAID162">yliopistojen</RCON> tehtävänä on edistää vapaata tutkimusta sekä tieteellistä ja taiteellista sivistystä, antaa tutkimukseen perustuvaa ylintä opetusta (1) sekä kasvattaa <RCON href="#tmpOKSAID227">opiskelijoita</RCON> palvelemaan isänmaata ja ihmiskuntaa.<SOURF>558/2009 + tr45</SOURF></NOTE>
     *       <NOTE>Englannin käsite education on laajempi kuin suomen kasvatus, niin että termillä education viitataan kasvatuksen lisäksi muun muassa <RCON href="#tmpOKSAID117">opetukseen (1)</RCON>, <RCON href="#tmpOKSAID121">koulutukseen (1)</RCON> ja sivistykseen.<SOURF>rk + KatriSeppala</SOURF></NOTE>
     *       <NOTE>Käsitteen tunnus: tmpOKSAID116</NOTE>
     *     </LANG>
     *     <LANG value="sv">
     *       <TE>
     *         <TERM>fostran</TERM>
     *         <SOURF>36/1973_sv + kielityoryhma_sv_04</SOURF>
     *       </TE>
     *     </LANG>
     *     <LANG value="en">
     *       <TE>
     *         <EQUI value="broader"></EQUI>
     *         <TERM>education</TERM>
     *         <HOGR>1</HOGR>
     *         <SOURF>ophall_sanasto</SOURF>
     *       </TE>
     *       <SY>
     *         <TERM>upbringing</TERM>
     *         <SOURF>MOT_englanti</SOURF>
     *       </SY>
     *     </LANG>
     *     <BCON href="#tmpOKSAID122" typr="generic">koulutus (2)</BCON>
     *     <NCON href="#tmpOKSAID123" typr="generic">koulutuksen toteutus</NCON>
     *     <CLAS>yleinen/yhteinen</CLAS>
     *     <CHECK>hyväksytty</CHECK>
     *   </RECORD>
     * @param vocabularity Graph-node from termed. It contains base-uri where esck Concept and Term is bound
     * @param r
     */
    void handleRECORD(Graph vocabularity, RECORD r, List<GenericNode> addNodeList) {
        String code="";
        UUID currentId=null;
        String uri="";
        Long number= 0L;
        String createdBy;
        LocalDate createdDate;
        String lastModifiedBy = null;
        LocalDate lastModifiedDate= LocalDate.now();
        // Attributes  are stored to property-list
        Map<String, List<Attribute>> properties = new HashMap<>();
        // references synomyms and preferred tems and so on
        Map<String, List<Identifier>> references = new HashMap<>();

        code = r.getNumb();
        // Check  whether id exist and create id
        if(idMap.get(code) != null) {
            System.out.println(" UPDATE operation!!!!!!!!! " + code);
            currentId = idMap.get(code);
        } else {
            System.out.println(" CREATE NEW  operation!!!!!!!!! " + code);
            currentId = UUID.randomUUID();
        }

        // Default  creator is importing user
        createdBy = userProvider.getUser().getUsername();

        logger.info("Record id:"+code);
        // Add info to editorial note
        String editorialNote = "";

        // Stat can be 'vanhentunut', 'aputermi', 'ulottuvuus'
        if(r.getStat() != null)
            editorialNote = r.getStat();
        if(r.getUpda() != null) {
            // Store that information to the  modificationHistory
            String updater=r.getUpda();
            String upd[]=r.getUpda().split(",");
            if(upd.length == 2) {
                DateTimeFormatter df =  DateTimeFormatter.ofPattern("yyyy-MM-dd");
                // User,  update date
                lastModifiedBy = upd[0].trim();
                try {
                    lastModifiedDate = LocalDate.parse(upd[1].trim(), df);
                    editorialNote = editorialNote+" -"+lastModifiedBy+", "+lastModifiedDate;
                } catch(DateTimeParseException dex){
                    statusList.put(currentRecord,new StatusMessage(currentRecord,
                            "Parse error for date"+dex.getMessage()));
                    System.out.println("Parse error for date"+dex.getMessage());
                }
            }
        }
        if(!editorialNote.isEmpty()) {
            Attribute att = new Attribute("fi", editorialNote);
            addProperty("editorialNote", properties, att);
        }

        // Resolve terms and  collect list of them for insert.
        List<GenericNode> terms = new ArrayList<>();
        // Filter LANG elemets as list.
        List<LANG> langs = r.getLANG();
        langs.forEach(o -> {
            // RECORD/LANG/TE/TERM -> prefLabel
            hadleLANG(terms, o, properties, references, vocabularity);
        });
        // Filter CLAS elemets as list
        List<CLAS> clas = r.getCLAS();
        clas.forEach(o -> {
            //RECORD/CLAS ->
            handleCLAS(o, properties);
        });
        // Filter CHECK elemets as list
        if(r.getCHECK() != null && !r.getCHECK().isEmpty()) {
            //RECORD/CHECK ->
            handleCHECK(r.getCHECK(), r.getStat(), properties);
        }

        // stat-attribute overrides CHECK
        if(r.getStat()!= null && !r.getStat().isEmpty()){
            handleStat(r.getStat(), properties);
        }
        if(r.getREMK()!= null){
            r.getREMK().forEach(o -> {
                handleREMK("",o,properties, vocabularity);
            });
        }
        // Filter BCON elemets as list
        List<BCON> bcon = r.getBCON();
        bcon.forEach(o -> {
            System.out.println("--BCON=" + o.getHref());
            //RECORD/BCON
            handleBCON(o,references);
        });
        // Filter NCON elemets as list
        List<NCON> ncon = r.getNCON();
        for(NCON o:ncon) {
            if( o.getHref()!= null && !o.getHref().isEmpty()) {
                String nrefId = o.getHref().substring(1);
                //RECORD/BCON
                // Store information of link for now on and after all items are created, update broader/isPartOf-references
                handleNCON(o, currentId);
            } else {
                statusList.put(currentRecord,new StatusMessage(currentRecord,
                        "Record:"+code+" has null NCON reference."));
                logger.warn("Record:"+code+" has null NCON reference.");
            }
        }

        TypeId typeId = null;
        typeId = typeMap.get("Concept").getDomain();
        GenericNode node = null;
        node = new GenericNode(currentId, code, vocabularity.getUri() + code, 0L, createdBy, new Date(), "", new Date(), typeId, properties, references, emptyMap());
        // Send item to termed-api
        // First add terms
        terms.forEach(t->{addNodeList.add(t);});
        // then concept itself
        addNodeList.add(node);
        // Add id for reference resolving
        createdIdMap.put(node.getCode(),node.getId());
    }

    /**
     * Handle mapping of individual Lang-element. This contains Terms which needs to be created and definition and some other information which
     * should be stored under parent concept
     * prefLabel value is found under LANG/TE/TERM
     * parent.definition is  under LANG/TE/DEF
     * parent.source-elements are found under LANG/TE/SOURF, LANG/TE/DEF/SOURF and LANG/TE/NOTE/SOURF all of them are mapped to same source-list
     * Incoming NTRF:
     *     <LANG value="fi">
     *       <TE>
     *         <TERM>opetus</TERM>
     *         <HOGR>1</HOGR>
     *         <SOURF>harmon + tr45</SOURF>
     *       </TE>
     *       <DEF>vuorovaikutukseen perustuva toiminta, jonka tavoitteena on <RCON href="#tmpOKSAID118">oppiminen</RCON><SOURF>wikipedia + rk + tr45</SOURF></DEF>
     *       <NOTE>Opetuksella (1) ja <RCON href="#tmpOKSAID116">kasvatuksella</RCON> on osin yhteneväisiä tavoitteita.<SOURF>vaka_tr_02 + tr63</SOURF></NOTE>
     *       <NOTE>Englannin käsite education on laajempi kuin suomen opetus (1), niin että termillä education viitataan opetuksen (1) lisäksi muun muassa <RCON href="#tmpOKSAID116">kasvatukseen</RCON>, <RCON href="#tmpOKSAID121">koulutukseen (1)</RCON> ja sivistykseen.<SOURF>rk + KatriSeppala</SOURF></NOTE>
     *       <NOTE>Käsitteen tunnus: tmpOKSAID117</NOTE>
     *     </LANG>
     *     <LANG value="en">
     *       <TE>
     *         <EQUI value="broader"></EQUI>
     *         <TERM>education</TERM>
     *         <HOGR>1</HOGR>
     *         <SOURF>ophall_sanasto</SOURF>
     *       </TE>
     *       <SY>
     *         <TERM>upbringing</TERM>
     *         <SOURF>MOT_englanti</SOURF>
     *       </SY>
     *     </LANG>
     * @param o LANGType containing incoming NTRF-block
     * @param vocabularity Graph-element containing  information of  parent vocabularity like id and base-uri
     */
    private int hadleLANG(List<GenericNode> termsList, LANG o, Map<String, List<Attribute>> parentProperties, Map<String, List<Identifier>> parentReferences, Graph vocabularity)  {
        // generate random UUID as a code and use it as part if the generated URI
        String code = UUID.randomUUID().toString();

        logger.info("Handle LANG:"+ o.getValue());

        // Attributes  are stored to property-list
        Map<String, List<Attribute>> properties = new HashMap<>();

        // Filter TE elemets as list and add mapped elements as properties under node
        if(o.getTE() != null){
            // TE/TERM TE/TERM/GRAM
            // TE/SOURF
            // TE/REMK
            // TE/HOGR
            // TE/EQUI
            // TE/SCOPE
            // TE/ADD
            handleTE(o.getTE(),
                    o.getValue().value(), // lang
                    properties,
                    parentProperties,
                    vocabularity);
        }

        //DEFINITION
        List<DEF> def = o.getDEF();
        // Definition is complex multi-line object which needs to be resolved
        for(DEF d:def){
            handleDEF(d, o.getValue().value(), parentProperties, parentReferences, properties,vocabularity);
        }
        // NOTE
        List<NOTE> notes = o.getNOTE();
        for(NOTE n:notes){
            handleNOTE(n, o.getValue().value(), parentProperties, parentReferences, properties, vocabularity);
        }

        // SY (synonym) is just like TE
        List <Termcontent> synonym = o.getSY();
        synonym.forEach(obj->{
            GenericNode n = handleSY(obj, o.getValue().value(), parentProperties, parentReferences, vocabularity);
            if(n != null){
                termsList.add(n);
                List<Identifier> ref;
                if(parentReferences.get("altLabelXl") != null)
                    ref = parentReferences.get("altLabelXl");
                else
                    ref = new ArrayList<>();
                ref.add(new Identifier(n.getId(), typeMap.get("Term").getDomain()));
                parentReferences.put("altLabelXl",ref);
            }
        });


        TypeId typeId = typeMap.get("Term").getDomain();
        // Uri is  parent-uri/term-'code'
        GenericNode node = null;
        if(idMap.get(code) != null) {
            if(logger.isDebugEnabled())
                logger.debug("Update Term");
            node = new GenericNode(idMap.get(code),code, vocabularity.getUri() + "term-" + code, 0L, "", new Date(), "", new Date(), typeId, properties, emptyMap(), emptyMap());
        }
        else {
            node = new GenericNode(code, vocabularity.getUri() + "term-" + code, 0L, "", new Date(), "", new Date(), typeId, properties, emptyMap(), emptyMap());
            // Set just created term as preferred term for concept

            List<Identifier> ref;
            if(parentReferences.get("prefLabelXl") != null)
                ref = parentReferences.get("prefLabelXl");
            else
                ref = new ArrayList<>();
            ref.add(new Identifier(node.getId(),typeId));
            parentReferences.put("prefLabelXl",ref);
        }
        termsList.add(node);
        // Add id for reference resolving
        createdIdMap.put(node.getCode(),node.getId());
        return termsList.size();
    }


    private void handleTERM(TERM term, String lang,
                            Map<String, List<Attribute>> properties){
        if(logger.isDebugEnabled())
            logger.debug("Handle Term:"+term.toString());
        term.getContent().forEach( li-> {
            if (li instanceof String) {
                Attribute att = new Attribute(lang, li.toString().trim());
                addProperty("prefLabel", properties, att);
            } else if(li instanceof GRAM && li != null ){
                handleGRAM((GRAM)li, properties);
            } else {
                System.out.println(" TERM: unhandled contentclass="+li.getClass().getName()+" value="+li.toString());
            }
        });
    }

    private void handleTE(Termcontent tc,
                          String lang,
                          Map<String, List<Attribute>> properties,
                          Map<String, List<Attribute>> parentProperties,
                          Graph vocabularity){
        if(logger.isDebugEnabled())
            logger.debug("Handle Te:"+tc.toString());
        // LANG/TE/TERM

        if(tc.getTERM()!=null){
            handleTERM(tc.getTERM(),lang,properties);
        }

        // LANG/TE/SOURF
        if(tc.getSOURF() != null)
            handleSOURF(tc.getSOURF(), lang, parentProperties,vocabularity);
        // LANG/TE/HOGR
        if(tc.getHOGR() != null && !tc.getHOGR().isEmpty()){
            Attribute att = new Attribute(lang, tc.getHOGR());
            addProperty("termHomographNumber", properties,  att);
        }

        //LANG/TE/SCOPE
        if(tc.getSCOPE() != null) {
            handleSCOPE(lang, tc.getSCOPE(),properties);
        }
        //LANG/TE/EQUI
        if(tc.getEQUI()!= null){
            handleEQUI(lang,tc.getEQUI(),properties);
        }
        //LANG/TE/REMK
        if(tc.getREMK() != null){
            handleREMK(lang,tc.getREMK(),properties, vocabularity);
        }
    }

    private String getAttributeContent( List<Serializable> li) {
        String value = null;
        if (!li.isEmpty()) { // if value exist
            value = li.get(0).toString();
        }
        return value;
    }

    private void handleREMK(String lang, REMK remk, Map<String, List<Attribute>> properties, Graph vocabularity){
        List<Attribute> eNotes = properties.get("editorialNote");
        if(eNotes == null)
            eNotes= new ArrayList<Attribute>();

        List<?> content=remk.getContent();
        String editorialNote ="";
        for(Object o:content) {
            if (o instanceof String) {
                editorialNote = editorialNote + ((String) o).toString();
            } else if (o instanceof JAXBElement) {
                JAXBElement elem = (JAXBElement) o;
                editorialNote = editorialNote + elem.getValue().toString();
            } else if (o instanceof LINK) {
                LINK l = (LINK) o;
                editorialNote = editorialNote.concat("<a href='" + l.getHref() + "' data-type='external'>" + l.getContent().get(0) + "</a>");
            } else if(o instanceof SOURF){
                handleSOURF((SOURF)o,lang,properties,vocabularity);
            }else {
                statusList.put(currentRecord,new StatusMessage(currentRecord," REMK: unhandled contentclass="+o.getClass().getName()+" value="+o.toString()));
                System.out.println(" REMK: unhandled contentclass="+o.getClass().getName()+" value="+o.toString());
            }

        };
        if(!editorialNote.isEmpty()) {
            if(logger.isDebugEnabled())
                logger.debug("REMK  Editorial note!!!"+editorialNote);
            Attribute att = new Attribute("fi", editorialNote);
            addProperty("editorialNote", properties, att);
        }
    }

    private void handleEQUI(String lang, EQUI equi, Map<String, List<Attribute>> properties){
        // Attribute string value = broader | narrower | near-equivalent
        String eqvalue = "=";
        if (equi.getValue().equalsIgnoreCase("broader"))
            eqvalue = ">";
        if (equi.getValue().equalsIgnoreCase("narrower"))
            eqvalue = "<";
        if (equi.getValue().equalsIgnoreCase("near-equivalent"))
            eqvalue = "~";
        Attribute att = new Attribute(lang, equi.getValue());
        addProperty("termEquivalency", properties, att);
    }

    private void handleSCOPE(String lang, SCOPE scope, Map<String, List<Attribute>> properties){
        System.out.println("HandleScope = "+scope.getContent().toString());
        scope.getContent().forEach( li-> {
            if (li instanceof String) {
                Attribute att = new Attribute(lang, li.toString());
                addProperty("scope", properties, att);
            } else if (li instanceof LINK){
                // <SCOPE>yliopistolain <LINK href="https://www.finlex.fi/fi/laki/kaannokset/2009/en20090558_20160644.pdf">558/2009 käännöksessä</LINK></SCOPE>
                System.out.println("Unimplemented SCOPE WITH LINK");
                //@TODO! Make impl
            }
        });

    }

    private void handleGRAM(GRAM gt, Map<String, List<Attribute>> properties){
        if(logger.isDebugEnabled())
            logger.debug("Grammatical specification");
        // termConjugation (single, plural)
        if(gt.getValue() != null && gt.getValue().equalsIgnoreCase("pl")){
            // Currently not localized
            Attribute att = new Attribute("fi", "monikko");
            addProperty("termConjugation", properties,  att);
        } else if(gt.getValue() != null && gt.getValue().equalsIgnoreCase("n pl")){
            // Currently not localized plural and  neutral
            Attribute att = new Attribute("fi", "monikko");
            addProperty("termConjugation", properties,  att);
            att = new Attribute("fi", "neutri");
            addProperty("termFamily", properties,  att);
        }else if(gt.getValue() != null && gt.getValue().equalsIgnoreCase("f pl")){
            // Currently not localized plural and  neutral
            Attribute att = new Attribute("fi", "monikko");
            addProperty("termConjugation", properties,  att);
            att = new Attribute("fi", "feminiini");
            addProperty("termFamily", properties,  att);
        }
        // termFamily
        if(gt.getGend() != null && gt.getGend().equalsIgnoreCase("f")){
            // feminiini
            // Currently not localized
            Attribute att = new Attribute("fi", "feminiini");
            addProperty("termFamily", properties,  att);
        } else if(gt.getGend() != null && gt.getGend() != null && gt.getGend().equalsIgnoreCase("m")){
            // maskuliiini
            Attribute att = new Attribute("fi", "maskuliini");
            addProperty("termFamily", properties,  att);
        } else if(gt.getGend() != null && gt.getGend().equalsIgnoreCase("n")){
            // Neutri
            Attribute att = new Attribute("fi", "neutri");
            addProperty("termFamily", properties,  att);
        }
        // wordClass
        if(gt.getPos() != null && !gt.getPos().isEmpty()){
            // Currently not localized, just copy wordClass as such
            Attribute att = new Attribute("fi", gt.getPos());
            addProperty("wordClass", properties,  att);
        }
    }

    /**
     * Handle CHECK->status-property mapping
     * @param o CHECK-field
     * @param properties Propertylist where status is added
     */
    private Attribute handleCHECK(String o, String stat, Map<String, List<Attribute>> properties){
        System.out.println(" Set status: " + o);
        String status = "DRAFT";
        /*
           keskeneräinen       | 'INCOMPLETE'
           korvattu            | 'SUPERSEDED'
           odottaa hyväksyntää | 'SUBMITTED'
                               | 'RETIRED'
                               | 'INVALID'
           hyväksytty          | 'VALID'
                               | 'SUGGESTED'
           luonnos             | 'DRAFT'
         */
        if(o.equalsIgnoreCase("hyväksytty"))
            status = "DRAFT";

        if(stat != null && !stat.isEmpty() && stat.equalsIgnoreCase("vanhentunut"))
            status="RETIRED";
        Attribute att = new Attribute("", status);
        addProperty("status", properties, att);
        return att;
    }

    private void handleStat(String stat, Map<String, List<Attribute>> properties){
        String status = "DRAFT";
        /*
           keskeneräinen       | 'INCOMPLETE'
           korvattu            | 'SUPERSEDED'
           odottaa hyväksyntää | 'SUBMITTED'
                               | 'RETIRED'
                               | 'INVALID'
           hyväksytty          | 'VALID'
                               | 'SUGGESTED'
           luonnos             | 'DRAFT'
         */
        if(stat != null && !stat.isEmpty() && stat.equalsIgnoreCase("vanhentunut")) {
            status = "RETIRED";
            Attribute att = new Attribute("", status);
            addProperty("status", properties, att);
        }
    }

    /**
     * NTRF Broader-concept parsing
     * Can be direct hierarchical or  partitive reference
     *    <BCON href="#tmpOKSAID122" typr="generic">koulutus (2)</BCON>
     *    <BCON href="#tmpOKSAID148" typr="partitive">toimipisteen</BCON>
     * @param o
     * @param references
     */
    private void handleBCON(BCON o, Map<String, List<Identifier>>references) {
        if(logger.isDebugEnabled())
            logger.debug("handleBCON:" + o.getHref());
        String brefId = o.getHref();
        // Remove #
        if (brefId.startsWith("#"))
            brefId = o.getHref().substring(1);

        UUID refId = idMap.get(brefId);
        if (refId == null)
            refId = createdIdMap.get(brefId);

        // partitive = isPartOf = koostumussuhteinen yläkäsite
        // generic = broader = hierarkkinen yläkäsite
        List<Identifier> ref = null;
        if(o.getTypr().equalsIgnoreCase("generic")) {
            ref = references.get("broader");
        } else
            ref = references.get("isPartOf");
        if (ref == null)
            ref = new ArrayList<>();
        // Generic = broader concept, partitive = related concept
        if (refId != null) {
            ref.add(new Identifier(refId, typeMap.get("Concept").getDomain()));
            if(o.getTypr().equalsIgnoreCase("generic")) {
                references.put("broader", ref);
            } else
                references.put("isPartOf", ref);
        }
        else {
            logger.warn("BCON reference match failed. for " + brefId);
            statusList.put(currentRecord,
                    new StatusMessage(currentRecord,
                            "BCON reference match failed. for " + brefId));
        }
    }

    /**
     * NTRF Related-concept parsing
     * Can be direct hierarchical or  partitive reference
     *     <RCON href="#tmpOKSAID564">ylioppilastutkintoa</RCON>
     *     <RCON href="#tmpOKSAID436">Eurooppa-koulujen</RCON>
     *     <RCON href="#tmpOKSAID456">lukiokoulutuksen</RCON>*
     * @param o
     * @param references
     */
    private void handleRCONRef(RCON o, Map<String, List<Identifier>>references) {
        if(logger.isDebugEnabled())
            logger.debug("handleRCON ref:" + o.getHref());
        String rrefId = o.getHref();
        // Remove #
        if (rrefId.startsWith("#"))
            rrefId = o.getHref().substring(1);

        UUID refId = idMap.get(rrefId);
        if (refId == null)
            refId = createdIdMap.get(rrefId);

        System.out.println("handleRCONref id:"+rrefId+" -> " +refId);
        /* oppiminen -> abea9901-86de-3901-bb41-89b2f1184f34
                     "references": {
      "prefLabelXl": [
        {
          "id": "dede45a2-74ac-459d-be52-a22cffc52dc0",
          "type": {
            "id": "Term",
            "graph": {
              "id": "9c2782be-5f75-4cd7-80fa-806a8abfd4e2"
            }
          }
        }
      ],
      "related": [
        {
          "id": "abea9901-86de-3901-bb41-89b2f1184f34",
          "type": {
            "id": "Concept",
            "graph": {
              "id": "9c2782be-5f75-4cd7-80fa-806a8abfd4e2"
            }
          }
        }
      ]
    },
                     */
        List<Identifier> ref = null;
        ref = references.get("related");
        if (ref == null)
            ref = new ArrayList<>();
        if (refId != null) {
            ref.add(new Identifier(refId, typeMap.get("Concept").getDomain()));
            references.put("related", ref);
        }else {
            logger.warn("RCON reference match failed. for " + rrefId);
            statusList.put(currentRecord,
                    new StatusMessage(currentRecord,
                            "RCON reference match failed. for " + rrefId));
        }
        System.out.println("After add ="+ref);
    }

    private void handleRCON(RCON o, UUID relatedConceptId) {
        Map<String, List<Identifier>>references;
        if(logger.isDebugEnabled())
            logger.debug("handleRCON:" + o.getHref());
        String rrefId = o.getHref();
        if(rrefId!= null) {
            // Remove #
            if (rrefId.startsWith("#"))
                rrefId = o.getHref().substring(1);

            UUID refId = idMap.get(rrefId);
            if (refId == null)
                refId = createdIdMap.get(rrefId);

            if (refId == null) {
                // Add placeholder for and resolve it after all items are created
                System.out.println("Can't resolve RCON-reference ID for " + rrefId);
                RconRef rconRef = new RconRef();
                rconRef.setReferenceString(rrefId);
                // Null id, as a placeholder
                rconRef.setId(NULL_ID);
                rconRef.setType(o.getTypr());
                rconRef.setTargetId(relatedConceptId);
                rconList.add(rconRef);
            } else {
                RconRef rconRef = new RconRef();
                rconRef.setReferenceString(rrefId);
                rconRef.setId(refId);
                rconRef.setType(o.getTypr());
                rconRef.setTargetId(relatedConceptId);
                rconList.add(rconRef);
            }
        } else {
            logger.warn("RCON with NULL href. Cant resolve.");
            statusList.put(currentRecord,
                    new StatusMessage(currentRecord,
                            "RCON reference match failed. for " + rrefId));
        }
    }

    private void handleNCON(NCON o, UUID broaderConceptId) {
        Map<String, List<Identifier>>references;
        if(logger.isDebugEnabled())
         logger.debug("handleNCON:" + o.getHref());
        String nrefId = o.getHref();
        if(nrefId!= null) {
            // Remove #
            if (nrefId.startsWith("#"))
                nrefId = o.getHref().substring(1);

            UUID refId = idMap.get(nrefId);
            if (refId == null)
                refId = createdIdMap.get(nrefId);

            if (refId == null) {
                // Add placeholder for and resolve it after all items are created
                System.out.println("Can't resolve NCON-reference ID for " + nrefId);
                NconRef nconRef = new NconRef();
                nconRef.setReferenceString(nrefId);
                // Null id, as a placeholder
                nconRef.setId(NULL_ID);
                nconRef.setType(o.getTypr());
                nconRef.setTargetId(broaderConceptId);
                nconList.add(nconRef);
            } else {
                NconRef nconRef = new NconRef();
                nconRef.setReferenceString(nrefId);
                nconRef.setId(refId);
                nconRef.setType(o.getTypr());
                nconRef.setTargetId(broaderConceptId);
                nconList.add(nconRef);
            }
        } else {
            logger.warn("NCON with NULL href. Cant resolve.");
            statusList.put(currentRecord,
                    new StatusMessage(currentRecord,
                            "NCON reference match failed. for " + nrefId));
        }
    }

    /**
     * Set up ConceptClass with CLAS-element data.
     * @param o CLAS object containing String list
     * @param properties
     */
    private void handleCLAS(CLAS o, Map<String, List<Attribute>> properties){
        System.out.println(" Set clas: " + o.getContent().toString());
        String attValue ="";
        if(o.getContent().size()>0){
            List<String> clasList = new ArrayList<>();
            o.getContent().forEach(obj ->{
                clasList.add(obj.toString());
            });
            Attribute att = new Attribute("", clasList.toString().substring(1,clasList.toString().length()-1));
            addProperty("conceptClass", properties, att);
        } else {
            logger.warn("Empty CLAS element.");
        }
    }

    private Attribute  handleDEF( DEF def, String lang, Map<String, List<Attribute>>  parentProperties, Map<String, List<Identifier>>parentReferences, Map<String, List<Attribute>>  termProperties,  Graph vocabularity){
        if(logger.isDebugEnabled())
            logger.debug("handleDEF-part:"+def.getContent());

        String defString="";
        System.out.println("handleDEF start" + defString);

        List<?> defItems = def.getContent();
        for(Object de:defItems) {
            System.out.println("   handleDEF block:" + de.getClass().getName());
            if(de instanceof  String) {
                defString =defString.concat((String)de);
            }
            else {
                if(de instanceof  RCON){
                    // <NCON href="#tmpOKSAID122" typr="partitive">koulutuksesta (2)</NCON> ->
                    // <a href="http://uri.suomi.fi/terminology/oksa/tmpOKSAID122" data-typr="partitive">koulutuksesta (2)</a>
                    // <DEF>suomalaista <RCON href="#tmpOKSAID564">ylioppilastutkintoa</RCON> vastaava <RCON href="#tmpOKSAID436">Eurooppa-koulujen</RCON> <BCON href="#tmpOKSAID1401" typr="generic">tutkinto</BCON>, joka suoritetaan kaksivuotisen <RCON href="#tmpOKSAID456">lukiokoulutuksen</RCON> päätteeksi<SOURF>opintoluotsi + rk + tr34</SOURF></DEF>
                    RCON rc=(RCON)de;
                    String hrefid=null;
                    defString = defString.concat("<a href='"+
                            vocabularity.getUri());
                    // Remove # from uri
                    if(rc.getHref().startsWith("#")) {
                        hrefid=rc.getHref().substring(1);
                        defString = defString.concat(hrefid + "'");
                    } else
                        defString = defString.concat(rc.getHref() + "'");
                    if(rc.getTypr() != null && !rc.getTypr().isEmpty()) {
                        defString = defString.concat(" data-typr ='" +
                                rc.getTypr()+"'");
                    }

                    String hrefText ="";
                    List<Serializable> content = rc.getContent();
                    for( Serializable c:content){
                        if(c instanceof  JAXBElement){
                            JAXBElement el = (JAXBElement)c;
                            if(el.getName().toString().equalsIgnoreCase("HOGR")){
                                hrefText = hrefText+"("+el.getValue().toString()+")";
                            }
                        } else if(c instanceof String) {
                            hrefText = hrefText+c;
                        }
                    }
                    defString = defString.concat(">"+hrefText+ "</a> ");
                    if(logger.isDebugEnabled())
                        logger.debug("handleDEF RCON:" + defString);
                    // Add also reference
                    handleRCONRef(rc, parentReferences);
                }else if(de instanceof BCON){
                    //<DEF><RCON href="#tmpOKSAID162">yliopiston</RCON> <BCON href="#tmpOKSAID187" typr="partitive"
                    // >opetus- ja tutkimushenkilöstön</BCON> osa, jonka tehtävissä suunnitellaan,
                    // koordinoidaan ja johdetaan erittäin laajoja kokonaisuuksia, tehtäviin sisältyy kokonaisvaltaista
                    // vastuuta organisaation toiminnasta ja taloudesta sekä kansallisen tai kansainvälisen
                    // tason kehittämistehtävistä ja tehtävissä vaikutetaan huomattavasti koko tutkimusjärjestelmään
                    // <SOURF>neliport + tr40</SOURF></DEF>

                    BCON bc=(BCON)de;
                    defString = defString.concat("<a href='"+
                            vocabularity.getUri());
                    // Remove # from uri
                    if(bc.getHref().startsWith("#")) {
                        defString = defString.concat(bc.getHref().substring(1) + "'");
                    } else
                        defString = defString.concat(bc.getHref() + "'");
                    if(bc.getTypr() != null && !bc.getTypr().isEmpty()) {
                        defString = defString.concat(" data-typr ='" +
                                bc.getTypr()+"'");
                    }
                    String hrefText ="";
                    List<Serializable> content = bc.getContent();
                    for( Serializable c:content){
                        if(c instanceof  JAXBElement){
                            JAXBElement el = (JAXBElement)c;
                            if(el.getName().toString().equalsIgnoreCase("HOGR")){
                                hrefText = hrefText+"("+el.getValue().toString()+")";
                            }
                        } else if(c instanceof String) {
                            hrefText = hrefText+c;
                        }
                    }
                    defString = defString.concat(">"+hrefText+ "</a> ");
                    // Add also reference
                    handleBCON(bc, parentReferences);
                }else if(de instanceof NCON){
                    //<DEF><RCON href="#tmpOKSAID162">yliopiston</RCON> <BCON href="#tmpOKSAID187" typr="partitive"
                    // >opetus- ja tutkimushenkilöstön</BCON> osa, jonka tehtävissä suunnitellaan,
                    // koordinoidaan ja johdetaan erittäin laajoja kokonaisuuksia, tehtäviin sisältyy kokonaisvaltaista
                    // vastuuta organisaation toiminnasta ja taloudesta sekä kansallisen tai kansainvälisen
                    // tason kehittämistehtävistä ja tehtävissä vaikutetaan huomattavasti koko tutkimusjärjestelmään
                    // <SOURF>neliport + tr40</SOURF>
                    // <NCON href="#tmpOKSAID450" typr="generic">esiopetusta</NCON></DEF>

                    NCON nc=(NCON)de;
                    defString = defString.concat("<a href='"+
                            vocabularity.getUri());
                    // Remove # from uri
                    if(nc.getHref().startsWith("#")) {
                        defString = defString.concat(nc.getHref().substring(1) + "'");
                    } else
                        defString = defString.concat(nc.getHref() + "'");
                    if(nc.getTypr() != null && !nc.getTypr().isEmpty()) {
                        defString = defString.concat(" data-typr ='" +
                                nc.getTypr()+"'");
                    }
                    String hrefText ="";
                    List<Serializable> content = nc.getContent();
                    for( Serializable c:content){
                        if(c instanceof  JAXBElement){
                            JAXBElement el = (JAXBElement)c;
                            if(el.getName().toString().equalsIgnoreCase("HOGR")){
                                hrefText = hrefText+"("+el.getValue().toString()+")";
                            }
                        } else if(c instanceof String) {
                            hrefText = hrefText+c;
                        }
                    }
                    defString = defString.concat(">"+hrefText+ "</a> ");
                } else if (de instanceof SOURF){
                    handleSOURF((SOURF)de, lang, termProperties, vocabularity);
                    // Add  refs as sources-part.
                    updateSources(((SOURF)de).getContent(), lang, termProperties);
                }  else if (de instanceof REMK) {
                    handleREMK(lang,(REMK)de,termProperties, vocabularity);
                }else if(de instanceof JAXBElement){
                    JAXBElement elem =(JAXBElement)de;
                    if(elem.getName().toString().equalsIgnoreCase("HOGR")){
                        Attribute att = new Attribute(lang, elem.getValue().toString());
                        addProperty("termHomographNumber", termProperties,  att);
                    } else if(elem.getName().toString().equalsIgnoreCase("fi.vm.yti.terminology.api.model.ntrf.LINK")){
                        LINK li = (LINK)elem.getValue();
                        System.out.println("DEF, jaxb-Link found:"+li.getHref());
                    } else
                        System.out.println(elem.getValue().getClass().getName()+" -- DEF, unhandled JAXB:"+elem.getName().toString()+"  value:"+elem.getValue().toString());
                } else if(de instanceof LINK){
                    LINK li = (LINK)de;
                    defString = defString.concat("<a href='"+li.getHref()+"' data-type='external'>"+li.getContent().get(0)+"</a> ");
                } else {
                    System.out.println("DEF, unhandled CLASS=" + de.getClass().getName());
                    statusList.put(currentRecord,
                            new StatusMessage(currentRecord,
                                    "DEF, unhandled CLASS=" + de.getClass().getName()));
                }
            }
        }
        System.out.println("handleDEF definition:<" + defString+">");
        if(logger.isDebugEnabled())
            logger.debug("Definition="+defString);
        // Add definition if exist.
        if(!defString.isEmpty()) {
            Attribute att = new Attribute(lang, defString);
            addProperty("definition", parentProperties, att);
            return att;
        } else
            return null;
    }

    private Attribute  handleNOTE( NOTE note, String lang, Map<String, List<Attribute>>  parentProperties, Map<String, List<Identifier>> parentReferences, Map<String, List<Attribute>>  termProperties, Graph vocabularity){
        if(logger.isDebugEnabled())
            logger.debug("handleNOTE-part"+note.getContent());

        String noteString="";
        for(Object de:note.getContent()) {
            System.out.println("Add NOTE:"+de.getClass().getName());
            if(de instanceof  String) {
                if(logger.isDebugEnabled())
                    logger.debug("  Parsing note-string:" + de.toString());
                noteString = noteString.concat(de.toString());
            } else if(de instanceof SOURF){
                if(((SOURF)de).getContent()!= null && ((SOURF)de).getContent().size() >0) {
                    handleSOURF((SOURF)de, lang, termProperties,vocabularity);
                    // Add  refs as string and  construct lines four sources-part.
                    updateSources(((SOURF)de).getContent(), lang, termProperties);
                }
            } else if(de instanceof RCON){
                RCON rc = (RCON)de;
                if(rc.getContent()!= null && rc.getContent().size() >0) {
                    System.out.println(" ADD NOTE RCON:"+rc.getHref());
                }
            } else if(de instanceof LINK){
                LINK lc = (LINK)de;
                if(lc.getContent()!= null && lc.getContent().size() >0) {
                    System.out.println(" ADD NOTE LINK:"+lc.getHref());
                    // Remove  "href:" from string "href:https://www.finlex.fi/fi/laki/ajantasa/1973/19730036"
                    String url=lc.getHref().substring(5);
                    noteString = noteString.concat("<a href='"+url+"' data-type='external'>"+lc.getContent().get(0)+"</a> ");
                    System.out.println("Add LINK:"+url);
                }
            } else if(de instanceof JAXBElement){
                JAXBElement j = (JAXBElement)de;
                if(logger.isDebugEnabled())
                    logger.debug("  Parsing note-elem:" + j.getName()+"");
                if(j.getName().toString().equalsIgnoreCase("RCON")){
                    RCON rc=(RCON)j.getValue();
                    noteString = noteString.concat("<a href='"+
                            vocabularity.getUri());
                    // Remove # from uri
                    if(rc.getHref().startsWith("#")) {
                        noteString = noteString.concat(rc.getHref().substring(1) + "'");
                    } else
                        noteString = noteString.concat(rc.getHref() + "'");
                    if(rc.getTypr() != null && !rc.getTypr().isEmpty()) {
                        noteString = noteString.concat(" data-typr ='" +
                                rc.getTypr()+"'");
                    }
                    noteString = noteString.concat(">"+rc.getContent().get(0)+ "</a> ");
                    handleRCONRef(rc, parentReferences);
                }
                else if(j.getName().toString().equalsIgnoreCase("SOURF")) {
                    SOURF sf = (SOURF)j.getValue();
                    if(sf.getContent()!= null && sf.getContent().size() >0) {
                        noteString.concat(sf.getContent().toString());
                        // Add  refs as string and  construct lines for sources-part.
                        updateSources(sf.getContent(), lang, termProperties);
                    }
                } else if(j.getName().toString().equalsIgnoreCase("LINK")){
                    // External link
                    LINK l=(LINK)j.getValue();
                    // Remove  "href:" from string "href:https://www.finlex.fi/fi/laki/ajantasa/1973/19730036"
                    String url=l.getHref().substring(5);
                    noteString = noteString.concat("<a href='"+url+"' data-type='external'>"+l.getContent().get(0)+"</a> ");
                    System.out.println("Add LINK:"+url);
                } else if(j.getName().toString().equalsIgnoreCase("HOGR")){
                    noteString = noteString + "("+j.getValue().toString()+")";
                } else if(j.getName().toString().equalsIgnoreCase("B") || j.getName().toString().equalsIgnoreCase("I")){
                    // Remove Bold and Italics
                    noteString = noteString + j.getValue().toString();
                }else if(j.getName().toString().equalsIgnoreCase("BR") ){
                    // Add newline
                    noteString = noteString + "\n";
                }else {
                    System.out.println("  Unhandled note-class " + j.getName().toString());
                    statusList.put(currentRecord,
                            new StatusMessage(currentRecord,
                                    "Unhandled note-class " + j.getName().toString()));
                }
            }
            if(logger.isDebugEnabled())
                logger.debug("note-String="+noteString);
        };

        // Add note if exist.
        if(!noteString.isEmpty()) {
            Attribute att = new Attribute(lang, noteString);
            addProperty("note", parentProperties, att);
            return att;
        } else
            return null;
    }
    /**
     * Sample of incoming synonyms
     *       <SY>
     *         <TERM>examensarbete<GRAM gend="n"></GRAM></TERM>
     *         <SCOPE>akademisk</SCOPE>
     *         <SOURF>fisv_utbild_ordlista + kielityoryhma_sv</SOURF>
     *       </SY>
     *       <SY>
     *         <EQUI value="near-equivalent"></EQUI>
     *         <TERM>vetenskapligt arbete<GRAM gend="n"></GRAM></TERM>
     *         <SCOPE>akademisk</SCOPE>
     *         <SOURF>fisv_utbild_ordlista + kielityoryhma_sv</SOURF>
     *       </SY>
     */

    private GenericNode handleSY(Termcontent synonym, String lang, Map<String, List<Attribute>>  parentProperties, Map<String, List<Identifier>> parentReferences, Graph vocabularity){
        if(logger.isDebugEnabled())
            logger.debug("handleSY-part:"+synonym.toString());
        //Synonym fields
        String equi= "";
        String hogr = "";
        String term ="";
        String sourf = "";
        String scope = "";
        // Attributes  are stored to property-list
        Map<String, List<Attribute>> properties = new HashMap<>();
        synonym.getEQUI();
        if(synonym.getEQUI() != null){
            // Attribute string value = broader | narrower | near-equivalent
            EQUI eqt = synonym.getEQUI();
            equi = eqt.getValue();
            String eqvalue ="=";
            if(equi.equalsIgnoreCase("broader"))
                eqvalue=">";
            if(equi.equalsIgnoreCase("narrower"))
                eqvalue="<";
            if(equi.equalsIgnoreCase("near-equivalent"))
                eqvalue="~";

            Attribute att = new Attribute(lang, eqvalue);
            addProperty("termEquivalency", properties,  att);
        }
        if(synonym.getHOGR() != null){
            Attribute att = new Attribute(lang, synonym.getHOGR());
            addProperty("termHomographNumber", properties,  att);
        }
        if(synonym.getSCOPE() != null){
            SCOPE sc = synonym.getSCOPE();
            sc.getContent().forEach(o ->{
                if(o instanceof  String){
                    Attribute att = new Attribute(lang, o.toString());
                    addProperty("scope", properties,  att);
                } else {
                    System.out.println("SCOPE unknown instance type:"+o.getClass().getName());
                    statusList.put(currentRecord,
                            new StatusMessage(currentRecord,
                                    "SCOPE unknown instance type:"+o.getClass().getName()));
                }
            });
        }
        if(synonym.getSOURF() != null){
            handleSOURF(synonym.getSOURF(),lang,properties,vocabularity);
        }
        if(synonym.getTERM() != null){
            handleTERM(synonym.getTERM(),lang,properties);
        }
        // create new synonym node (Term)
        TypeId typeId = typeMap.get("Term").getDomain();
        // Uri is  parent-uri/term-'code'
        GenericNode node = null;
        UUID id = UUID.randomUUID();
        String code = id.toString();

        node = new GenericNode(id, code, vocabularity.getUri() + "term-" + code, 0L, "", new Date(), "", new Date(), typeId, properties, emptyMap(), emptyMap());
        // Add id for reference resolving
        createdIdMap.put(node.getCode(),node.getId());
        return node;
    }

    /**
     *  From
     * @param source
     * @param lang
     * @param properties
     * @param vocabularity
     * @return
     */
    private Attribute  handleSOURF(SOURF source, String lang, Map<String, List<Attribute>>  properties, Graph vocabularity){
        if(logger.isDebugEnabled())
            logger.debug("handleSOURF-part"+source.getContent());

        String sourceString="";

        List<?> sourceItems = source.getContent();
        for(Object se:sourceItems) {
            if(se instanceof  String) {
                sourceString =sourceString.concat(se.toString());
            } else if(se instanceof NCON){
                System.out.println("SOURF-NCON");
                NCON rc=(NCON)se;
                sourceString = sourceString.concat("<a href='"+
                        vocabularity.getUri());
                if(rc.getTypr() != null && !rc.getTypr().isEmpty()) {
                    sourceString = sourceString.concat(" data-typr ='" +
                            rc.getTypr()+"'");
                }
                sourceString = sourceString.concat(">"+rc.getContent().toString()+ "</a>");
            } else if(se instanceof BCON){
                System.out.println("SOURF-BCON");
            } else if(se instanceof RCON){
                System.out.println("SOURF-RCON");
                RCON rc=(RCON)se;
                sourceString = sourceString.concat("<a href='"+
                        vocabularity.getUri());
                if(rc.getTypr() != null && !rc.getTypr().isEmpty()) {
                    sourceString = sourceString.concat(" data-typr ='" +
                            rc.getTypr()+"'");
                }
                sourceString = sourceString.concat(">"+rc.getContent().toString()+ "</a>");
            }
            else {
                if(se instanceof JAXBElement){
                    JAXBElement j = (JAXBElement)se;
                    System.out.println("  SOURF-elem <" + j.getName()+">");
                    if(j.getName().toString().equalsIgnoreCase("SOURF")){
                        RCON rc=(RCON)j.getValue();
                        sourceString = sourceString.concat("<a href='"+
                                vocabularity.getUri());
                        if(rc.getTypr() != null && !rc.getTypr().isEmpty()) {
                            sourceString = sourceString.concat(" data-typr ='" +
                                    rc.getTypr()+"'");
                        }
                        sourceString = sourceString.concat(">"+rc.getContent().get(0)+ "</a>");
                    }
                    else if(j.getName().toString().equalsIgnoreCase("SOURF")) {
                        SOURF sf = (SOURF)j.getValue();
                        if(sf.getContent()!= null && sf.getContent().size() >0) {
                            sourceString = sourceString.concat(" "+sf.getContent());
                            // Add  refs as string and  construct lines four sources-part.
                            updateSources(sf.getContent(), lang, properties);
                        }
                    }  else {
                        System.out.println("  UNKNOWN  SOURF-class" + se.getClass().getName());
                        statusList.put(currentRecord,
                                new StatusMessage(currentRecord,
                                        "SOURF unknown instance type:"+se.getClass().getName()));                    }
                }
            }
        };
        // Add definition if exist.

        if(!sourceString.isEmpty()) {
            Attribute att = new Attribute(lang, sourceString);
            addProperty("source", properties, att);
            return att;
        } else
            return null;
    }

    /**
     * Add individual source-elements to the source-list for each individual reference  enumerated inside imported SOURF
     * @param srefs
     * @param lang
     * @param properties
     */
    private void updateSources(List<Object> srefs, String lang,  Map<String, List<Attribute>> properties){
        for(Object o:srefs) {
            updateSources(o.toString(),lang, properties);
        };
    }

    /**
     * Add individual source-elements from give string
     * @param srefs
     * @param lang
     * @param properties
     */
    private void updateSources(String srefs, String lang,  Map<String, List<Attribute>> properties){
        String fields[] = srefs.split("\\+");
        for (String s : fields) {
            s = s.trim();
            String sourcesString="["+s+"]";
            Map<String, String> m = referenceMap.get(s);
            if (m != null) {
                if (m.get("text") != null && !m.get("text").isEmpty()) {
                    sourcesString = sourcesString.concat("\n "+m.get("text")+"\n");
                }
                if (m.get("url") != null && !m.get("url").isEmpty()) {
                    sourcesString = sourcesString.concat( m.get("url"));
                }
            } else {
                logger.warn("Not matching reference found for:"+s);
                statusList.put(currentRecord,new StatusMessage(currentRecord,"Not matching reference found for :"+s));
            }
            if(!sourcesString.isEmpty()) {
                if(logger.isDebugEnabled())
                    logger.debug("ADDING sourf:"+sourcesString);
                Attribute satt = new Attribute(lang, sourcesString);
                addProperty("source", properties, satt);
            }
        }
    }

    /**
     * Add individual named attribute to property list
     * @param attributeName like prefLabel
     * @param properties  Propertylist where attribute is added
     * @param att Attribute to be added
     */
    private void addProperty(String attributeName, Map<String, List<Attribute>> properties, Attribute att){
        if (!properties.containsKey(attributeName)) {
            List<Attribute> a = new ArrayList<>();
            a.add(att);
            properties.put(attributeName, a);
        } else
            properties.get(attributeName).add(att);
    }

    private static <K, V> Map<K, List<V>> mapMapValues(Map<K, List<V>> map, Function<V, V> mapper) {
        return map.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> mapToList(e.getValue(), mapper)));
    }

    private static boolean isUUID(String s) {
        return UUID_PATTERN.matcher(s).matches();
    }

    private class NconRef {
        String referenceString;
        String type;
        UUID id;
        UUID targetId;

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

    private class RconRef {
        String referenceString;
        String type;
        UUID id;
        UUID targetId;

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

    private class StatusMessage{
        String record;
        List<String> message = new ArrayList<>();

        public StatusMessage(String record, String msg) {
            this.record = record;
            this.message.add(msg);
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

        public void putMessage(String msg){
            this.message.add(msg);
        }
    }
}
