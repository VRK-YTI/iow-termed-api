package fi.vm.yti.terminology.api.model.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.vm.yti.terminology.api.model.termed.Attribute;
import java.util.Date;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

//@SpringBootTest
public class ConceptSuggestionTest {

    String jsonString = "{\"prefLabel\":{\"lang\":\"fi\",\"value\":\"esimerkki\"},\"definition\":{\"lang\":\"fi\",\"value\":\"jotain\"},\"creator\":\"45778009-804c-4aba-a836-f5c911ea5ef1\",\"terminologyUri\":\"http://uri.suomi.fi/terminology/kira/\",\"uri\":\"http://uri.suomi.fi/terminology/kira/\", \"created\":\"2019-09-17T09:54:30.139\"}";

    private ConceptSuggestionResponse cs = null;

    @BeforeEach
    public void setUp() throws Exception {
        System.out.println("incoming" + jsonString);
        ObjectMapper mapper = new ObjectMapper();
        cs = mapper.readValue(jsonString, ConceptSuggestionResponse.class);
        assertNotNull(cs);
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    public void getPrefLabel() {
        if (cs != null && cs.getPrefLabel() != null) {
            assertEquals("esimerkki", cs.getPrefLabel().getValue());
        } else {
            fail("GetPrefLabel test Failed");
        }
    }

    @Test
    public void getDefinition() {
        System.out.println("test getDefinitionLabel");
        if (cs != null && cs.getDefinition() != null) {
            assertEquals("jotain", cs.getDefinition().getValue());
        } else {
            fail("GetDefinition test Failed");
        }
    }

    @Test
    public void getCreator() {
        if (cs != null && cs.getCreator() != null) {
            assertEquals("45778009-804c-4aba-a836-f5c911ea5ef1", cs.getCreator().toString());
        } else {
            fail("GetCreator test Failed");
        }
    }

    @Test
    public void getterminologyUri() {
        if (cs != null && cs.getTerminologyUri() != null) {
            assertEquals("http://uri.suomi.fi/terminology/kira/", cs.getTerminologyUri());
        } else {
            fail("GetTerminologyUri test Failed");
        }
    }

    @Test
    public void getUri() {
        String expected = "http://uri.suomi.fi/terminology/kira/";
        if (cs != null && cs.getTerminologyUri() != null) {
            assertEquals(expected, cs.getTerminologyUri());
        } else {
            fail("GetUri test Failed");
        }
    }

    @Test
    public void getCreated() {
        System.err.println("getCreated test not implemented");
        // TODO
    }
}
