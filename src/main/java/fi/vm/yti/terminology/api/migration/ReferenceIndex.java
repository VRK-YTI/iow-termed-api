package fi.vm.yti.terminology.api.migration;

import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.ReferenceMeta;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import org.jetbrains.annotations.NotNull;

import static fi.vm.yti.terminology.api.migration.DomainIndex.GROUP_DOMAIN;
import static fi.vm.yti.terminology.api.migration.DomainIndex.ORGANIZATION_DOMAIN;
import static fi.vm.yti.terminology.api.migration.PropertyUtil.*;
import static java.util.Collections.emptyMap;

public final class ReferenceIndex {

    private static final TypeId termDomainFromConceptDomain(final TypeId conceptDomain) {
        return new TypeId(NodeType.Term, conceptDomain.getGraph());
    }

    // One graph, so use  hardCoded value
    private static final TypeId terminologyDomainFromConceptDomain(final TypeId conceptDomain) {
        return new TypeId(NodeType.TerminologicalVocabulary, conceptDomain.getGraph());
    }

    @NotNull
    public static ReferenceMeta contributor(final TypeId domain, final long index) {
        return new ReferenceMeta(
                ORGANIZATION_DOMAIN,
                "contributor",
                "http://purl.org/dc/terms/contributor",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Sisällöntuottaja",
                        "Contributor"
                )
        );
    }

    @NotNull
    public static ReferenceMeta group(final TypeId domain, final long index) {
        return new ReferenceMeta(
                GROUP_DOMAIN,
                "inGroup",
                "http://purl.org/dc/terms/isPartOf",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Luokitus",
                        "Classification"
                )
        );
    }

    @NotNull
    public static ReferenceMeta broader(final TypeId domain, final long index, final String fi, final String en) {
        return new ReferenceMeta(
                domain,
                "broader",
                "http://www.w3.org/2004/02/skos/core#broader",
                index,
                domain,
                emptyMap(),
                prefLabel(fi, en)
        );
    }

    @NotNull
    public static ReferenceMeta narrower(final TypeId domain, final long index, final String fi, final String en) {
        return new ReferenceMeta(
                domain,
                "narrower",
                "http://www.w3.org/2004/02/skos/core#narrower",
                index,
                domain,
                emptyMap(),
                prefLabel(fi, en)
        );
    }

    @NotNull
    public static ReferenceMeta relatedConcept(final TypeId domain, final long index) {
        return new ReferenceMeta(
                domain,
                "related",
                "http://www.w3.org/2004/02/skos/core#related",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Liittyvä käsite",
                        "Related concept"
                )
        );
    }

    @NotNull
    public static ReferenceMeta partOfConcept(final TypeId domain, final long index) {
        return new ReferenceMeta(
                domain,
                "isPartOf",
                "http://purl.org/dc/terms/partOf",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Koostumussuhteinen yläkäsite",
                        "Is part of concept"
                )
        );
    }

    @NotNull
    public static ReferenceMeta hasPartConcept(final TypeId domain, final long index) {
        return new ReferenceMeta(
                domain,
                "hasPart",
                "http://purl.org/dc/terms/hasPart",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Koostumussuhteinen alakäsite",
                        "Has part concept"
                )
        );
    }

    @NotNull
    public static ReferenceMeta relatedMatch(final TypeId domain, final TypeId externalLinkDomain, final long index) {
        return new ReferenceMeta(
                externalLinkDomain,
                "relatedMatch",
                "http://www.w3.org/2004/02/skos/core#relatedMatch",
                index,
                domain,
                emptyMap(),
                merge(
                        prefLabel(
                                "Liittyvä käsite toisessa sanastossa",
                                "Related concept in other vocabulary"
                        ),
                        type("link")
                )
        );
    }
    /**
     * Replace original with local reference
     * @param domain
     * @param index
     * @return
     */
    @NotNull
    public static ReferenceMeta relatedMatch(final TypeId domain, final long index) {
        return new ReferenceMeta(
                domain,
                "relatedMatch",
                "http://www.w3.org/2004/02/skos/core#relatedMatch",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Liittyvä käsite toisessa terminologiassa",
                        "Related concept in other terminology"
                )
        );
    }

    @NotNull
    public static ReferenceMeta exactMatch(final TypeId domain, final TypeId externalLinkDomain, final long index) {
        return new ReferenceMeta(
                externalLinkDomain,
                "exactMatch",
                "http://www.w3.org/2004/02/skos/core#exactMatch",
                index,
                domain,
                emptyMap(),
                merge(
                        prefLabel(
                                "Vastaava käsite toisessa sanastossa",
                                "Related concept from other vocabulary"
                        ),
                        type("link")
                )
        );
    }
    /**
     * replace original with local links
     * @param domain
     * @param index
     * @return
     */
    @NotNull
    public static ReferenceMeta exactMatch(final TypeId domain, final long index) {
        return new ReferenceMeta(
                domain,
                "exactMatch",
                "http://www.w3.org/2004/02/skos/core#exactMatch",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Vastaava käsite",
                        "Related concept"
                )
        );
    }

    @NotNull
    public static ReferenceMeta closeMatch(final TypeId domain, final TypeId externalLinkDomain, final long index) {
        return new ReferenceMeta(
                externalLinkDomain,
                "closeMatch",
                "http://www.w3.org/2004/02/skos/core#closeMatch",
                index,
                domain,
                emptyMap(),
                merge(
                        prefLabel(
                                "Lähes vastaava käsite toisessa sanastossa",
                                "Close match in other vocabulary"
                        ),
                        type("link")
                )
        );
    }
    /**
     * Replace original with intelnal links
     * @param domain
     * @param externalLinkDomain
     * @param index
     * @return
     */
    @NotNull
    public static ReferenceMeta closeMatch(final TypeId domain, final long index) {
        return new ReferenceMeta(
                domain,
                "closeMatch",
                "http://www.w3.org/2004/02/skos/core#closeMatch",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Lähes vastaava käsite",
                        "Close match"
                )
        );
    }

    @NotNull
    public static ReferenceMeta member(final TypeId domain, final TypeId targetDomain, final long index) {
        return new ReferenceMeta(
                targetDomain,
                "member",
                "http://www.w3.org/2004/02/skos/core#member",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Valikoimaan kuuluva käsite",
                        "Member"
                )
        );
    }

    @NotNull
    public static ReferenceMeta prefLabelXl(final TypeId domain, final long index) {
        return new ReferenceMeta(
                termDomainFromConceptDomain(domain),
                "prefLabelXl",
                "http://www.w3.org/2008/05/skos-xl#prefLabel",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Suositettava termi",
                        "Preferred term"
                )
        );
    }

    @NotNull
    public static ReferenceMeta altLabelXl(final TypeId domain, final long index) {
        return new ReferenceMeta(
                termDomainFromConceptDomain(domain),
                "altLabelXl",
                "http://uri.suomi.fi/datamodel/ns/st#synonym",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Synonyymi",
                        "Synonym"
                )
        );
    }

    @NotNull
    public static ReferenceMeta notRecommendedSynonym(final TypeId domain, final long index) {
        return new ReferenceMeta(
                termDomainFromConceptDomain(domain),
                "notRecommendedSynonym",
                "http://uri.suomi.fi/datamodel/ns/st#notRecommendedSynonym",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Ei-suositeltava synonyymi",
                        "Non-recommended synonym"
                )
        );
    }

    @NotNull
    public static ReferenceMeta hiddenTerm(final TypeId domain, final long index) {
        return new ReferenceMeta(
                termDomainFromConceptDomain(domain),
                "hiddenTerm",
                "http://uri.suomi.fi/datamodel/ns/st#hiddenTerm",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Ohjaustermi",
                        "Hidden term"
                )
        );
    }

    @NotNull
    public static ReferenceMeta searchTerm(final TypeId domain, final long index) {
        return new ReferenceMeta(
                termDomainFromConceptDomain(domain),
                "searchTerm",
                "http://uri.suomi.fi/datamodel/ns/st#searchTerm",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Hakutermi",
                        "Search term"
                )
        );
    }


    @NotNull
    public static ReferenceMeta usedInScheme(final TypeId domain, final long index) {
        return new ReferenceMeta(
                terminologyDomainFromConceptDomain(domain), // range or target, refers to terminology node        
                "usedInScheme",
                "http://uri.suomi.fi/datamodel/ns/st#usedInScheme",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Käytössä sanastossa",
                        "Used in scheme"
                )
        );
    }

    @NotNull
    public static ReferenceMeta definedInScheme(final TypeId domain, final long index) {
        return new ReferenceMeta(
                terminologyDomainFromConceptDomain(domain), // range or target, refers to terminology node        
                "definedInScheme",
                "http://www.w3.org/2004/02/skos/core#inScheme",
                index,
                domain,
                emptyMap(),
                prefLabel(
                        "Määritelty sanastossa",
                        "Defined in scheme"
                )
        );
    }    
    
    // prevent construction
    private ReferenceIndex() {
    }
}
