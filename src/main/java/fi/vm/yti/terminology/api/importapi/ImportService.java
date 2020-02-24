package fi.vm.yti.terminology.api.importapi;

import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.mq.service.YtiMQService;
import fi.vm.yti.terminology.api.frontend.FrontendGroupManagementService;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.importapi.ImportStatusResponse.Status;
import fi.vm.yti.terminology.api.model.ntrf.VOCABULARY;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.security.AuthorizationManager;
import fi.vm.yti.terminology.api.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@EnableJms
public class ImportService {

    public static final class ImportResponse {
        private String jobtoken;

        public ImportResponse(final String jobtoken) {
            this.jobtoken = jobtoken;
        }

        public String getJobtoken() {
            return jobtoken;
        }

        public void setJobtoken(final String jobtoken) {
            this.jobtoken = jobtoken;
        }

        @Override
        public String toString() {
            return "{\"jobtoken\":\"" + jobtoken + "\"}";
        }
    }

    private final FrontendGroupManagementService groupManagementService;
    private final FrontendTermedService termedService;
    private final AuthenticatedUserProvider userProvider;
    private final AuthorizationManager authorizationManager;
    private final YtiMQService ytiMQService;

    // JMS-client
    private JmsMessagingTemplate jmsMessagingTemplate;
    /**
     * Map containing metadata types. used  when creating nodes.
     */
    private HashMap<String,MetaNode> typeMap = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);
    private final String subSystem;


    @Autowired
    public ImportService(FrontendGroupManagementService groupManagementService,
                         FrontendTermedService frontendTermedService,
                         AuthenticatedUserProvider userProvider,
                         AuthorizationManager authorizationManager,
                         YtiMQService ytiMQService,
                         JmsMessagingTemplate jmsMessagingTemplate,
                         @Value("${mq.active.subsystem}") String subSystem) {
        this.groupManagementService = groupManagementService;
        this.termedService = frontendTermedService;
        this.userProvider = userProvider;
        this.authorizationManager = authorizationManager;
        this.subSystem = subSystem;
        this.ytiMQService = ytiMQService;
        this.jmsMessagingTemplate = jmsMessagingTemplate;
    }

    ResponseEntity<String> getStatus(UUID jobtoken, boolean full){
        // Status not_found/running/errors
        // Query status information from ActiveMQ
        HttpStatus status;
        StringBuffer statusString= new StringBuffer();
        System.out.println(" ImportService.getStatus Status_full="+full);
        ImportStatusResponse response = new ImportStatusResponse();

        // Get always full state
        status = ytiMQService.getStatus(jobtoken, statusString);

        // Construct  response
        if(status == HttpStatus.OK){
            response = ImportStatusResponse.fromString(statusString.toString());
            if(!full){
                // Remove status messages if not needed
                response.getStatusMessage().clear();
            }
            response.setStatus(ImportStatusResponse.Status.SUCCESS);
        } else if(status == HttpStatus.NOT_ACCEPTABLE){
                response.setStatus(Status.FAILURE);
                response.getStatusMessage().clear();
                response.addStatusMessage( new ImportStatusMessage("Vocabulary","Import operation already started"));
        } else if (status ==  HttpStatus.PROCESSING){
            response = ImportStatusResponse.fromString(statusString.toString());
            if(!full){
                // Remove status messages if not needed
                response.getStatusMessage().clear();
            }
            response.setStatus(ImportStatusResponse.Status.PROCESSING);
        } else {
                response.setStatus(ImportStatusResponse.Status.NOT_FOUND);
        }
        System.out.println("Response status json");
        // Construct return message
        JsonUtils.prettyPrintJson(response);

        return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(response), HttpStatus.OK);
    }

    ResponseEntity<String> checkIfImportIsRunning(String uri){
        System.out.println("CheckIfRunning");
        boolean status = ytiMQService.checkIfImportIsRunning(uri);
        System.out.println("CheckIfRunning - "+status);
        if(status)
            return new ResponseEntity<>("{\"status\":\"Running\"}", HttpStatus.OK);
        return new ResponseEntity<>("{\"status\":\"Stopped\"}", HttpStatus.OK);
    }

    ResponseEntity<String> handleNtrfDocumentAsync(String format, UUID terminologyId, MultipartFile file) {
        String rv;
        System.out.println("Incoming terminology= "+terminologyId+" - file:"+file.getName()+" size:"+file.getSize()+ " type="+file.getContentType());
        // Fail if given format string is not ntrf
        if (!format.equals("ntrf")) {
            logger.error("Unsupported format:<" + format + "> (Currently supported formats: ntrf)");
            // Unsupported format
            return new ResponseEntity<>("Unsupported format:<" + format + ">    (Currently supported formats: ntrf)\n", HttpStatus.NOT_ACCEPTABLE);
        }

        GenericNode terminology = null;

        // Get vocabulary        
        try {
            terminology = termedService.getNode(terminologyId);
            // Import running for given terminology, drop it
            if(ytiMQService.checkIfImportIsRunning(terminology.getUri())){
                logger.error("Import running for Vocabulary:<" + terminologyId + ">");
                return new ResponseEntity<>("Import running for Vocabulary:<" + terminologyId+">", HttpStatus.CONFLICT);
            }
        } catch ( NullPointerException nex){
            logger.error("Terminology:<" + terminologyId + "> not found");
            return new ResponseEntity<>("Vocabulary:<" + terminologyId + "> not found\n", HttpStatus.NOT_FOUND);
        }

        UUID operationId=UUID.randomUUID();
        if(!file.getContentType().equalsIgnoreCase("text/xml")){
            rv = "{\"operation\":\""+operationId+"\", \"error\":\"incoming file type  is wrong\"}";
            return new ResponseEntity<>( rv, HttpStatus.BAD_REQUEST);
        }
        rv = new ImportResponse(operationId.toString()).toString();
        // Handle incoming xml
        try {
            // Just force to use test user
            String userId =  userProvider.getUser().getId()== null ? "00000000-0000-0000-0000-000000000000" : userProvider.getUser().getId().toString();
            System.out.println("userProvider"+userProvider.getUser()+ "id ="+userId);            
            ytiMQService.setStatus(YtiMQService.STATUS_PREPROCESSING, operationId.toString(), userId , terminology.getUri(),"Validating");
            JAXBContext jc = JAXBContext.newInstance(VOCABULARY.class);
            // Disable DOCTYPE-directive from incoming file.
            XMLInputFactory xif = XMLInputFactory.newFactory();
            xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader xsr = xif.createXMLStreamReader(file.getInputStream());
            Unmarshaller unmarshaller = jc.createUnmarshaller();
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            // At last, resolve ntrf-POJO's
            VOCABULARY voc = (VOCABULARY) unmarshaller.unmarshal(xsr);

            // All set up, execute actual import
            List<?> l = voc.getRECORDAndHEADAndDIAG();
            System.out.println("Incoming objects count=" + l.size());
            ImportStatusResponse response = new ImportStatusResponse();
            response.setStatus(Status.PREPROCESSING);
            response.addStatusMessage(new ImportStatusMessage("Terminology ",l.size()+" items validated"));
            response.setProcessingTotal(l.size());
            ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, operationId.toString(), userId, terminology.getUri(),response.toString());
            StringWriter sw = new StringWriter();
            marshaller.marshal(voc, sw);
            // Add application specific headers
            MessageHeaderAccessor accessor = new MessageHeaderAccessor();
            accessor.setHeader("vocabularyId",terminologyId.toString());
            accessor.setHeader("format","NTRF");
            int stat = ytiMQService.handleImportAsync(operationId, accessor, subSystem, terminology.getUri(), sw.toString());
            if(stat != HttpStatus.OK.value()){
                logger.error("Import failed code:"+stat);
            }
        } catch (IOException ioe){
            System.out.println("Incoming transform error=" + ioe);
        } catch (XMLStreamException se) {
            System.out.println("Incoming transform error=" + se);
        } catch(JAXBException je){
            System.out.println("Incoming transform error=" + je);
        }
        return new ResponseEntity<>( rv, HttpStatus.OK);
    }

    @PreDestroy
    public void onDestroy() throws Exception {
        System.out.println("Spring Container is destroyed!");
    }
}
