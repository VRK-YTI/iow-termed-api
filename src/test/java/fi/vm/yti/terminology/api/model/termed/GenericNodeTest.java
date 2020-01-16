package fi.vm.yti.terminology.api.model.termed;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.util.JsonUtils;
import static org.junit.jupiter.api.Assertions.*;

public class GenericNodeTest {

    String jsonString = "{\"id\":\"30893fad-cd46-437e-a51c-69ce4e701adc\",\"code\":\"concept-link-2\",\"uri\":\"http://uri.suomi.fi/terminology/idha/concept-link-2\",\"number\":2,\"createdBy\":\"5dc030a4-740f-4dde-9548-4e6e797899d8\",\"createdDate\":\"2018-05-28T12:42:45.103Z\",\"lastModifiedBy\":\"5dc030a4-740f-4dde-9548-4e6e797899d8\",\"lastModifiedDate\":\"2018-09-27T12:58:40.513Z\",\"type\":{\"id\":\"ConceptLink\",\"graph\":{\"id\":\"43037b41-73c7-4ee7-979f-826462e988b9\"}},\"properties\":{\"prefLabel\":[{\"lang\":\"fi\",\"value\":\"henkilötunnus\",\"regex\":\"(?s)^.*$\"}],\"vocabularyLabel\":[{\"lang\":\"fi\",\"value\":\"Julkisen hallinnon yhteinen sanasto\",\"regex\":\"(?s)^.*$\"}],\"targetId\":[{\"lang\":\"\",\"value\":\"900df251-b787-3a6d-9a74-3d1faccbd058\",\"regex\":\"(?s)^.*$\"}],\"targetGraph\":[{\"lang\":\"\",\"value\":\"9458edd5-d15f-4b9c-b981-2178c282e0eb\",\"regex\":\"(?s)^.*$\"}]},\"references\":{},\"referrers\":{\"exactMatch\":[{\"id\":\"b4389526-ffe2-4816-8d7d-d7910b39f467\",\"code\":\"concept-9\",\"uri\":\"http://uri.suomi.fi/terminology/idha/concept-9\",\"number\":9,\"createdBy\":\"5dc030a4-740f-4dde-9548-4e6e797899d8\",\"createdDate\":\"2018-05-28T12:39:36.608Z\",\"lastModifiedBy\":\"5dc030a4-740f-4dde-9548-4e6e797899d8\",\"lastModifiedDate\":\"2018-09-27T12:58:40.513Z\",\"type\":{\"id\":\"Concept\",\"graph\":{\"id\":\"43037b41-73c7-4ee7-979f-826462e988b9\"}},\"properties\":{\"status\":[{\"lang\":\"\",\"value\":\"DRAFT\",\"regex\":\"(?s)^.*$\"}]},\"references\":{},\"referrers\":{}}]}}";

    private GenericNode gn = null;

    @BeforeEach
    public void setUp() throws Exception {
        System.out.println("incoming" + jsonString);
        ObjectMapper mapper = new ObjectMapper();
        gn = mapper.readValue(jsonString, GenericNode.class);
        JsonUtils.prettyPrintJson(gn);
        assertNotNull(gn);
    }

    @Test
    public void getType() {
        if (gn != null && gn.getType() != null) {
            assertEquals("ConceptLink", gn.getType().getId().name());
        } else {
            fail("GetType test Failed");
        }
    }

    @Test
    public void getReferrers() {
        if (gn != null && gn.getReferrers().get("exactMatch") != null) {
            List<Identifier> idf = gn.getReferrers().get("exactMatch");
            assertEquals("http://uri.suomi.fi/terminology/idha/concept-9", idf.get(0).getUri());
        } else {
            fail("GetReferrers test Failed");
        }
    }
}
