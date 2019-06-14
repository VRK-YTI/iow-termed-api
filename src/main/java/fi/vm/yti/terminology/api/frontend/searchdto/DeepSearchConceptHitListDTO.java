package fi.vm.yti.terminology.api.frontend.searchdto;

import java.util.List;

public class DeepSearchConceptHitListDTO extends DeepSearchHitListDTO<ConceptSimpleDTO> {
    public DeepSearchConceptHitListDTO() {
        super(Type.CONCEPT);
    }

    public DeepSearchConceptHitListDTO(long totalCount, List<ConceptSimpleDTO> topHits) {
        super(Type.CONCEPT, totalCount, topHits);
    }
}
