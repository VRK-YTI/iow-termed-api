package fi.vm.yti.terminology.api.model.termed;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.migration.DomainIndex;
import fi.vm.yti.terminology.api.util.JsonUtils;
import static org.junit.jupiter.api.Assertions.*;

public class GenericNodeTest {

    String jsonString = "{\"id\":\"30893fad-cd46-437e-a51c-69ce4e701adc\",\"code\":\"concept-link-2\",\"uri\":\"http://uri.suomi.fi/terminology/idha/concept-link-2\",\"number\":2,\"createdBy\":\"5dc030a4-740f-4dde-9548-4e6e797899d8\",\"createdDate\":\"2018-05-28T12:42:45.103Z\",\"lastModifiedBy\":\"5dc030a4-740f-4dde-9548-4e6e797899d8\",\"lastModifiedDate\":\"2018-09-27T12:58:40.513Z\",\"type\":{\"id\":\"ConceptLink\",\"graph\":{\"id\":\"43037b41-73c7-4ee7-979f-826462e988b9\"}},\"properties\":{\"prefLabel\":[{\"lang\":\"fi\",\"value\":\"henkilötunnus\",\"regex\":\"(?s)^.*$\"}],\"vocabularyLabel\":[{\"lang\":\"fi\",\"value\":\"Julkisen hallinnon yhteinen sanasto\",\"regex\":\"(?s)^.*$\"}],\"targetId\":[{\"lang\":\"\",\"value\":\"900df251-b787-3a6d-9a74-3d1faccbd058\",\"regex\":\"(?s)^.*$\"}],\"targetGraph\":[{\"lang\":\"\",\"value\":\"9458edd5-d15f-4b9c-b981-2178c282e0eb\",\"regex\":\"(?s)^.*$\"}]},\"references\":{},\"referrers\":{\"exactMatch\":[{\"id\":\"b4389526-ffe2-4816-8d7d-d7910b39f467\",\"code\":\"concept-9\",\"uri\":\"http://uri.suomi.fi/terminology/idha/concept-9\",\"number\":9,\"createdBy\":\"5dc030a4-740f-4dde-9548-4e6e797899d8\",\"createdDate\":\"2018-05-28T12:39:36.608Z\",\"lastModifiedBy\":\"5dc030a4-740f-4dde-9548-4e6e797899d8\",\"lastModifiedDate\":\"2018-09-27T12:58:40.513Z\",\"type\":{\"id\":\"Concept\",\"graph\":{\"id\":\"43037b41-73c7-4ee7-979f-826462e988b9\"}},\"properties\":{\"status\":[{\"lang\":\"\",\"value\":\"DRAFT\",\"regex\":\"(?s)^.*$\"}]},\"references\":{},\"referrers\":{}}]}}";
    String jsonString2 = "[{\"id\":\"81d2eb4b-3855-11ea-be24-db2d3d09470f\",\"code\":\"concept-2052\",\"uri\":\"http://uri.suomi.fi/terminology/jhs/J491\",\"number\":2052,\"createdBy\":\"admin\",\"createdDate\":\"2020-01-16T11:44:29Z\",\"lastModifiedBy\":\"admin\",\"lastModifiedDate\":\"2020-01-16T12:28:44Z\",\"type\":{\"id\":\"Concept\",\"graph\":{\"id\":\"61cf6bde-46e6-40bb-b465-9b2c66bf4ad8\"}},\"properties\":{\"definition\":[{\"lang\":\"fi\",\"value\":\"tietty� aatteellista tarkoitusta varten perustettu yhdistys\",\"regex\":\"(?s)^.*$\"}],\"source\":[{\"lang\":\"\",\"value\":\"Tuotu JHSMETA.fi palvelusta. Laadintahetki: 2013-11-06. Viimeisin muokkaushetki: 2013-11-06. Omistaja: Ydinsanastoryhm�. Laatija: Mikael af H�llstr�m.\",\"regex\":\"(?s)^.*$\"}],\"status\":[{\"lang\":\"\",\"value\":\"VALID\",\"regex\":\"(?s)^.*$\"}]},\"references\":{\"definedInScheme\":[{\"id\":\"81d2e917-3855-11ea-be24-db2d3d09470f\",\"code\":\"terminological-vocabulary-19\",\"uri\":\"http://uri.suomi.fi/terminology/jhs\",\"number\":19,\"createdBy\":\"admin\",\"createdDate\":\"2020-01-16T11:44:29Z\",\"lastModifiedBy\":\"admin\",\"lastModifiedDate\":\"2020-01-16T11:44:29Z\",\"type\":{\"id\":\"TerminologicalVocabulary\",\"graph\":{\"id\":\"61cf6bde-46e6-40bb-b465-9b2c66bf4ad8\"}},\"properties\":{\"status\":[{\"lang\":\"\",\"value\":\"VALID\",\"regex\":\"(?s)^.*$\"}],\"description\":[{\"lang\":\"en\",\"value\":\"The Finnish Public Sector Terminological Glossary is a controlled vocabulary consisting of terms representing concepts that are defined in accordance with the Finnish Public Sector Recommendation JHS175. The concepts form a shared and harmonized core vocabulary for all public sector organizations. \",\"regex\":\"(?s)^.*$\"}],\"language\":[{\"lang\":\"\",\"value\":\"en\",\"regex\":\"(?s)^.*$\"},{\"lang\":\"\",\"value\":\"sv-FI\",\"regex\":\"(?s)^.*$\"},{\"lang\":\"\",\"value\":\"sv-SE\",\"regex\":\"(?s)^.*$\"},{\"lang\":\"\",\"value\":\"fi\",\"regex\":\"(?s)^.*$\"}],\"prefLabel\":[{\"lang\":\"fi\",\"value\":\"Julkisen hallinnon yhteinen sanasto\",\"regex\":\"(?s)^.*$\"},{\"lang\":\"en\",\"value\":\"Finnish Public Sector Terminological Glossary (Controlled Vocabulary)\",\"regex\":\"(?s)^.*$\"}]},\"references\":{},\"referrers\":{}}],\"prefLabelXl\":[{\"id\":\"81d31183-3855-11ea-be24-db2d3d09470f\",\"code\":\"term-3627\",\"uri\":\"http://uri.suomi.fi/terminology/jhs/idelem1551x2\",\"number\":3627,\"createdBy\":\"admin\",\"createdDate\":\"2020-01-16T11:44:29Z\",\"lastModifiedBy\":\"admin\",\"lastModifiedDate\":\"2020-01-16T11:44:29Z\",\"type\":{\"id\":\"Term\",\"graph\":{\"id\":\"61cf6bde-46e6-40bb-b465-9b2c66bf4ad8\"}},\"properties\":{\"prefLabel\":[{\"lang\":\"fi\",\"value\":\"aatteellinen yhdistys\",\"regex\":\"(?s)^.*$\"}],\"status\":[{\"lang\":\"\",\"value\":\"DRAFT\",\"regex\":\"(?s)^.*$\"}]},\"references\":{},\"referrers\":{}}],\"broader\":[{\"id\":\"81d2ea26-3855-11ea-be24-db2d3d09470f\",\"code\":\"concept-2298\",\"uri\":\"http://uri.suomi.fi/terminology/jhs/J148\",\"number\":2298,\"createdBy\":\"admin\",\"createdDate\":\"2020-01-16T11:44:29Z\",\"lastModifiedBy\":\"admin\",\"lastModifiedDate\":\"2020-01-16T11:44:29Z\",\"type\":{\"id\":\"Concept\",\"graph\":{\"id\":\"61cf6bde-46e6-40bb-b465-9b2c66bf4ad8\"}},\"properties\":{\"definition\":[{\"lang\":\"fi\",\"value\":\"julkisen teht�v�n tai aatteellisten p��m��r�n perusteella kokonaisuuden muodostava yhteis�\",\"regex\":\"(?s)^.*$\"}],\"note\":[{\"lang\":\"fi\",\"value\":\"Yhdistyksi� ovat aatteelliset, julkisoikeudelliset ja taloudelliset yhdistykset. Yhdistykset voivat olla rekister�ityj� tai rekister�im�tt�mi�. Vuonna 2013 kaupparekisteriin on merkittyn� vain yksi taloudellinen yhdistys.\",\"regex\":\"(?s)^.*$\"}],\"source\":[{\"lang\":\"\",\"value\":\"Tuotu JHSMETA.fi palvelusta. Laadintahetki: 2010-12-08. Viimeisin muokkaushetki: 2013-11-06. Omistaja: Ydinsanastoryhm�. Laatija: Mikael af H�llstr�m.\",\"regex\":\"(?s)^.*$\"}],\"status\":[{\"lang\":\"\",\"value\":\"VALID\",\"regex\":\"(?s)^.*$\"}]},\"references\":{},\"referrers\":{}}]},\"referrers\":{}}]";
    private GenericNode gn = null;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setUp() throws Exception {
//        System.out.println("incoming" + jsonString);
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

    @Test
    public void getDefinedInScheme() throws Exception {
        File resource = new File("src/test/resources/model/termed/definedInScheme.json");
        String json = new String(java.nio.file.Files.readAllBytes(resource.toPath()), "UTF8");
        GenericNode gNode = mapper.readValue(json, GenericNode.class);
        if (gNode != null && gNode.getReferences().get("definedInScheme") != null) {
            List<Identifier> idf = gNode.getReferences().get("definedInScheme");
            assertEquals("http://uri.suomi.fi/terminology/jhs", idf.get(0).getUri());
            assertEquals("81d2e917-3855-11ea-be24-db2d3d09470f", idf.get(0).getId().toString());

        } else {
            fail("GetDefinedInSheme test Failed");
        }
    }

    @Test
    public void putDefinedInScheme() throws Exception {
        File resource = new File("src/test/resources/model/termed/definedInScheme.json");
        String json = new String(java.nio.file.Files.readAllBytes(resource.toPath()), "UTF8");
        GenericNode gNode = mapper.readValue(json, GenericNode.class);
        TypeId typeId = new TypeId(NodeType.TerminologicalVocabulary, new GraphId(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID));
        gNode.addDefinedInScheme(UUID.fromString("11111111-1111-11ea-be24-db2d3d09470f"), typeId);
        if (gNode != null) {
            // change 
            List<Identifier> idf = gNode.getReferences().get("definedInScheme");
            assertEquals("11111111-1111-11ea-be24-db2d3d09470f", idf.get(0).getId().toString());
            JsonUtils.prettyPrintJson(gNode);
        } else {
            fail("putDefinedInSheme test Failed");
        }        
    }    

    @Test
    public void putDefinedInSchemeNull() throws Exception {
        File resource = new File("src/test/resources/model/termed/definedInSchemeNullRefs.json");
        String json = new String(java.nio.file.Files.readAllBytes(resource.toPath()), "UTF8");
        GenericNode gNode = mapper.readValue(json, GenericNode.class);
        TypeId typeId = new TypeId(NodeType.TerminologicalVocabulary, new GraphId(DomainIndex.TERMINOLOGICAL_VOCABULARY_TEMPLATE_GRAPH_ID));
        gNode.addDefinedInScheme(UUID.fromString("11111111-1111-11ea-be24-db2d3d09470f"), typeId);
        if (gNode != null) {
            // change 
            List<Identifier> idf = gNode.getReferences().get("definedInScheme");
            assertEquals("11111111-1111-11ea-be24-db2d3d09470f", idf.get(0).getId().toString());
            JsonUtils.prettyPrintJson(gNode);
        } else {
            fail("putDefinedInSheme test Failed");
        }
    }    
}
