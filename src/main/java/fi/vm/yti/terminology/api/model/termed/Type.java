package fi.vm.yti.terminology.api.model.termed;

public final class Type {

    private TypeId type;

    // Jackson constructor
    private Type() {
        type = null;
    }

    public Type(TypeId id) {
        this.type = id;
    }

    public static Type placeholder() {
        return new Type();
    }

    public TypeId getType() {
        return type;
    }

    public void setType(TypeId t) {
        type = t;
    }
}
