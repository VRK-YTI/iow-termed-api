package fi.vm.yti.terminology.api.migration.task;

import fi.vm.yti.migration.MigrationTask;
import fi.vm.yti.terminology.api.migration.AttributeIndex;
import fi.vm.yti.terminology.api.migration.MigrationService;
import fi.vm.yti.terminology.api.migration.ReferenceIndex;
import fi.vm.yti.terminology.api.model.termed.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static fi.vm.yti.terminology.api.migration.DomainIndex.*;
import static fi.vm.yti.terminology.api.migration.PropertyUtil.*;
import static java.util.Arrays.asList;
import static java.util.Collections.*;

@Component
public class V17_Groups implements MigrationTask {

        private final MigrationService migrationService;

        @Autowired
        V17_Groups(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void migrate() {
        replaceGroups();
    }

    private void replaceGroups() {
        // YTI-1308
        // Change Kartat --> Paikkatieto
        // sv: Geodata
        // en: Geospatial data
        migrationService.updateNodesWithJson(new ClassPathResource("migration/newGroupNodes.json"));
    }
}
