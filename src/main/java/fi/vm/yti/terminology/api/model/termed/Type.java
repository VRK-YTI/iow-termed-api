package fi.vm.yti.terminology.api.model.termed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.fasterxml.jackson.annotation.JsonInclude.Include;;
import java.util.UUID;
public final class Type {

    private  TypeId type;

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
