package fi.vm.yti.terminology.api.migration.task;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.AttributeIndex;
import fi.vm.yti.terminology.api.migration.DomainIndex;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.model.termed.Identifier;
import fi.vm.yti.terminology.api.model.termed.MetaNode;
import fi.vm.yti.terminology.api.model.termed.NodeType;
import fi.vm.yti.terminology.api.model.termed.TypeId;
import fi.vm.yti.terminology.api.model.termed.VocabularyNodeType;
import fi.vm.yti.terminology.api.util.JsonUtils;

/**
 * Migration for YTI-60, Remove vocabularies
 *
 */
@Component
public class V16_RemoveVocabulary implements MigrationTask {
    private static Logger logger = LoggerFactory.getLogger(V16_RemoveVocabulary.class);
    private final MigrationService migrationService;

    V16_RemoveVocabulary(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        // Delete Vocabulary, YTI-60
        migrationService.deleteTypes(VocabularyNodeType.TerminologicalVocabulary, "Vocabulary");
        // Delete Vocabulary template YTI-60
        migrationService.deleteVocabularyGraph(DomainIndex.VOCABULARY_TEMPLATE_GRAPH_ID);
    }

}