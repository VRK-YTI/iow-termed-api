package fi.vm.yti.terminology.api.importapi;

import fi.vm.yti.security.AuthenticatedUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Service;


import javax.jms.*;

import java.util.*;

@Service
@EnableJms
public class YtiMQService {

    private final AuthenticatedUserProvider userProvider;

    private final String subSystem;

    private static final Logger logger = LoggerFactory.getLogger(YtiMQService.class);

    public static final String[] SET_VALUES = new String[] { "Vocabulary", "Test", "CodeList" };
    public static final Set<String> SUPPORTED_SYSTEMS = new HashSet<>(Arrays.asList(SET_VALUES));

    // JMS-client
    private JmsMessagingTemplate jmsMessagingTemplate;

    private YtiMQListener listener;

    // Define public status values
    public final static int STATUS_PREPROCESSING = 1;
    public final static int STATUS_PROCESSING = 2;
    public final static int STATUS_READY = 3;

    private Map<String,Message> currentStatus = new HashMap<>();

    private JmsTemplate jmsTemplate;

    @Autowired
    public YtiMQService(AuthenticatedUserProvider userProvider,
                        JmsMessagingTemplate jmsMessagingTemplate,
                        JmsTemplate jmsTemplate,
                        YtiMQListener listener,
                        @Value("${mq.active.subsystem}") String subSystem) {
        this.userProvider = userProvider;
        this.jmsMessagingTemplate = jmsMessagingTemplate;
        this.jmsTemplate = jmsTemplate;
        this.subSystem = subSystem;
        this.listener=listener;
    }

    public HttpStatus getStatus(UUID jobtoken){

        System.out.println("IncomingQueue!");
        browseQueue(subSystem+"Incoming");
        System.out.println("StatusQueue!");
        browseQueue(subSystem+"Status");
        System.out.println("ProcessingQueue!");
        browseQueue(subSystem+"Processing");
        System.out.println("ReadyQueue!");
        browseQueue(subSystem+"Ready");
        // Status not_found/running/errors
        Message mess = currentStatus.get(jobtoken.toString());
        if(mess!=null) {
            int status=(int)mess.getHeaders().get("status");
            switch(status){
                case YtiMQService.STATUS_READY:{
                    System.out.println("Ready found:");
                    if(logger.isDebugEnabled())
                        logger.debug("Import done for "+jobtoken);
                    return HttpStatus.OK;
                }
                case YtiMQService.STATUS_PROCESSING:{
                    if(logger.isDebugEnabled())
                        logger.debug("Processing "+jobtoken);
                    long expirationtime=System.currentTimeMillis() - (long)mess.getHeaders().get("timestamp");
                    if( expirationtime > 60 * 1000) {
                        return HttpStatus.OK;
                    } else
                        return HttpStatus.PROCESSING;
                }
                case YtiMQService.STATUS_PREPROCESSING:{
                    logger.warn("Import operation already started for "+jobtoken);
                    return  HttpStatus.NOT_ACCEPTABLE;
                }
            }
        }
        else
            System.out.println("CurrentStatus = null");
        // Query status information from ActiveMQ
        if(getJobState(jobtoken, subSystem+"Ready")){
            System.out.println("Ready found:");
            if(logger.isDebugEnabled())
                logger.debug("Import done for "+jobtoken);
            return HttpStatus.OK;
        } else if (getJobState(jobtoken, subSystem+"Incoming")){
            logger.warn("Import operation already started for "+jobtoken);
            return  HttpStatus.NOT_ACCEPTABLE;
        } else if (getJobState(jobtoken, subSystem+"Status")){
            if(logger.isDebugEnabled())
                logger.debug("Processing "+jobtoken);
            return HttpStatus.PROCESSING;
        } else if (getJobState(jobtoken, subSystem+"Processing")){
            logger.debug("Processing "+jobtoken);
            return HttpStatus.PROCESSING;
        }
        return  HttpStatus.NO_CONTENT;
    }

    public HttpStatus getStatus(UUID jobtoken, StringBuffer payload){
        // Status not_found/running/errors
        // Query status information from ActiveMQ
        browseQueue("VocabularyStatus");
        Message mess = currentStatus.get(jobtoken.toString());
        if(mess!=null) {
            // return also payload
            payload.append(mess.getPayload());
            int status=(int)mess.getHeaders().get("status");
            switch(status){
                case YtiMQService.STATUS_READY:{
                    System.out.println("Ready found:");
                    if(logger.isDebugEnabled())
                        logger.debug("Import done for "+jobtoken);
                    return HttpStatus.OK;
                }
                case YtiMQService.STATUS_PROCESSING:{
                    if(logger.isDebugEnabled())
                        logger.debug("Processing "+jobtoken);
                    System.out.println("Timestamp="+(long)mess.getHeaders().get("timestamp"));
                    long expirationtime=System.currentTimeMillis() - (long)mess.getHeaders().get("timestamp");
                    System.out.println("current_time-stamp="+expirationtime);
                    if( expirationtime > 60 * 1000) {
                        return HttpStatus.OK;
                    } else
                        return HttpStatus.PROCESSING;
                }
                case YtiMQService.STATUS_PREPROCESSING:{
                    logger.warn("Import operation already started for "+jobtoken);
                    return  HttpStatus.NOT_ACCEPTABLE;
                }
            }
        }

         if(getJobState(jobtoken, subSystem+"Ready")){
            System.out.println("Ready found:");
            if(logger.isDebugEnabled())
                logger.debug("Import done for "+jobtoken);
            payload.append(getJobPayload(jobtoken,subSystem+"Ready"));
            System.out.println(" Payload="+payload);
            return HttpStatus.OK;
        } else if (getJobState(jobtoken, subSystem+"Incoming")){
            logger.warn("Import operation already started for "+jobtoken);
            return  HttpStatus.NOT_ACCEPTABLE;
        } else if (getJobState(jobtoken, subSystem+"Status")){
            if(logger.isDebugEnabled())
                logger.debug("Processing "+jobtoken);
            payload.append( getJobPayload(jobtoken,subSystem+"Status"));
            System.out.println(" Status Payload="+payload);
            return HttpStatus.PROCESSING;
        } else if (getJobState(jobtoken, subSystem+"Processing")){
            logger.debug("Processing "+jobtoken);
            payload.append(getJobPayload(jobtoken,subSystem+"Processing"));
            System.out.println(" processing Payload="+payload);
            return HttpStatus.PROCESSING;
        }
        return  HttpStatus.NO_CONTENT;
    }

    private boolean getJobState(UUID jobtoken,String queueName) {
        return
//         jmsMessagingTemplate.getJmsTemplate().browseSelected(queueName, "jobtoken='"+jobtoken.toString()+"'",new BrowserCallback<Boolean>() {
            jmsTemplate.browseSelected(queueName, "jobtoken='"+jobtoken.toString()+"'",new BrowserCallback<Boolean>() {
                @Override
            public Boolean doInJms(Session session, QueueBrowser browser) throws JMSException {
                Enumeration messages = browser.getEnumeration();
                return  messages.hasMoreElements();
            }
        });
    }


    public boolean checkIfImportIsRunning(String uri) {
        Boolean rv = false;
        // Check cached status first running if not ready
        Message mess = currentStatus.get(uri);
        if(mess!=null){
            int status = (int)mess.getHeaders().get("status");
            System.out.println("YtiMQService checkIfImportIsRunning using cached state:"+status+"\n"+mess);
            if(status == YtiMQService.STATUS_PROCESSING || status == YtiMQService.STATUS_PREPROCESSING) {
                System.out.println("Timestamp="+(long)mess.getHeaders().get("timestamp"));
                long expirationtime=System.currentTimeMillis() - (long)mess.getHeaders().get("timestamp");
                System.out.println("current_time-stamp="+expirationtime);
                rv =true;
                if( expirationtime > 60 * 1000) {
                    logger.info("Status Expired for job:"+(String)mess.getHeaders().get("jobtoken"));
                    // Hardcoded expiration time 60 sec
//                    rv = true;
                    // cached item expired, clean it
                    currentStatus.remove(uri);
                    currentStatus.remove((String)mess.getHeaders().get("jobtoken"));
                    rv = false;
                }
            }
            // Cache found, use it
            return rv;
        }
        else
            System.out.println("Not cached item found for "+uri);
        // Check queues
        if (checkUriStatus(uri, subSystem+"Processing")) {
                rv = true;
        } //else if (checkUriStatus(uri, subSystem+"Status")) {
//            rv = true;
//        } else
        return rv;
    }

    public boolean checkUriStatus(String uri, String queueName) {
        // selector uri='url' AND
//        return jmsMessagingTemplate.getJmsTemplate().browseSelected(queueName, "uri='"+uri+"'",new BrowserCallback<Boolean>() {
        return jmsTemplate.browseSelected(queueName, "uri='"+uri+"'",new BrowserCallback<Boolean>() {
                @Override
            public Boolean doInJms(Session session, QueueBrowser browser) throws JMSException {
                Enumeration messages = browser.getEnumeration();
                System.out.println("checkUriStatus "+uri+" Queue:"+queueName+" status="+messages.hasMoreElements());
                return  messages.hasMoreElements();
            }
        });
    }

    private List<String> getJobJMSId(UUID jobtoken,String queueName) {
        String messageSelector = "jobtoken='"+jobtoken.toString()+"'";
        System.out.println(" MessageSelector for browse:"+messageSelector);
        return jmsTemplate.browseSelected(queueName, messageSelector,new BrowserCallback<List<String>>() {
//            return jmsMessagingTemplate.getJmsTemplate().browseSelected(queueName, messageSelector,new BrowserCallback<List<String>>() {
                @Override
            public List<String> doInJms(Session session, QueueBrowser browser) throws JMSException {
                List<String> rv = new ArrayList<>();
                Enumeration messages = browser.getEnumeration();
                if(messages.hasMoreElements()){
                    javax.jms.Message m = (javax.jms.Message)messages.nextElement();
                    rv.add(m.getJMSMessageID());
                    System.out.println("  "+m.getStringProperty("jobtoken")+"  adding "+m.getJMSMessageID());
                }
                return  rv;
            }
        });
    }

    public String browseQueue(String queue) {
        return jmsMessagingTemplate.getJmsTemplate().browse(queue, (session, browser) -> {
            Enumeration<?> messages = browser.getEnumeration();
            int total = 0;
            while (messages.hasMoreElements()) {
                javax.jms.Message m = (javax.jms.Message)messages.nextElement();
                if (m instanceof TextMessage) {
                    TextMessage message = (TextMessage) m;
                    System.out.println(" browse message="+message);
                }
                total++;
            }
            return String.format("Total '%d elements waiting in %s", total, queue);
        });
    }

    private String getJobPayload(UUID jobtoken,String queueName) {
        String messageSelector = "jobtoken='"+jobtoken.toString()+"'";
        System.out.println(" getJobPayload MessageSelector for browse:"+messageSelector + " queue="+queueName);
        return jmsTemplate.browseSelected(queueName, messageSelector,new BrowserCallback<String>() {
//            return jmsMessagingTemplate.getJmsTemplate().browseSelected(queueName, messageSelector,new BrowserCallback<String>() {
                @Override
            public String doInJms(Session session, QueueBrowser browser) throws JMSException {
                String rv= null;
                Enumeration messages = browser.getEnumeration();
                if(messages.hasMoreElements()){
                    javax.jms.Message m = (javax.jms.Message)messages.nextElement();
                    if (m instanceof TextMessage) {
                        TextMessage message = (TextMessage) m;
                        rv = message.getText();
                    }
                    System.out.println("  getPayload="+rv);
                }
                return  rv;
            }
        });
    }

    public boolean deleteJmsStatusMessage(String jobtoken){
        boolean rv = false;
//        JmsTemplate client=jmsMessagingTemplate.getJmsTemplate();
//        ConnectionFactory cf = client.getConnectionFactory();
        ConnectionFactory cf = jmsTemplate.getConnectionFactory();

        if(getStatus(UUID.fromString(jobtoken)) != HttpStatus.NO_CONTENT) {
            Connection connection = null;
            Session session = null;
            try {
                connection = cf.createConnection();
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createQueue(subSystem + "Status");
                String selector = "jobtoken = '" + jobtoken + "'";
                javax.jms.Message message = jmsTemplate.receiveSelected(destination, selector);
//                javax.jms.Message message = client.receiveSelected(destination, selector);
                if (message != null) {
                    System.out.println(" delete message with selector:" + selector + " message=" + message);
//                    System.out.println("Message:"+ jobtoken + " consumed.");
                    rv = true;
                }

            } catch (JMSException e) {
                logger.error("Failed to read message from MessageConsumer. " + e);
            } finally {
                try {
                    session.close();
                } catch (Exception e) { /* NOP */ }
                try {
                    connection.close();
                } catch (Exception e) { /* NOP */ }
            }
        } else {
            System.out.println("Can't find status  message to delete");
        }
        return rv;
    }

    public int handleImportAsync(UUID jobtoken, MessageHeaderAccessor headers, String subsystem, String uri, String payload) {

        System.out.println("handleImportAsync subsystem:"+subsystem+" Uri:"+uri);
        if (!SUPPORTED_SYSTEMS.contains(subsystem)) {
            logger.error("Unsupported subsystem:<" + subsystem + "> (Currently supported subsystems: "+SET_VALUES);
            return  HttpStatus.NOT_ACCEPTABLE.value();
        }

        // Check uri
        /*
        if(checkIfImportIsRunning(uri)){
            logger.error("Import running for URI:<" + uri + ">");
            return  HttpStatus.CONFLICT.value();
        }
*/
        // If jobtoken is not set, create new one
        if(jobtoken == null)
            jobtoken=UUID.randomUUID();
        MessageHeaderAccessor accessor = new MessageHeaderAccessor();
        if (headers != null) {
            // Add application specific headers
            accessor.copyHeaders(headers.toMap());
        }
        // Authenticated user, force defautl if not given
        String userId = userProvider.getUser().getId() != null ? userProvider.getUser().getId().toString(): "7095c4eb-d2ce-4a32-9245-def5c0c8fd8d";
        accessor.setHeader("userId",  userId);
        // Token which is used when querying status
        accessor.setHeader("jobtoken", jobtoken.toString());
        // Target identification data
        accessor.setHeader("system", subsystem);
        accessor.setHeader("uri", uri);
        // Use jobtoken as correlationID
        accessor.setHeader("JMSCorrelationID",jobtoken.toString());

        Message mess = MessageBuilder
                    .withPayload(payload)
                    .setHeaders(accessor)
                    .build();
            // send item for processing
        System.out.println("Send job:"+jobtoken+" to the processing queue:"+subsystem+"Incoming");
        jmsMessagingTemplate.send(subsystem+"Incoming", mess);

        StatusTest(mess);        
        return  HttpStatus.OK.value();
    }

    @SendTo("${mq.active.subsystem}StatusTest")
    public Message StatusTest(Message mess){
        return mess;
    }
    public void setReady(String jobtoken) {
        int deletecount=countMessageByJobtoken(UUID.fromString(jobtoken), subSystem+"Status");
        System.out.println("SetReady - Consume Processed item from:"+subSystem+"Status" + " count="+deletecount);
        // Consume all existing
        for(int x=0;x<deletecount; x++){
            if (deleteJmsStatusMessage(jobtoken)) {
                System.out.println("Delete done");
            }
        }
    }

    private javax.jms.Message getMessageByJobtoken(UUID jobtoken, String queueName) {
        return jmsTemplate.browseSelected(queueName, "jobtoken='"+jobtoken.toString()+"'",new BrowserCallback<javax.jms.Message>() {
//            return jmsMessagingTemplate.getJmsTemplate().browseSelected(queueName, "jobtoken='"+jobtoken.toString()+"'",new BrowserCallback<javax.jms.Message>() {
                @Override
            public javax.jms.Message doInJms(Session session, QueueBrowser browser) throws JMSException {
                javax.jms.Message mess = null;
                Enumeration messages = browser.getEnumeration();
                System.out.println("getMessagesByJobToken "+messages.hasMoreElements());
                if(messages.hasMoreElements()){
                    mess = (javax.jms.Message)messages.nextElement();
                }
                return  mess;
            }
        });
    }

    private int countMessageByJobtoken(UUID jobtoken, String queueName) {
        return jmsTemplate.browseSelected(queueName, "JMSCorrelationID='"+jobtoken.toString()+"'",new BrowserCallback<Integer>() {
//            return jmsMessagingTemplate.getJmsTemplate().browseSelected(queueName, "JMSCorrelationID='"+jobtoken.toString()+"'",new BrowserCallback<Integer>() {
                @Override
            public Integer doInJms(Session session, QueueBrowser browser) throws JMSException {
                int count =0;
                Enumeration messages = browser.getEnumeration();
                count=Collections.list(messages).size();
                System.out.println("countMessagesByJobToken "+count);
                return  count;
            }
        });
    }

    public void setStatusDelete(boolean ready, String jobtoken, String uri,  String payload) {
        System.out.println(jobtoken+"  Set status to Processed item from:"+subSystem+"Status"+ " Value="+payload);
        try {
            Message mess=null;
            // Get previous status and copy headers
            javax.jms.Message m = getMessageByJobtoken(UUID.fromString(jobtoken), subSystem+"Status");
            if(m != null) {
                // Copy headers
                Enumeration en = m.getPropertyNames();
                MessageHeaderAccessor accessor = new MessageHeaderAccessor();
                while (en.hasMoreElements()) {
                    String name = (String) en.nextElement();
                    System.out.println("Header:" + name + "=" + m.getStringProperty(name));
                    if (!name.equalsIgnoreCase("timestamp") && !name.equalsIgnoreCase("__AMQ_CID"))
                        accessor.setHeader(name, m.getStringProperty(name));
                }
                // Consume existing
                if(deleteJmsStatusMessage(jobtoken)) {
                    System.out.println("Delete done");
                }
                System.out.println("Send new statusMessage");
                // Replace it with new one
                mess = MessageBuilder
                        .withPayload(payload)
                        .setHeaders(accessor)
                        .build();
            } else {
                System.out.println("SetStatus, create new one");
                // Add application specific headers
                MessageHeaderAccessor accessor = new MessageHeaderAccessor();
                // Authenticated user
                if(userProvider!= null && userProvider.getUser() != null && userProvider.getUser().getId()!=null)
                    accessor.setHeader("userId",  userProvider.getUser().getId().toString());
                // Token which is used when querying status
                accessor.setHeader("jobtoken", jobtoken.toString());
                //  Use  jobtoken as correlation id
                accessor.setHeader("JMSCorrelationID",jobtoken.toString());
                // Target identification data
                accessor.setHeader("system", subSystem);
                accessor.setHeader("uri", uri);
                mess = MessageBuilder
                        .withPayload(payload)
                        .setHeaders(accessor)
                        .build();
            }
            // send new replacing iten to Status-queue
            if(mess != null) {
                jmsMessagingTemplate.send(subSystem + "Status", mess);
                // Update status cache
                currentStatus.put(jobtoken, mess);
                currentStatus.put(uri, mess);
            }
        } catch (JMSException jex) {
            jex.printStackTrace();
        }
    }


    @SendTo("${mq.active.subsystem}Status")
    public Message setStatus(int status, String jobtoken, String userId, String uri,  String payload) {

        // Consume previous
//        if(deleteJmsStatusMessage(jobtoken)){
//            System.out.println("    setStatus previous deleted.");
//        }

        // Add application specific headers
        MessageHeaderAccessor accessor = new MessageHeaderAccessor();
        // Authenticated user
        accessor.setHeader("userId",  userId);
        // Token which is used when querying status
        accessor.setHeader("jobtoken", jobtoken.toString());
        //  Use  jobtoken as correlation id
        accessor.setHeader("JMSCorrelationID",jobtoken.toString());
        // Target identification data
        accessor.setHeader("system", subSystem);
        accessor.setHeader("uri", uri);
        // Set status as int
        accessor.setHeader("status",status);

        Message mess = MessageBuilder
                .withPayload(payload)
                .setHeaders(accessor)
                .build();
        // send new  item to Status-queue
        if(mess != null) {
            System.out.println("Send status message to queue:");
            jmsMessagingTemplate.send(subSystem + "Status", mess);
            jmsTemplate.convertAndSend(subSystem + "Status", mess);
            // Update internal cache
            currentStatus.put(jobtoken, mess);
            currentStatus.put(uri, mess);

            System.out.println("SEND STATUS:"+mess);
        }
        System.out.println("Send Status to QUEUE "+ mess);

        return mess;
    }
}
