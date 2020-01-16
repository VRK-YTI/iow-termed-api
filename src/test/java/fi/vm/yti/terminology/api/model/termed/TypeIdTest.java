package fi.vm.yti.terminology.api.model.termed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.util.JsonUtils;
import static org.junit.jupiter.api.Assertions.*;

public class TypeIdTest {

    String jsonString = "{\"type\":{\"id\":\"TerminologicalVocabulary\",\"graph\":{\"id\":\"c7168ae0-4bd1-4b2c-ad79-b1ddbde49d12\"}}}";

    private Type ti = null;

    @BeforeEach
    public void setUp() throws Exception {
        System.out.println("incoming" + jsonString);
        ObjectMapper mapper = new ObjectMapper();
        ti = mapper.readValue(jsonString, Type.class);
        JsonUtils.prettyPrintJson(ti);
        assertNotNull(ti);
    }

    @Test
    public void getId() {
        if (ti != null && ti.getType() != null) {
            assertEquals("TerminologicalVocabulary", ti.getType().getId().name());
        } else {
            fail("GetId test Failed");
        }
    }

    @Test
    public void getGraphId() {
        if (ti != null && ti.getType() != null) {
            assertEquals("c7168ae0-4bd1-4b2c-ad79-b1ddbde49d12", ti.getType().getGraphId().toString());
        } else {
            fail("GetId test Failed");
        }
    }

}
