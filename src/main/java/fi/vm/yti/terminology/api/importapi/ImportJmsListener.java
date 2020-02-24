package fi.vm.yti.terminology.api.importapi;

import java.io.Reader;
import java.io.StringReader;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import fi.vm.yti.terminology.api.model.ntrf.VOCABULARY;

@Component
public class ImportJmsListener {
    private static final Logger logger = LoggerFactory.getLogger(ImportJmsListener.class);

	@Autowired
	NtrfMapper ntrfMapper;
    // JMS-client
    @Autowired
    private JmsMessagingTemplate jmsMessagingTemplate;

    /**
     * Implements actual import operation.
     * @param message
     * @param session
     * @param jobtoken
     * @param userId
     * @param system
     * @param vocabularyId
     * @param uri
     * @return
     * @throws JMSException
     */
    @JmsListener(id="NtrfProcessor", destination = "${mq.active.subsystem}Processing")
	@SendTo("${mq.active.subsystem}Ready")
	public Message<String> processMessage(final Message<String> message,Session session,
                                 @Header String jobtoken,
                                 @Header String userId,
                                 @Header String system,
                                 @Header String vocabularyId,
                                 @Header String uri) throws JMSException {
        // Consume incoming
        if(logger.isDebugEnabled()){
            logger.debug("Process message "+ message.getHeaders());
            logger.debug("session= "+ session);
            logger.debug("UserId="+userId);
        }
        String payload ="{}";
        if(jmsMessagingTemplate == null){
            logger.error("MessagingTemplate not initialized!!!!!");
        }
        // Process ntrf item
        try {
            JAXBContext jc = JAXBContext.newInstance(VOCABULARY.class);
            // Disable DOCTYPE-directive from incoming file.
            XMLInputFactory xif = XMLInputFactory.newFactory();
            xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            // Unmarshall XMl with JAXB
            Reader inReader = new StringReader((String)message.getPayload());
            XMLStreamReader xsr = xif.createXMLStreamReader(inReader);
            Unmarshaller unmarshaller = jc.createUnmarshaller();
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            // At last, resolve ntrf-POJO's
            VOCABULARY voc = (VOCABULARY) unmarshaller.unmarshal(xsr);
            payload=ntrfMapper.mapNtrfDocument( jobtoken, UUID.fromString(vocabularyId), voc,UUID.fromString(userId));
        } catch (XMLStreamException se) {
            logger.error("Incoming transform error=" + se);
        } catch(JAXBException je){
            logger.error("Incoming transform error=" + je);
        }

        // Set import as handled. IE. consume processed message
        logger.info("Import handled:" + payload);
/*
        MessageHeaderAccessor accessor = new MessageHeaderAccessor();
        accessor.copyHeaders(message.getHeaders());
        // Set result as a payload and move it to ready-queue1
        Message<String> mess = MessageBuilder
                .withPayload(payload)
                .setHeaders(accessor)
                .build();
        return mess; */
        return message;
	}
}