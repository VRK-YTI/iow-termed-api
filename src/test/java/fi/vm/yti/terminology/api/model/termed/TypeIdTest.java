package fi.vm.yti.terminology.api.model.termed;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.util.JsonUtils;

import static org.junit.Assert.*;

public class TypeIdTest {
   String jsonString="{\"type\":{\"id\":\"TerminologicalVocabulary\",\"graph\":{\"id\":\"c7168ae0-4bd1-4b2c-ad79-b1ddbde49d12\"}}}";

    private Type ti =null;
    @org.junit.Before
    public void setUp() throws Exception {
        System.out.println("incoming"+jsonString);
        ObjectMapper mapper = new ObjectMapper();
        ti = mapper.readValue(jsonString,Type.class);
        JsonUtils.prettyPrintJson(ti);
        assertNotNull(ti);
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

    @org.junit.Test
    public void getId() {
        if(ti != null && ti.getType() != null ){
            assertEquals("TerminologicalVocabulary",ti.getType().getId().name());
        }
        else
            fail("GetId test Failed");
    }

    @org.junit.Test
    public void getGraphId() {
        if(ti != null && ti.getType() != null ){
            assertEquals("c7168ae0-4bd1-4b2c-ad79-b1ddbde49d12",ti.getType().getGraphId().toString());
        }
        else
            fail("GetId test Failed");
    }

}