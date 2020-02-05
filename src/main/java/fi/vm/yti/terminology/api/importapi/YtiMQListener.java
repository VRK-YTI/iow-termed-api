package fi.vm.yti.terminology.api.importapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Session;

@Component
public class YtiMQListener {
    private final String subSystem;

    // JMS-client
    @Autowired
    private JmsMessagingTemplate jmsMessagingTemplate;

    @Autowired
    public YtiMQListener(JmsMessagingTemplate jmsMessagingTemplate,
                         @Value("${mq.active.subsystem}") String subSystem) {
        this.jmsMessagingTemplate = jmsMessagingTemplate;
        this.subSystem = subSystem;
    }

    /**
     * State-handler queue, just receive and move it into the actual processing queue
     *
     * @param message
     * @return
     * @throws JMSException
     */
    @JmsListener(destination =  "${mq.active.subsystem}Incoming")
    @SendTo("${mq.active.subsystem}Processing")
    public Message receiveMessage(final Message message,
                                  Session session,
                                  @Header String jobtoken,
                                  @Header String userId,
                                  @Header String uri) throws JMSException {
        MessageHeaderAccessor accessor = new MessageHeaderAccessor();
        accessor.copyHeaders(message.getHeaders());
        accessor.setLeaveMutable(true);

        // Send status message
        Message mess = MessageBuilder
                .withPayload("Processing " + uri)
                .setHeaders(accessor)
                .build();
        return message;
    }
}