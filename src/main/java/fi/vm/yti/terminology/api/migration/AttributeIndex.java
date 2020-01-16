package fi.vm.yti.terminology.api.migration;

import org.jetbrains.annotations.NotNull;

import fi.vm.yti.terminology.api.model.termed.AttributeMeta;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import static fi.vm.yti.terminology.api.migration.PropertyUtil.merge;
import static fi.vm.yti.terminology.api.migration.PropertyUtil.type;
import static java.util.Collections.emptyMap;

public final class AttributeIndex {

    @NotNull
    public static AttributeMeta prefLabel(TypeId domain,
                                          long index,
                                          String fi,
                                          String en) {

        return new AttributeMeta(
            "prefLabel",
            "http://www.w3.org/2004/02/skos/core#prefLabel",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(fi, en),
                type("localizable:single,required")
            )
        );
    }

    @NotNull
    public static AttributeMeta prefLabelXl(TypeId domain,
                                            long index) {

        return new AttributeMeta(
            "prefLabel",
            "http://www.w3.org/2008/05/skos-xl#literalForm",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Termin arvo",
                    "Term literal value"
                ),
                type("localizable:single,required")
            )
        );
    }

    @NotNull
    public static AttributeMeta altLabel(TypeId domain,
                                         long index) {

        return new AttributeMeta(
            "altLabel",
            "http://www.w3.org/2004/02/skos/core#altLabel",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Vaihtoehtoinen termi",
                    "Alternative label"
                ),
                type("localizable:multiple")
            )
        );
    }

    @NotNull
    public static AttributeMeta hiddenLabel(TypeId domain,
                                            long index) {

        return new AttributeMeta(
            "hiddenLabel",
            "http://www.w3.org/2004/02/skos/core#hiddenLabel",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Piilotettu termi",
                    "Hidden label"
                ),
                type("localizable:multiple")
            )
        );
    }

    @NotNull
    public static AttributeMeta definition(TypeId domain,
                                           long index,
                                           boolean linkable) {
        return new AttributeMeta(
            "definition",
            "http://www.w3.org/2004/02/skos/core#definition",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Määritelmä",
                    "Definition"
                ),
                type("localizable:multiple,area" + (linkable ? ",xml" : ""))
            )
        );
    }

    @NotNull
    public static AttributeMeta note(TypeId domain,
                                     long index,
                                     boolean linkable) {
        return new AttributeMeta(
            "note",
            "http://www.w3.org/2004/02/skos/core#note",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Huomautus",
                    "Note"
                ),
                type("localizable:multiple,area" + (linkable ? ",xml" : ""))
            )
        );
    }

    @NotNull
    public static AttributeMeta description(TypeId domain,
                                            long index) {
        return new AttributeMeta(
            "description",
            "http://purl.org/dc/terms/description",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Kuvaus",
                    "Description"
                ),
                type("localizable:area")
            )
        );
    }

    @NotNull
    public static AttributeMeta language(TypeId domain,
                                         long index) {
        return new AttributeMeta(
            "language",
            "http://purl.org/dc/terms/language",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Kieli",
                    "Language"
                ),
                type("language:multiple")
            )
        );
    }

    @NotNull
    public static AttributeMeta status(TypeId domain,
                                       long index,
                                       String fi,
                                       String en) {
        return new AttributeMeta(
            "status",
            "http://www.w3.org/2003/06/sw-vocab-status/ns#term_status",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(fi, en),
                type("status")
            )
        );
    }

    @NotNull
    public static AttributeMeta draftComment(TypeId domain,
                                             long index) {
        return new AttributeMeta(
            "draftComment",
            "http://www.w3.org/2000/01/rdf-schema#comment",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Luonnosvaiheen kommentti",
                    "Draft comment"
                ),
                type("string:single,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta source(TypeId domain,
                                       long index) {
        return new AttributeMeta(
            "source",
            "http://purl.org/dc/terms/source",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Lähde",
                    "Source"
                ),
                type("string:single,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta scope(TypeId domain,
                                      long index) {
        return new AttributeMeta(
            "scope",
            "http://uri.suomi.fi/datamodel/ns/iow#scope",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Käyttöala",
                    "Scope"
                ),
                type("string:single,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta conceptScope(TypeId domain,
                                             long index) {
        return new AttributeMeta(
            "conceptScope",
            "http://uri.suomi.fi/datamodel/ns/iow#conceptScope",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Käyttöala",
                    "Concept scope"
                ),
                type("string:single,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta conceptClass(TypeId domain,
                                             long index) {
        return new AttributeMeta(
            "conceptClass",
            "http://uri.suomi.fi/datamodel/ns/iow#conceptClass",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Käsitteen luokka",
                    "Concept class"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta priority(TypeId domain,
                                         long index) {
        return new AttributeMeta(
            "priority",
            "http://uri.suomi.fi/datamodel/ns/iow#priority",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Ensisijainen järjestysnimi",
                    "Priority name"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta scopeNote(TypeId domain,
                                          long index) {
        return new AttributeMeta(
            "scopeNote",
            "http://www.w3.org/2004/02/skos/core#scopeNote",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Käyttöala",
                    "Scope note"
                ),
                type("string:single,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta example(TypeId domain,
                                        long index) {
        return new AttributeMeta(
            "example",
            "http://www.w3.org/2004/02/skos/core#example",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Käyttöesimerkki",
                    "Example"
                ),
                type("localizable:multiple,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta editorialNote(TypeId domain,
                                              long index) {
        return new AttributeMeta(
            "editorialNote",
            "http://www.w3.org/2004/02/skos/core#editorialNote",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Ylläpitäjän muistiinpano",
                    "Editorial note"
                ),
                type("string:multiple,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta historyNote(TypeId domain,
                                            long index) {
        return new AttributeMeta(
            "historyNote",
            "http://www.w3.org/2004/02/skos/core#historyNote",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Käytön historiatieto",
                    "History note"
                ),
                type("string:single,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta changeNote(TypeId domain,
                                           long index) {
        return new AttributeMeta(
            "changeNote",
            "http://www.w3.org/2004/02/skos/core#changeNote",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Muutoshistoriatieto",
                    "Change note"
                ),
                type("string:single,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta notation(TypeId domain,
                                         long index,
                                         String fi,
                                         String en) {
        return new AttributeMeta(
            "notation",
            "http://www.w3.org/2004/02/skos/core#notation",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(fi, en),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta order(TypeId domain,
                                      long index) {
        return new AttributeMeta(
            "order",
            "http://www.w3.org/ns/shacl#order",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Järjestysnumero",
                    "Order index"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta conceptWordClass(TypeId domain,
                                                 long index) {
        return new AttributeMeta(
            "wordClass",
            "http://uri.suomi.fi/datamodel/ns/st#conceptWordClass",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Sanaluokka",
                    "Word class"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta termWordClass(TypeId domain,
                                              long index) {
        return new AttributeMeta(
            "wordClass",
            "http://uri.suomi.fi/datamodel/ns/st#termWordClass",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Sanaluokka",
                    "Word class"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta termStyle(TypeId domain,
                                          long index) {
        return new AttributeMeta(
            "termStyle",
            "http://uri.suomi.fi/datamodel/ns/st#termStyle",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Termin tyyli",
                    "Term style"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta termFamily(TypeId domain,
                                           long index) {
        return new AttributeMeta(
            "termFamily",
            "http://uri.suomi.fi/datamodel/ns/st#termFamily",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Termin suku",
                    "Term family"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta termConjugation(TypeId domain,
                                                long index) {
        return new AttributeMeta(
            "termConjugation",
            "http://uri.suomi.fi/datamodel/ns/st#termConjugation",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Termin luku",
                    "Term conjugation"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta termEquivalency(TypeId domain,
                                                long index) {
        return new AttributeMeta(
            "termEquivalency",
            "http://uri.suomi.fi/datamodel/ns/st#termEquivalency",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Termin vastaavuus",
                    "Term equivalency"
                ),
                type("string:single")
            )
        );
    }

    // Termin lisätieto / Term info.  Additional information about the term / Lisätietoa termin käytöstä
    @NotNull
    public static AttributeMeta termInfo(TypeId domain,
                                         long index) {
        return new AttributeMeta(
            "termInfo",
            "http://uri.suomi.fi/datamodel/ns/st#termInfo",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Termin lisätieto",
                    "Term info"
                ),
                type("string:single")
            )
        );
    }

    // homonyymin järjestysnumero / homograph number.  Numero joka kuvaa homonyymin järjestyksen. A number indicating the position of the term within a sequence
    //of homographs
    @NotNull
    public static AttributeMeta termHomographNumber(TypeId domain,
                                                    long index) {
        return new AttributeMeta(
            "termHomographNumber",
            "http://uri.suomi.fi/datamodel/ns/st#termHomographNumber",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "homonyymin järjestysnumero",
                    "Homograph number"
                ),
                type("string:single")
            )
        );
    }

    @NotNull
    public static AttributeMeta contact(TypeId domain,
                                        long index) {
        return new AttributeMeta(
            "contact",
            "http://uri.suomi.fi/datamodel/ns/st#contact",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Yhteydenotto",
                    "Contact"
                ),
                type("string:single,area")
            )
        );
    }

    @NotNull
    public static AttributeMeta usedInScheme(TypeId domain,
                                             long index) {
        return new AttributeMeta(
            "usedInScheme",
            "http://uri.suomi.fi/datamodel/ns/st#usedInScheme",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Käytössä sanastossa",
                    "Used in scheme"
                )
            )
        );
    }

    @NotNull
    public static AttributeMeta definedInScheme(TypeId domain,
                                                long index) {
        return new AttributeMeta(
            "definedInScheme",
            "http://www.w3.org/2004/02/skos/core#inScheme",
            index,
            domain,
            emptyMap(),
            merge(
                PropertyUtil.prefLabel(
                    "Määritelty sanastossa",
                    "Defined in scheme"
                )
            )
        );
    }

    // prevent construction
    private AttributeIndex() {
    }
}
