package fi.vm.yti.terminology.api.migration.task;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.AttributeIndex;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.migration.PropertyUtil;
import fi.vm.yti.terminology.api.model.termed.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Migration for YTI-893, add contact field into the vocabulary Meta-model
 */
@Component
public class V14_AddContact implements MigrationTask {

    private final MigrationService migrationService;

    V14_AddContact(MigrationService migrationService) {
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
        ;
        // Print textAttributes

        String domainName = meta.getDomain().getId().name();
        System.out.println("DomainName:"+domainName);
        if (domainName.equals("TerminologicalVocabulary")) {
            System.out.println("Add  info-texts for contact:"+domainName);
            meta.addAttribute(AttributeIndex.contact(meta.getDomain(), 20));
            updateTextAttributeDescription(meta, "contact",
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
        // Print textAttributes
        List<AttributeMeta> ta = meta.getTextAttributes().stream().filter(item -> item.getId().equals(attributeName))
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
