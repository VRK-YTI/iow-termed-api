package fi.vm.yti.terminology.api;

import org.springframework.util.StringUtils;

public enum TermedContentType {

    JSON("application/json; charset=utf-8"),
    JSON_LD("application/ld+json"),
    RDF_XML("application/rdf+xml"),
    RDF_TURTLE("text/turtle");

    private final String contentType;

    TermedContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public static TermedContentType fromString(String... contentTypes) {

        for (String contentType : contentTypes) {
            if (!StringUtils.isEmpty(contentType)) {
                for (TermedContentType value : values()) {
                    if (contentType.contains(value.contentType)) {
                        return value;
                    }
                }
            }
        }

        return JSON;
    }
}
