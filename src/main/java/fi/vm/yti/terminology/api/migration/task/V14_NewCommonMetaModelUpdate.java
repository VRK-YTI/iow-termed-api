package fi.vm.yti.terminology.api.migration.task;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.AttributeIndex;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.migration.DomainIndex;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;
import fi.vm.yti.terminology.api.util.JsonUtils;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Migration for YTI-1159, New common metamodel ToDo:
 *
 * * LinkNode poisto ja siihen liittyvät assiaatiot + Poistetaan
 * createTerminologicalConceptLinkMeta() + Poistetaan
 * ReferenceIndex.relatedMatch + Muutetaan viittaamaan Concept-nodeen:
 * ReferenceIndex.exactMatch ja ReferenceIndex.closeMatch
 *
 *
 * Concept nodelle usedInScheme joka viittaa sanastonodeen. Syntyy silloin kun
 * käsite on "lainattu" toiseen sanastoon
 *
 * PrefLabel: "Käytössä sanastossa" / "Used in scheme"
 *
 * Id: usedInScheme
 *
 * URI: skos:inScheme
 *
 *
 * * Concept nodelle definedInScheme joka viittaa sanastonodeen. Syntyy
 * käsitettä luotaessa
 *
 * PrefLabel: "Määritelty sanastossa" / "Defined in scheme"
 *
 * Id: definedInScheme
 *
 * URI: skos:inScheme
 * 
 * Poistetaan asiasanastot 
 */
@Component
public class V14_NewCommonMetaModelUpdate implements MigrationTask {

    private final MigrationService migrationService;

    V14_NewCommonMetaModelUpdate(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        migrationService.updateTypes(VocabularyNodeType.TerminologicalVocabulary, meta -> {
            // Go through text-attributes and add descriptions to known ids
            updateTypes(meta);
        });
        // Delete concept-link YTI-330
        migrationService.deleteTypes(VocabularyNodeType.TerminologicalVocabulary, "ConceptLink");
        // Delete Vocabulary, YTI-60
        migrationService.deleteTypes(VocabularyNodeType.TerminologicalVocabulary, "Vocabulary");
        // Delete Vocabulary template YTI-60
        migrationService.deleteVocabularyGraph(DomainIndex.VOCABULARY_TEMPLATE_GRAPH_ID);        
    }

    /**
     * Add new used- and definedInScheme attributes
     * @param meta MetaNode to be updated
     * @return true if updated
     */
    boolean updateTypes(MetaNode meta){
        boolean rv = false;
        String domainName=meta.getDomain().getId().name();
        JsonUtils.prettyPrintJson(meta);
        if (meta.isOfType(NodeType.TerminologicalVocabulary)) {
            System.out.println("Remove priority metaID::"+meta.getId()+ " Domain:"+meta.getDomain().getGraphId().toString());
            meta.removeAttribute("priority"); 
        } else if (meta.isOfType(NodeType.Concept)) {
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