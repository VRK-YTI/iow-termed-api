package fi.vm.yti.terminology.api.migration.task;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.migration.PropertyUtil;
import fi.vm.yti.terminology.api.model.termed.*;
import fi.vm.yti.terminology.api.util.JsonUtils;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Migration for YTI-46.
 */
@Component
public class V18_SwedishTranslations implements MigrationTask {

    private final MigrationService migrationService;

    V18_SwedishTranslations(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        migrationService.updateTypes(VocabularyNodeType.TerminologicalVocabulary, meta -> {
            // Go through text-attributes and add descriptions to known ids
            updateMeta(meta);
        });
    }

    /**
     * Update MetaNodes text- and reference-attributes with Swedish translations
     * 
     * @param meta MetaNode to be updated
     * @return true if updated
     */
    boolean updateMeta(MetaNode meta) {
        boolean rv = false;

        String domainName = meta.getDomain().getId().name();
        if (domainName.equals("TerminologicalVocabulary")) {
            meta.updateProperties(PropertyUtil.prefLabel("Terminologinen sanasto", "Terminological Dictionary", "Terminologisk ordlista"));

            updateTextAttributePrefLabel(meta, "prefLabel", "Nimi", "Name", "Namn");
            updateTextAttributeDescription(meta, "prefLabel", "Sanaston nimi, joka näkyy otsikossa ja hakutuloksissa",
                    "Name of the terminology that is displayed in the header and search results",
                    "Ordlistans namn som visas i rubriken och sökresultaten");

            updateTextAttributePrefLabel(meta, "description", "Kuvaus", "Description", "Beskrivning");
            updateTextAttributeDescription(meta, "description", "Laaja kuvaus sanaston sisällöstä, kohderyhmästä yms.",
                "Description of the terminology and the domain of use",
                "Omfattande beskrivning av ordlistans innehåll, målgrupper m.m.");

            updateTextAttributePrefLabel(meta, "language", "Kieli", "Language", "Språk");
            updateTextAttributeDescription(meta, "language", "Kielet, joilla sanaston sisältöä määritellään",
                    "Languages used to define the terminology", "Språk, på vilka ordlistans innehåll definieras");

            updateTextAttributePrefLabel(meta, "status", "Sanaston tila", "Terminology status", "Ordlistans status");
            updateTextAttributeDescription(meta, "status", "Sanastomäärittelyn valmiusastetta kuvaava tilatieto",
                    "Status of the terminology", "Status som visar i vilken grad ordlistan är färdig");

            updateTextAttributePrefLabel(meta, "contact", "Yhteydenotto", "Contact", "Kontakt");
            updateTextAttributeDescription(meta, "contact",
                    "Palautekanavan kuvaus. Älä käytä henkilökohtaista sähköpostiosoitetta. Suositeltava muoto esimerkiksi: \"Sanastotyöryhmän ylläpito: yllapito@example.org\"",
                    "Description for the feedback channel. Do not use personal email addresses. Preferred form for example: \"Terminology working group: maintain@example.org\"",
                    "Beskrivning av responskanalen. Använd inte en personlig e-postadress. Rekommenderad form, exempelvis: \"Ordlistearbetsgruppens underhåll: underhall@example.org\"");

            updateTextAttributePrefLabel(meta, "priority", "Ensisijainen järjestysnimi", "Priority name", "Prioritetsnamn");

            // reference attributes
            updateReferenceAttributePrefLabel(meta, "contributor", "Sisällöntuottaja", "Contributor",
                    "Innehållsproducent");
            updateReferenceAttributeDescription(meta, "contributor",
                    "Organisaatio, joka vastaa sanaston sisällöstä ja ylläpidosta",
                    "Organization that is responsible for the content and maintenance of the terminology",
                    "Organisationen som ansvarar för ordlistans innehåll och uppdatering");

            updateReferenceAttributePrefLabel(meta, "inGroup", "Tietoalue", "Information domain", "Informationsområde");
            updateReferenceAttributeDescription(meta, "inGroup",
                    "Sanaston aihealue julkisten palvelujen luokituksen mukaan",
                    "Information domain of the terminology according to the classification of public services",
                    "Ordlistans ämnesområde enligt klassificeringen av offentlig service");
        }

        if (domainName.equals("Collection")) {
            meta.updateProperties(PropertyUtil.prefLabel("Käsitevalikoima", "Collection", "Begreppsurval"));

            updateTextAttributePrefLabel(meta, "prefLabel", "Nimi", "Name", "Namn");
            updateTextAttributeDescription(meta, "prefLabel", "Käsitevalikoiman nimi valitsemallasi kielellä",
                    "Name of the collection", "Begreppsurvalets namn på det språk du valt");

            updateTextAttributePrefLabel(meta, "definition", "Määritelmä", "Definition", "Definition");
            updateTextAttributeDescription(meta, "definition",
                    "Kuvaus valikoiman käyttötarkoituksesta ja mitä käsitteitä valikoimaan kuuluu",
                    "Description of the use cases and content of the collection",
                    "Beskrivning av urvalets användningsändamål och vilka begrepp urvalet omfattar");

            // reference attributes
            updateReferenceAttributePrefLabel(meta, "broader", "Jaottelu yläkäsitteen mukaan", "Collection broader",
                    "Uppdelning enligt det överordnade begreppet");
            updateReferenceAttributeDescription(meta, "broader",
                    "Laajempi käsitevalikoima, johon tämä valikoima liittyy",
                    "Broader collection that is referenced by this collection",
                    "Bredare begreppsurval som gäller för detta urval");

            updateReferenceAttributePrefLabel(meta, "member", "Valikoimaan kuuluva käsite", "Member",
                    "Begrepp som ingår i urvalet");
            updateReferenceAttributeDescription(meta, "member", "Käsite, joka on poimittu mukaan valikoimaan",
                    "Concept that is member of this collection", "Begrepp som kompilerats i urvalet");
        }

        if (domainName.equals("Term")) {
            meta.updateProperties(PropertyUtil.prefLabel("Termi", "Term", "Term"));

            updateTextAttributePrefLabel(meta, "prefLabel", "Termin arvo", "Term literal value", "Termens värde");
            updateTextAttributeDescription(meta, "prefLabel", "Termin tekstimuotoinen kuvaus/nimi (merkkijono)",
                    "Literal value of the term", "Termens beskrivning/namn i textform (teckensträng)");

            updateTextAttributePrefLabel(meta, "source", "Lähde", "Source", "Källa");
            updateTextAttributeDescription(meta, "source", "Termin määrittelyssä käytetyt lähteet",
                    "Sources used to define the term", "De källor som använts för att definiera termen");

            updateTextAttributePrefLabel(meta, "scope", "Käyttöala", "Scope", "Användningsområde");
            updateTextAttributeDescription(meta, "scope", "Ala jossa termi on käytössä",
                    "Scope or domain where term is in use", "Område där termen används");

            updateTextAttributePrefLabel(meta, "termStyle", "Termin tyyli", "Term style", "Termens stilart");
            updateTextAttributeDescription(meta, "termStyle", "Tyylilaji (esim. puhekieli)",
                    "Style of the term (for example street language)", "Stilart (t.ex. talspråk)");

            updateTextAttributePrefLabel(meta, "termFamily", "Termin suku", "Term family", "Termens genus");
            updateTextAttributeDescription(meta, "termFamily", "maskuliini/neutri/feminiini",
                    "masculine/neutral/feminine", "maskulinum/neutrum/femininum");

            updateTextAttributePrefLabel(meta, "termConjugation", "Termin luku", "Term conjugation", "Termens numerus");
            updateTextAttributeDescription(meta, "termConjugation", "Yksikkö tai monikko", "Unit or Plural",
                    "Singularis eller pluralis");

            updateTextAttributePrefLabel(meta, "termEquivalency", "Termin vastaavuus", "Term equivalency",
                    "Termens ekvivalens");
            updateTextAttributeDescription(meta, "termEquivalency", "<,>,~", "<,>,~", "<,>,~");

            updateTextAttributePrefLabel(meta, "termInfo", "Termin lisätieto", "Term info",
                    "Tilläggsuppgifter om termen");
            updateTextAttributeDescription(meta, "termInfo", "Lisätietoa termin käytöstä",
                    "Additional information about the term", "Mer information om hur termen används");

            updateTextAttributePrefLabel(meta, "wordClass", "Sanaluokka", "Word class", "Ordklass");
            updateTextAttributeDescription(meta, "wordClass",
                    "Merkitään, jos termi on eri sanaluokasta kuin muunkieliset termit",
                    "Word class is used if term is from different class than terms in other language",
                    "Anges om termen har en annan ordklass än på de andra språken");

            updateTextAttributePrefLabel(meta, "termHomographNumber", "Homonyymin järjestysnumero", "Homograph number",
                    "Homonymens ordningsnummer");
            updateTextAttributeDescription(meta, "termHomographNumber", "Numero joka kuvaa homonyymin järjestyksen.",
                    "A number indicating the position of the term within a sequenceof homographs",
                    "Ett tal som beskriver homonymens ordning");

            updateTextAttributePrefLabel(meta, "editorialNote", "Ylläpitäjän muistiinpano", "Editorial note",
                    "Administratörens anteckning");
            updateTextAttributeDescription(meta, "editorialNote", "Ylläpitäjille tai kääntäjille tarkoitettu huomio",
                    "Note that is intended for translators or maintainers",
                    "Anmärkning avsedd för administratörer eller översättare");

            updateTextAttributePrefLabel(meta, "draftComment", "Luonnosvaiheen kommentti", "Draft comment",
                    "Utkastskommentar");
            updateTextAttributeDescription(meta, "draftComment", "Luonnosvaiheessa näkyväksi tarkoitettu kommentti",
                    "Comment that is intended to be shown only in draft stage",
                    "En kommentar som visas i utkastsskedet");

            updateTextAttributePrefLabel(meta, "historyNote", "Käytön historiatieto", "History note",
                    "Användningshistorik");
            updateTextAttributeDescription(meta, "historyNote", "Termin aiempi merkitys tai käyttö",
                    "Description about prior use or history of the term",
                    "Termens tidigare betydelse eller användning");

            updateTextAttributePrefLabel(meta, "changeNote", "Muutoshistoriatieto", "Change note", "Ändringshistorik");
            updateTextAttributeDescription(meta, "changeNote", "Merkintä termiin tehdystä yksittäisestä muutoksesta",
                    "Note about changes made to the term", "Uppgift om enskilda ändringar som gjorts i termen");

            updateTextAttributePrefLabel(meta, "status", "Termin tila", "Term status", "Termens status");
            updateTextAttributeDescription(meta, "status", "Termin valmiusastetta kuvaava tila", "Status of the term",
                    "Status som beskriver i vilken grad termen är färdig");
        }

        if (domainName.equals("Concept")) {
            meta.updateProperties(PropertyUtil.prefLabel("Käsite", "Concept", "Begrepp"));

            updateTextAttributePrefLabel(meta, "definition", "Määritelmä", "Definition", "Definition");
            updateTextAttributeDescription(meta, "definition",
                    "Kuvaa käsitteen sisällön ja erottaa sen muista käsitteistä",
                    "Defines the concept in a way that it is understandable in the the context of the terminology and other concepts",
                    "Beskriver begreppets innehåll och separerar det från övriga begrepp");

            updateTextAttributePrefLabel(meta, "note", "Huomautus", "Note", "Anmärkning");
            updateTextAttributeDescription(meta, "note", "Käsitteen käyttöön liittyviä yleisiä huomioita",
                    "Common notes about the use of the concept", "Allmän anmärkning om hur begreppet används");

            updateTextAttributePrefLabel(meta, "editorialNote", "Ylläpitäjän muistiinpano", "Editorial note",
                    "Administratörens anteckning");
            updateTextAttributeDescription(meta, "editorialNote", "Ylläpitäjille tai kääntäjille tarkoitettu huomio",
                    "Note that is intended for translators or maintainers",
                    "Anmärkning avsedd för administratörer eller översättare");

            updateTextAttributePrefLabel(meta, "example", "Käyttöesimerkki", "Example", "Användningsexempel");
            updateTextAttributeDescription(meta, "example", "Esimerkki käsitteen käytöstä",
                    "Example using the concept in a context", "Exempel på hur begreppet används");

            updateTextAttributePrefLabel(meta, "conceptScope", "Käyttöala", "Concept scope", "Användningsområde");
            updateTextAttributeDescription(meta, "conceptScope", "Ala, jossa käsite on käytössä",
                    "Scope or domain where concept is in use", "Område där begreppet används");

            updateTextAttributePrefLabel(meta, "conceptClass", "Käsitteen luokka", "Concept class", "Begreppets klass");
            updateTextAttributeDescription(meta, "conceptClass",
                    "Sanastokohtainen luokitus, jolla voidaan ryhmitellä käsitteitä",
                    "Terminology specific classification for concept categorisation",
                    "Klassificering enligt ordlista för gruppering av begrepp");

            updateTextAttributePrefLabel(meta, "wordClass", "Sanaluokka", "Word class", "Ordklass");
            updateTextAttributeDescription(meta, "wordClass",
                    "Merkitään tarvittaessa käsitteelle, jos se on adjektiivi tai verbi",
                    "Word class is used if concept is adjective or verb",
                    "Anges vid behov för ett begrepp, om det är ett adjektiv eller ett verb");

            updateTextAttributePrefLabel(meta, "changeNote", "Muutoshistoriatieto", "Change note", "Ändringshistorik");
            updateTextAttributeDescription(meta, "changeNote",
                    "Merkintä käsitteeseen tehdystä yksittäisestä muutoksesta",
                    "Note about changes made to the concept", "Uppgift om enskilda ändringar som gjorts i begreppet");

            updateTextAttributePrefLabel(meta, "historyNote", "Käytön historiatieto", "History note",
                    "Användningshistorik");
            updateTextAttributeDescription(meta, "historyNote", "Käsitteen aiempi merkitys tai käyttö",
                    "Notes about the historical use of the concept", "Begreppets tidigare betydelse eller användning");

            updateTextAttributePrefLabel(meta, "status", "Käsitteen tila", "Concept status", "Begreppets status");
            updateTextAttributeDescription(meta, "status", "Käsitteen valmiusastetta kuvaava tila",
                    "Status of the concept", "Status som beskriver i vilken grad begreppet är färdigt");

            updateTextAttributePrefLabel(meta, "notation", "Systemaattinen merkintätapa", "Notation",
                    "Systematiskt anteckningssätt");
            updateTextAttributeDescription(meta, "notation",
                    "Merkintä, jolla käsitteet voidaan jäsentää eri järjestykseen tai joukkoihin",
                    "Systematic notation that is used to order the concepts",
                    "Ett anteckningssätt för att strukturera begreppen i olika ordningar eller grupper");

            updateTextAttributePrefLabel(meta, "source", "Lähde", "Source", "Källa");
            updateTextAttributeDescription(meta, "source", "Käsitteen määrittelyssä käytetyt lähteet",
                    "Sources used to define the concept", "De källor som använts för att definiera begreppet");

            // reference attributes
            updateReferenceAttributePrefLabel(meta, "prefLabelXl", "Suositettava termi", "Preferred term",
                    "Rekommenderad term");
            updateReferenceAttributeDescription(meta, "prefLabelXl",
                    "Termi, joka on sopivin kuvaamaan kyseistä käsitettä",
                    "Preferred term that is assigned for the concept", "Den term som bäst beskriver begreppet i fråga");

            updateReferenceAttributePrefLabel(meta, "altLabelXl", "Synonyymi", "Synonym", "Synonym");
            updateReferenceAttributeDescription(meta, "altLabelXl",
                    "Termi jolla on (lähes) sama merkitys kuin tällä termillä", "Synonym for the term",
                    "En term som har (nästan) samma definition som denna term");

            updateReferenceAttributePrefLabel(meta, "notRecommendedSynonym", "Ei-suositeltava synonyymi",
                    "Non-recommended synonym", "Icke-rekommenderad synonym");
            updateReferenceAttributeDescription(meta, "notRecommendedSynonym", "Termi, joka ei ole kielellisesti hyvä",
                    "Term that is not recommended for use", "En term som inte är språkligt bra");

            updateReferenceAttributePrefLabel(meta, "hiddenTerm", "Ohjaustermi", "Hidden term", "Hänvisningsterm");
            updateReferenceAttributeDescription(meta, "hiddenTerm",
                    "Termi, jolla halutaan ohjata käyttämään toista termiä",
                    "Hidden term that is used to direct the users to use the preferred term",
                    "En term, med vilken man vill styra till att använda en annan term");

            updateReferenceAttributePrefLabel(meta, "broader", "Hierarkkinen yläkäsite", "Broader concept",
                    "Överordnat begrepp");
            updateReferenceAttributeDescription(meta, "broader", "Laajempi käsite, johon tämä käsite liittyy",
                    "Concept that is conceptually braoder than this concept",
                    "Bredare begrepp som detta begrepp anknyter till");

            updateReferenceAttributePrefLabel(meta, "narrower", "Hierarkkinen alakäsite", "Narrower concept",
                    "Underordnat begrepp");
            updateReferenceAttributeDescription(meta, "narrower",
                    "Käsite, joka on hierarkkisessa suhteessa tähän käsitteeseen ja jonka sisältöön kuuluu tämän käsitteen sisältö",
                    "Concept that is in hierarchical relation with this concept and contains semantic meaning of this concept",
                    "Ett begrepp som står i hierarkisk relation till detta begrepp och vars intension (innehåll) inkluderar intensionen av detta begreppet");

            updateReferenceAttributePrefLabel(meta, "closeMatch", "Lähes vastaava käsite toisessa sanastossa",
                    "Close match in other vocabulary", "Ett nästan motsvarande begrepp i en annan ordlista");
            updateReferenceAttributeDescription(meta, "closeMatch",
                    "Osittain tätä käsitettä vastaava käsite, mutta niiden käyttöala on eri",
                    "Concept that is partly the same but domain or scope is different",
                    "Ett begrepp som delvis motsvarar detta begrepp, men med annat användningsområde");

            updateReferenceAttributePrefLabel(meta, "related", "Liittyvä käsite", "Related concept",
                    "Anknytande begrepp");
            updateReferenceAttributeDescription(meta, "related", "Käsite joka liittyy tähän käsitteeseen",
                    "Concept that is related to this concept", "Ett begrepp som anknyter till detta begrepp");

            updateReferenceAttributePrefLabel(meta, "isPartOf", "Koostumussuhteinen yläkäsite", "Is part of concept",
                    "Helhetsbegrepp");
            updateReferenceAttributeDescription(meta, "isPartOf", "Käsite, johon tämä käsite kuuluu (on osa)",
                    "Concept that is hierarchically related to this concept", "Ett begrepp som omfattar detta begrepp");

            updateReferenceAttributePrefLabel(meta, "hasPart", "Koostumussuhteinen alakäsite", "Has part concept",
                    "Delbegrepp");
            updateReferenceAttributeDescription(meta, "hasPart",
                    "Koostumussuhteinen käsite, joka vastaa kokonaisuuden osaa",
                    "Narrower concept that is part of this concept",
                    "Ett begrepp i en partitiv begreppsrelation som avser en del av helheten");

            updateReferenceAttributePrefLabel(meta, "relatedMatch", "Liittyvä käsite toisessa sanastossa",
                    "Related concept in other vocabulary", "Anknytande begrepp i en annan ordlista");
            updateReferenceAttributeDescription(meta, "relatedMatch",
                    "Käsite, joka liittyy tähän käsitteeseen, mutta eri sanastossa",
                    "Related concept that is in different terminology",
                    "Ett begrepp som anknyter till detta begrepp, men finns i en annan ordlista");

            updateReferenceAttributePrefLabel(meta, "exactMatch", "Vastaava käsite toisessa sanastossa",
                    "Matching concept in other vocabulary", "Motsvarande begrepp i en annan ordlista");
            updateReferenceAttributeDescription(meta, "exactMatch",
                    "Toisessa sanastossa sijaitseva käsite, jota voidaan käyttää tämän käsitteen sijaan",
                    "Exact copy of this concept in different terminology",
                    "Ett begrepp i en annan ordlista som kan användas istället för detta begrepp");

            updateReferenceAttributePrefLabel(meta, "searchTerm", "Hakutermi", "Search term", "Sökterm");
            updateReferenceAttributeDescription(meta, "searchTerm",
                    "Termi, jolla käyttäjä ohjataan katsomaan tiettyyn käsitteeseen liitettyjä tietoja",
                    "Term not designating the concept, but which may be useful for search or reference purposes",
                    "En term som styr användare att se uppgifter gällande ett visst begrepp");
        }
        return rv;
    }

    /**
     * Update individual preflabel for text-attributes
     * 
     * @param meta
     * @param attributeName
     * @param description
     * @return
     */
    boolean updateTextAttributePrefLabel(MetaNode meta, String attributeName, String fi, String en, String sv) {
        boolean rv = false;
        // Print textAttributes
        List<AttributeMeta> ta = meta.getTextAttributes().stream().filter(item -> item.getId().equals(attributeName))
                .collect(Collectors.toList());
        if (!ta.isEmpty()) {
            if (ta.size() > 1) {
                System.err.println("Error, several " + attributeName + " attributes in same node ");
            } else {
                AttributeMeta att = ta.get(0);
                att.updateProperties(PropertyUtil.merge(att.getProperties(), PropertyUtil.prefLabel(fi, en, sv)));
                rv = true;
            }
        }
        return rv;
    }

    /**
     * Update individual description for text-attributes
     * 
     * @param meta
     * @param attributeName
     * @param description
     * @return
     */
    boolean updateTextAttributeDescription(MetaNode meta, String attributeName, String fi, String en, String sv) {
        boolean rv = false;
        // Print textAttributes
        List<AttributeMeta> ta = meta.getTextAttributes().stream().filter(item -> item.getId().equals(attributeName))
                .collect(Collectors.toList());
        if (!ta.isEmpty()) {
            if (ta.size() > 1) {
                System.err.println("Error, several " + attributeName + " attributes in same node ");
            } else {
                AttributeMeta att = ta.get(0);
                att.updateProperties(PropertyUtil.merge(att.getProperties(), PropertyUtil.description(fi, en, sv)));
                rv = true;
            }
        }
        return rv;
    }

    /**
     * Update individual prefLabels for reference-attributes
     * 
     * @param meta
     * @param attributeName
     * @param description
     * @return
     */
    boolean updateReferenceAttributePrefLabel(MetaNode meta, String attributeName, String fi, String en, String sv) {
        boolean rv = false;
        // Print textAttributes
        List<ReferenceMeta> ra = meta.getReferenceAttributes().stream()
                .filter(item -> item.getId().equals(attributeName)).collect(Collectors.toList());
        if (!ra.isEmpty()) {
            if (ra.size() > 1) {
                System.err.println("Error, several " + attributeName + " attributes in same node ");
            } else {
                ReferenceMeta att = ra.get(0);
                // check if description exist and add new one
                att.updateProperties(PropertyUtil.merge(att.getProperties(), PropertyUtil.prefLabel(fi, en, sv)));
                rv = true;
            }
        }
        return rv;
    }

    /**
     * Update individual descriptions for reference-attributes
     * 
     * @param meta
     * @param attributeName
     * @param description
     * @return
     */
    boolean updateReferenceAttributeDescription(MetaNode meta, String attributeName, String fi, String en, String sv) {
        boolean rv = false;
        // Print textAttributes
        List<ReferenceMeta> ra = meta.getReferenceAttributes().stream()
                .filter(item -> item.getId().equals(attributeName)).collect(Collectors.toList());
        if (!ra.isEmpty()) {
            if (ra.size() > 1) {
                System.err.println("Error, several " + attributeName + " attributes in same node ");
            } else {
                ReferenceMeta att = ra.get(0);
                // check if description exist and add new one
                att.updateProperties(PropertyUtil.merge(att.getProperties(), PropertyUtil.description(fi, en, sv)));
                rv = true;
            }
        }
        return rv;
    }
}
