package fi.vm.yti.terminology.api.index;

import java.util.UUID;

public class BrokenTermedDataLinkException extends RuntimeException {
    BrokenTermedDataLinkException(SimpleTerminology vocabulary, UUID nodeId) {
        super("Node (" + nodeId + ") not in graph (" + vocabulary.getId() + ")");
    }
}
