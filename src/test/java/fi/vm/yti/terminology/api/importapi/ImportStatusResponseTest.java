package fi.vm.yti.terminology.api.importapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

public class ImportStatusResponseTest {

    String jsonString = "{\"status\":\"SUCCESS_WITH_ERRORS\",\"processingTotal\":910,\"resultsWarning\":0,\"resultsError\":744,\"processingProgress\":910,\"statusMessage\":[{\"targetIdentifier\":\"tmpOKSAID116\",\"message\":\"[Not matching reference found for :KatriSeppala]\"},{\"targetIdentifier\":\"tmpOKSAID117\",\"message\":\"[Not matching reference found for :KatriSeppala]\"},{\"targetIdentifier\":\"tmpOKSAID118\",\"message\":\"[Not matching reference found for :tr63]\"}]}";
    ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        System.out.println("Setup");
    }

    @After
    public void tearDown() throws Exception {
        System.out.println("teardown");
    }

    @Test
    public void toStringTest() {
        ImportStatusResponse status= new ImportStatusResponse();
        status.setProcessingTotal(10);
        status.setProcessingProgress(8);
        status.setStatus(ImportStatusResponse.Status.PROCESSING);
        status.addStatusMessage(new ImportStatusMessage("codeID1","warning! some problems"));
        status.addStatusMessage(new ImportStatusMessage("codeID1","warning! some problems count 2"));
        status.addStatusMessage(new ImportStatusMessage("codeID2","warning! 2"));
        try {
            String result = mapper.writeValueAsString(status);
            System.out.println("toString() Result="+result);
        } catch(JsonProcessingException jex){
            jex.printStackTrace();
        }
    }

    @Test
    public void fromString() {
        // Read json
        System.out.println("test fromString: ");
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("Map result from json: "+jsonString);
        try {
            System.out.println("Map result from json: "+jsonString);

            ImportStatusResponse resp = mapper.readValue(jsonString,ImportStatusResponse.class);
            assertNotNull(resp);
            assertEquals(ImportStatusResponse.Status.SUCCESS_WITH_ERRORS,resp.getStatus());
            assertEquals((int)910,(int)resp.getProcessingTotal());

        } catch(JsonMappingException jme){
            jme.printStackTrace();
        } catch(JsonProcessingException jex){
            jex.printStackTrace();
        }
    }
}
