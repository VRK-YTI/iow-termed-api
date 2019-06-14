package fi.vm.yti.terminology.api.migration.task;

import org.springframework.stereotype.Component;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.migration.ReferenceIndex;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;

/**
 * Migration for YTI-1106, classification->information domain and HomographNumber fix."
 */
@Component
public class V10_AddNarrowerAndHasPart implements MigrationTask {

    private final MigrationService migrationService;

    V10_AddNarrowerAndHasPart(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        migrationService.updateTypes(VocabularyNodeType.TerminologicalVocabulary, meta -> {
            // Go  through text-attributes and add descriptions to known ids
            updateMeta(meta);
        });
    }

    /**
     * Update MetaNodes text- and reference-attributes with given description texts.
     * @param meta MetaNode to be updated
     * @return true if updated
     */
    boolean updateMeta(MetaNode meta){
        boolean rv = false;;
        // Print textAttributes

        String domainName=meta.getDomain().getId().name();
        if(domainName.equals("Concept")){
            // ReferenceAttributes
            updateReferenceMeta(meta);
        }
        return rv;
    }


    /**
     * Update Group reference-attribute labels
     * @param meta
     * @param attributeName
     * @return
     */
    boolean updateReferenceMeta(MetaNode meta){
        boolean rv = false;        
        meta.getReferenceAttributes().add(ReferenceIndex.narrower(meta.getDomain(), 17, "Hierarkkinen alakäsite","Narrower concept"));
        meta.getReferenceAttributes().add(ReferenceIndex.hasPartConcept(meta.getDomain(), 18));
        return rv;
    }

}
