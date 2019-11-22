package fi.vm.yti.terminology.api.model.termed;

public enum NodeType {

    Vocabulary,
    TerminologicalVocabulary,
    Concept,
    Term,
    ConceptLink,
    Collection,
    Group,
    Organization,
    Schema;

    public static NodeType placeholder() {
        return TerminologicalVocabulary;
    }
}
