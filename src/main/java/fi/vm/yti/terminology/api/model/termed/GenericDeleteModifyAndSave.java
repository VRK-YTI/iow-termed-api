package fi.vm.yti.terminology.api.model.termed;

import java.util.List;

import static java.util.Collections.emptyList;

public final class GenericDeleteModifyAndSave implements DeleteAndSave {

    private final List<Identifier> delete;
    private final List<GenericNode> save;
    private final List<GenericNode> patch;

    // Jackson constructor
    private GenericDeleteModifyAndSave() {
        this(emptyList(), emptyList(), emptyList());
    }

    public GenericDeleteModifyAndSave(List<Identifier> delete,
                                      List<GenericNode> save,
                                      List<GenericNode> patch) {
        this.delete = delete;
        this.save = save;
        this.patch = patch;
    }

    public List<Identifier> getDelete() {
        return delete;
    }

    public List<GenericNode> getSave() {
        return save;
    }

    public List<GenericNode> getPatch() {
        return patch;
    }
}
