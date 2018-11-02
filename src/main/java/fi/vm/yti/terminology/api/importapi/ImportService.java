package fi.vm.yti.terminology.api.importapi;

import fi.vm.yti.mq.service.YtiMQService;
import fi.vm.yti.security.AuthenticatedUserProvider;
import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.frontend.FrontendGroupManagementService;
import fi.vm.yti.terminology.api.frontend.FrontendTermedService;
import fi.vm.yti.terminology.api.model.ntrf.VOCABULARY;
import fi.vm.yti.terminology.api.model.termed.Graph;
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

    private final TermedRequester termedRequester;
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
    public ImportService(TermedRequester termedRequester,
                         FrontendGroupManagementService groupManagementService,
                         FrontendTermedService frontendTermedService,
                         AuthenticatedUserProvider userProvider,
                         AuthorizationManager authorizationManager,
                         YtiMQService ytiMQService,
                         JmsMessagingTemplate jmsMessagingTemplate,
                         @Value("${mq.active.subsystem}") String subSystem) {
        this.termedRequester = termedRequester;
        this.groupManagementService = groupManagementService;
        this.termedService = frontendTermedService;
        this.userProvider = userProvider;
        this.authorizationManager = authorizationManager;
        this.subSystem = subSystem;
        this.ytiMQService = ytiMQService;
        this.jmsMessagingTemplate = jmsMessagingTemplate;
    }

    ResponseEntity getStatus(UUID jobtoken, boolean full){
        // Status not_found/running/errors
        // Query status information from ActiveMQ
        HttpStatus status;
        StringBuffer statusString= new StringBuffer();
        System.out.println(" ImportService.getStatus Status_full="+full);
        ImportStatusResponse response = new ImportStatusResponse();

        if(full){
            status = ytiMQService.getStatus(jobtoken, statusString);
            System.out.println("Status="+status + " StatusString="+statusString);
            response = ImportStatusResponse.fromString(statusString.toString());
        } else
            status = ytiMQService.getStatus(jobtoken);
        System.out.println("Status class="+status.getClass().getName());
        // Construct  response
        if(status == HttpStatus.OK){
            response.setStatus("Ready");
            if(full)
                response = ImportStatusResponse.fromString(statusString.toString());
        } else if(status == HttpStatus.NOT_ACCEPTABLE){
                response.setStatus("Import operation already started");
        } else if (status ==  HttpStatus.PROCESSING){
                response.setStatus("Processing");
                if(full)
                    response = ImportStatusResponse.fromString(statusString.toString());
                System.out.println(" Processing StatusString="+statusString);
        } else {
                response.setStatus("Not found");
        }
        System.out.println("Response status json");
        // Construct return message
        JsonUtils.prettyPrintJson(response);

        return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(response), HttpStatus.OK);
    }


    int phase=0;
    ResponseEntity setStatus(UUID jobtoken){
        System.out.println("SetStatus for "+jobtoken);
        ImportStatusResponse response = new ImportStatusResponse();
        ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, jobtoken.toString(),userProvider.getUser().getId().toString(),"http://yti.dev.fi/test", "phase"+(phase++));
        // Query status information from ActiveMQ
        HttpStatus status = ytiMQService.getStatus(jobtoken);
        // Construct return message
        if (status == HttpStatus.NOT_ACCEPTABLE){
            response.setStatus("Import operation already started");
            return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(response), HttpStatus.NOT_ACCEPTABLE);
        } else if (status == HttpStatus.PROCESSING){
            response.setStatus("Processing");
            response.setProgress(10*phase+"/100");
            return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(response), HttpStatus.OK);
        } else if(status == HttpStatus.OK){
            response.setStatus("Ready");
            return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(response), HttpStatus.OK);
        }
        response.setStatus("Not found");
        return new ResponseEntity<>(JsonUtils.prettyPrintJsonAsString(response), HttpStatus.OK);
    }


    ResponseEntity checkIfImportIsRunning(String uri){
        System.out.println("CheckIfRunning");
        boolean status = ytiMQService.checkIfImportIsRunning(uri);
        System.out.println("CheckIfRunning - "+status);
        if(status)
            return new ResponseEntity<>("{\"status\":\"Running\"}", HttpStatus.OK);
        return new ResponseEntity<>("{\"status\":\"Stopped\"}", HttpStatus.OK);
    }

    ResponseEntity handleNtrfDocumentAsync(String format, UUID vocabularyId, MultipartFile file) {
        String rv;
        System.out.println("Incoming vocabularity= "+vocabularyId+" - file:"+file.getName()+" size:"+file.getSize()+ " type="+file.getContentType());
        // Fail if given format string is not ntrf
        if (!format.equals("ntrf")) {
            logger.error("Unsupported format:<" + format + "> (Currently supported formats: ntrf)");
            // Unsupported format
            ResponseEntity<?> re = new ResponseEntity<>("Unsupported format:<" + format + ">    (Currently supported formats: ntrf)\n", HttpStatus.NOT_ACCEPTABLE);
            return new ResponseEntity<>("Unsupported format:<" + format + ">    (Currently supported formats: ntrf)\n", HttpStatus.NOT_ACCEPTABLE);
        }

        Graph vocabulary = null;

        // Get vocabularity
        try {
            vocabulary = termedService.getGraph(vocabularyId);
            // Import running for given vocabulary, drop it
            if(ytiMQService.checkIfImportIsRunning(vocabulary.getUri())){
                logger.error("Import running for Vocabulary:<" + vocabularyId + ">");
                return new ResponseEntity<>("Import running for Vocabulary:<" + vocabularyId+">", HttpStatus.CONFLICT);
            }
        } catch ( NullPointerException nex){
            // Vocabularity not found
            logger.error("Vocabulary:<" + vocabularyId + "> not found");
            return new ResponseEntity<>("Vocabulary:<" + vocabularyId + "> not found\n", HttpStatus.NOT_FOUND);
        }

        UUID operationId=UUID.randomUUID();
        if(!file.getContentType().equalsIgnoreCase("text/xml")){
            rv = "{\"operation\":\""+operationId+"\", \"error\":\"incoming file type  is wrong\"}";
            return new ResponseEntity<>( rv, HttpStatus.BAD_REQUEST);
        }
        rv = "{\"jobtoken\":\"" + operationId + "\"}";
        // Handle incoming xml
        try {
            ytiMQService.setStatus(YtiMQService.STATUS_PREPROCESSING, operationId.toString(), userProvider.getUser().getId().toString(), vocabulary.getUri(),"Validating");
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
            response.setStatus("Processing "+l.size()+" items validated");
            response.setStatistics("Total:0 warnings:0");
            ytiMQService.setStatus(YtiMQService.STATUS_PROCESSING, operationId.toString(), userProvider.getUser().getId().toString(), vocabulary.getUri(),response.toString());
            StringWriter sw = new StringWriter();
            marshaller.marshal(voc, sw);
            // Add application specific headers
            MessageHeaderAccessor accessor = new MessageHeaderAccessor();
            accessor.setHeader("vocabularyId",vocabularyId.toString());
            accessor.setHeader("format","NTRF");
            int stat = ytiMQService.handleImportAsync(operationId, accessor, subSystem, vocabulary.getUri(), sw.toString());
            if(stat != HttpStatus.OK.value()){
                System.out.println("Import failed code:"+stat);
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
