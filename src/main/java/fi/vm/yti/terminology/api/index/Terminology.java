package fi.vm.yti.terminology.api.index;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;


public class Terminology extends SimpleTerminology {
    private final Map<String, List<String>> description;
    private final Instant modified;
    private final List<String> languages;
    private final List<String> domains;
    private final List<String> contributors;

    protected Terminology(@NotNull UUID id,
                          @NotNull String uri,
                          @NotNull Map<String, List<String>> label,
                          @NotNull String status,
                          @NotNull Map<String, List<String>> description,
                          @NotNull Instant modified,
                          @NotNull List<String> languages,
                          @NotNull List<String> domains,
                          @NotNull List<String> contributors) {
        super(id, uri, label, status);
        this.description = description;
        this.modified = modified;
        this.languages = languages;
        this.domains = domains;
        this.contributors = contributors;
    }

    public Map<String, List<String>> getDescription() {
        return description;
    }

    public Instant getModified() {
        return modified;
    }

    public List<String> getLanguages() {
        return languages;
    }
}
