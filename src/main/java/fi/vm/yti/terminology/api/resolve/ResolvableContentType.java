package fi.vm.yti.terminology.api.resolve;

import java.util.Arrays;

import org.springframework.util.StringUtils;

public enum ResolvableContentType {

    HTML("text/html"),
    JSON("application/json"),
    JSON_LD("application/ld+json"),
    RDF_XML("application/rdf+xml"),
    RDF_TURTLE("text/turtle");

    private final String mediaType;

    ResolvableContentType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getMediaType() {
        return this.mediaType;
    }

    public boolean isHandledByFrontend() {
        return this == HTML;
    }

    public static ResolvableContentType fromString(String... contentTypes) {

        for (String contentType : contentTypes) {
            if (!StringUtils.isEmpty(contentType)) {
                for (ResolvableContentType value : values()) {
                    if (contentType.contains(value.mediaType)) {
                        return value;
                    }
                }
            }
        }

        if (contentTypes.length == 0 || Arrays.stream(contentTypes).anyMatch(type -> "*/*".equals(type))) {
            return JSON;
        }

        throw new RuntimeException("Unsupported content types: " + String.join(", ", contentTypes));
    }
}
