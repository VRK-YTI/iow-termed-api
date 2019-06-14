package fi.vm.yti.terminology.api.importapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class ImportStatusMessageTest {
    String statusStr="{\"targetIdentifier\":\"tmpOKSAID116\",\"message\":\"[Not matching reference found for :KatriSeppala]\"}";
    @Before
    public void setUp() throws Exception {
        System.out.println("ImportStatusMessagetest() before");
    }

    @Test
    public void getTargetIdentifier() {
    }

    @Test
    public void setTargetIdentifier() {
    }

    @Test
    public void getMessage() {
    }

    @Test
    public void setMessage() {
        System.out.println("SetMessage");
    }

    @Test
    public void fromString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println("Map result from json: "+statusStr);
            ImportStatusMessage resp = mapper.readValue(statusStr,ImportStatusMessage.class);
            assertNotNull(resp);
            System.out.println(resp);
            assertEquals("[Not matching reference found for :KatriSeppala]",resp.getMessage());

        } catch(JsonMappingException jme){
            jme.printStackTrace();
        } catch(JsonProcessingException jex){
            jex.printStackTrace();
        } catch(IOException ioe){
            ioe.printStackTrace();
        }
    }
}