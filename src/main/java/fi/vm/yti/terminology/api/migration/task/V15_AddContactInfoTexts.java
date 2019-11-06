package fi.vm.yti.terminology.api.migration.task;

import static fi.vm.yti.terminology.api.migration.PropertyUtil.merge;
import static fi.vm.yti.terminology.api.migration.PropertyUtil.type;
import static java.util.Collections.emptyMap;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.migration.PropertyUtil;
import fi.vm.yti.terminology.api.model.termed.AttributeMeta;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.Property;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;


/**
 * Migration for YTI-893, add contact field into the vocabulary Meta-model
 */
@Component
public class V15_AddContactInfoTexts implements MigrationTask {

    private final MigrationService migrationService;

    V15_AddContactInfoTexts(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        migrationService.updateTypes(VocabularyNodeType.TerminologicalVocabulary, meta -> {
            // Go through text-attributes and add descriptions to known ids
            updateDescriptions(meta);
        });
    }

    /**
     * Update MetaNodes text- and reference-attributes with given description texts.
     * 
     * @param meta MetaNode to be updated
     * @return true if updated
     */
    boolean updateDescriptions(MetaNode meta) {
        boolean rv = false;
        String domainName = meta.getDomain().getId().name();
        if (domainName.equals("TerminologicalVocabulary")) {
            // meta.addAttribute(AttributeIndex.contact(meta.getDomain(), 20));
            updateTextAttributeDescription(meta, "http://uri.suomi.fi/datamodel/ns/st#contact",
                    "Palautekanavan kuvaus. Älä käytä henkilökohtaista sähköpostiosoitetta."
                            + " Suositeltava muoto esimerkiksi: \"Sanastotyöryhmän ylläpito: yllapito@example.org\"",
                    "Description for the feedback channel. Do not use personal email addresses."
                            + " Preferred form for example: \"Terminology working group: maintain@example.org\"");
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
    boolean updateTextAttributeDescription(MetaNode meta, String attributeName, String description,
            String endescription) {
        boolean rv = false;
        List<AttributeMeta> ta = meta.getTextAttributes().stream().filter(item -> item.getUri().equals(attributeName))
                .collect(Collectors.toList());
        if (!ta.isEmpty()) {
            if (ta.size() > 1) {
                System.err.println("Error, several example attributes in same node ");
            } else {

                AttributeMeta att = ta.get(0);
                // check if description exist and add new one
                Property desc = new Property("fi", description);
                if (att.getProperties().get(desc) != null) {
                    System.err.println("Description property exists already");
                } else {
                    att.updateProperties(PropertyUtil.merge(att.getProperties(),
                            PropertyUtil.description(description, endescription)));
                    rv = true;
                }
            }
        }
        return rv;
    }
}