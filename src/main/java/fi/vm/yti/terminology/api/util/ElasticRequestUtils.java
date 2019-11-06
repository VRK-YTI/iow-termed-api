package fi.vm.yti.terminology.api.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.elasticsearch.client.Response;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.terminology.api.exception.InvalidQueryException;

public final class ElasticRequestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticRequestUtils.class);

    public static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("[a-z]+(?:-[a-zA-Z0-9-]+)?");
    public static final Pattern QUERY_SPLITTER_PATTERN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String LUCENE_PLAIN_QUERY_PATTERN_STRING = "^(?:(?!(?:\\s++|^)(?:AND|OR|TO)(?:\\s|$))(?:\\w++|\\s++|(?<=\\w)-++))+$";
    private static final String LUCENE_ASTERISK_QUERY_PATTERN_STRING = "^(?:(?!(?:\\s++|^)(?:AND|OR|TO)(?:\\s|$))(?:\\w++|\\s++|(?<=[\\w*])-++|(?<!\\*)\\*(?=[\\w-])|(?<=[\\w-])\\*(?!\\*)))+$";
    private static final Pattern LUCENE_PLAIN_QUERY_PATTERN = Pattern.compile(LUCENE_PLAIN_QUERY_PATTERN_STRING, Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LUCENE_GIVEN_QUERY_PATTERN = Pattern.compile(LUCENE_ASTERISK_QUERY_PATTERN_STRING, Pattern.UNICODE_CHARACTER_CLASS);

    private ElasticRequestUtils() {
        // prevent construction
    }

    public static QueryStringQueryBuilder buildPrefixSuffixQuery(final String searchTerm) {
        if (searchTerm != null) {
            final String trimmed = searchTerm.trim().toLowerCase();
            if (!searchTerm.isEmpty()) {
                String parsedQuery = null;
                if (LUCENE_PLAIN_QUERY_PATTERN.matcher(trimmed).matches()) {
                    final String[] splitQuery = QUERY_SPLITTER_PATTERN.split(trimmed);
                    if (splitQuery.length == 1) {
                        parsedQuery = trimmed + " OR " + trimmed + "* OR *" + trimmed;
                    } else if (splitQuery.length > 1) {
                        parsedQuery = Arrays.stream(splitQuery).map(q -> "(" + q + " OR " + q + "* OR *" + q + ")").collect(Collectors.joining(" AND "));
                    }
                } else if (LUCENE_GIVEN_QUERY_PATTERN.matcher(trimmed).matches()) {
                    parsedQuery = trimmed;
                }
                if (parsedQuery != null) {
                    final StandardQueryParser parser = new StandardQueryParser();
                    try {
                        parser.setAllowLeadingWildcard(true);
                        LOG.debug("Using Lucene query: '" + parsedQuery + "'");
                        return QueryBuilders.queryStringQuery(parser.parse(parsedQuery, "").toString());
                    } catch (final QueryNodeException e) {
                        // nop
                    }
                }
            }
        }
        LOG.warn("Search term string disqualified: '" + searchTerm + "'");
        throw new InvalidQueryException();
    }

    public static @NotNull JsonNode responseContentAsJson(@NotNull ObjectMapper objectMapper,
                                                          @NotNull Response response) {
        try {
            return objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NotNull String responseContentAsString(@NotNull Response response) {
        try (InputStream is = response.getEntity().getContent()) {
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines()
                .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, String> labelFromKeyValueNode(JsonNode labelNode) {
        Map<String, String> ret = new HashMap<>();
        if (labelNode != null) {
            Iterator<Map.Entry<String, JsonNode>> labelIter = labelNode.fields();
            while (labelIter.hasNext()) {
                Map.Entry<String, JsonNode> entry = labelIter.next();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    ret.put(entry.getKey(), value.textValue());
                } else if (value.isArray() && value.has(0)) {
                    ret.put(entry.getKey(), value.get(0).textValue());
                }
            }
        }
        return !ret.isEmpty() ? ret : null;
    }

    public static Map<String, String> labelFromLangValueArray(JsonNode labelArray) {
        Map<String, String> ret = new HashMap<>();
        if (labelArray != null) {
            for (JsonNode label : labelArray) {
                ret.put(label.get("lang").textValue(), label.get("value").textValue());
            }
        }
        return !ret.isEmpty() ? ret : null;
    }

    public static String getTextValueOrNull(JsonNode node,
                                            String fieldName) {
        if (node != null) {
            JsonNode field = node.get(fieldName);
            if (field != null) {
                return field.textValue();
            }
        }
        return null;
    }

    public static Pattern createHighlightPattern(String queryString) {
        if (queryString != null) {
            String patternString = QUERY_SPLITTER_PATTERN.splitAsStream(queryString)
                .filter(part -> !part.isEmpty())
                .sorted((a, b) -> {
                    int diff = b.length() - a.length();
                    return diff != 0 ? diff : a.compareTo(b);
                })
                .map(Pattern::quote)
                .map(quoted -> quoted + "\\b|\\b" + quoted)
                .collect(Collectors.joining("|"));
            if (!patternString.isEmpty()) {
                return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
            }
        }
        return null;
    }

    public static void highlightLabel(Map<String, String> label,
                                      Pattern highlightPattern) {
        if (highlightPattern != null && label != null && !label.isEmpty()) {
            for (Map.Entry<String, String> entry : label.entrySet()) {
                entry.setValue(highlightPattern.matcher(entry.getValue()).replaceAll("<b>$0</b>"));
            }
        }
    }
}
