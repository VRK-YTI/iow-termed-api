package fi.vm.yti.terminology.api.migration.task;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.AttributeIndex;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;
import org.springframework.stereotype.Component;


/**
 * Migration for YTI-1159, New common metamodel
 * ToDo:
 *
 * * LinkNode poisto ja siihen liittyvät assiaatiot
 *   + Poistetaan createTerminologicalConceptLinkMeta()
 *   + Poistetaan ReferenceIndex.relatedMatch
 *   + Muutetaan viittaamaan Concept-nodeen: ReferenceIndex.exactMatch ja ReferenceIndex.closeMatch
 *
 *
 * Concept nodelle usedInScheme joka viittaa sanastonodeen. Syntyy silloin kun käsite on "lainattu" toiseen sanastoon
 *
 * PrefLabel:
 * "Käytössä sanastossa" / "Used in scheme"
 *
 * Id: usedInScheme
 *
 * URI: skos:inScheme
 *
 *
 * * Concept nodelle definedInScheme joka viittaa sanastonodeen. Syntyy käsitettä luotaessa
 *
 * PrefLabel: "Määritelty sanastossa" / "Defined in scheme"
 *
 * Id: definedInScheme
 *
 * URI: skos:inScheme
 */
@Component
public class V9_NewCommonMetamodelUpdate implements MigrationTask {

    private final MigrationService migrationService;

    V9_NewCommonMetamodelUpdate(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        migrationService.updateTypes(VocabularyNodeType.TerminologicalVocabulary, meta -> {
            // Go  through text-attributes and add descriptions to known ids
//            updateTypes(meta);
        });
    }

    /**
     * Add new used- and definedInScheme attributes
     * @param meta MetaNode to be updated
     * @return true if updated
     */
    boolean updateTypes(MetaNode meta){
        boolean rv = false;;
        String domainName=meta.getDomain().getId().name();
        System.out.println("Migration v9 updateTypes "+domainName);
        if (meta.isOfType(NodeType.Concept)) {
            // Add new used- and definedInScheme YTI-1159
            TypeId domain = meta.getDomain();
            meta.addAttribute(AttributeIndex.usedInScheme(domain, 15));
            meta.addAttribute(AttributeIndex.definedInScheme(domain, 16));
            // Remove reference-attribute relatedMatch because ConceptLinkMeta is retired
            meta.removeReference("relatedMatch");
            rv = true;
        }
        return rv;
    }

}
