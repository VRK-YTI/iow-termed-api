package fi.vm.yti.terminology.api.migration.task;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.migration.PropertyUtil;
import fi.vm.yti.terminology.api.model.termed.AttributeMeta;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;

/**
 * Additional migrations for YTI-46.
 */
@Component
public class V19_SwedishTranslationsForConceptLinks implements MigrationTask {

    private final MigrationService migrationService;

    V19_SwedishTranslationsForConceptLinks(MigrationService migrationService) {
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
     * Update ConceptLink type and text attributes with Swedish translations.
     *
     * @param meta MetaNode to be updated
     */
    void updateMeta(MetaNode meta) {
        String domainName = meta.getDomain().getId().name();
        if (domainName.equals("ConceptLink")) {
            meta.updateProperties(PropertyUtil.prefLabel("Linkitetty käsite", "Linked concept", "Länkat begrepp"));
            updateTextAttributePrefLabel(meta, "prefLabel", "Suositettava termi", "Preferred label", "Rekommenderad term");
            updateTextAttributePrefLabel(meta, "targetGraph", "Viitatun käsitteen uri", "Target uri", "Begreppets uri som det hänvisas till");
            updateTextAttributePrefLabel(meta, "targetId", "Viitatun käsitteen id", "Target identifier", "Begreppets id som det hänvisas till");
            updateTextAttributePrefLabel(meta, "vocabularyLabel", "Sanaston nimi", "Vocabulary label", "Ordlistans namn");
        }
    }

    /**
     * Update individual preflabel for text-attributes
     *
     * @param meta
     * @param attributeName
     * @return
     */
    private void updateTextAttributePrefLabel(MetaNode meta,
                                              String attributeName,
                                              String fi,
                                              String en,
                                              String sv) {
        List<AttributeMeta> ta = meta.getTextAttributes().stream().filter(item -> item.getId().equals(attributeName))
            .collect(Collectors.toList());
        if (!ta.isEmpty()) {
            if (ta.size() > 1) {
                System.err.println("Error, several " + attributeName + " attributes in same node ");
            } else {
                AttributeMeta att = ta.get(0);
                att.updateProperties(PropertyUtil.merge(att.getProperties(), PropertyUtil.prefLabel(fi, en, sv)));
            }
        }
    }
}
