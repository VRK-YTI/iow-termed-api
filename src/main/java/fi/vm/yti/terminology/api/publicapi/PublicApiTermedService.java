package fi.vm.yti.terminology.api.publicapi;

import fi.vm.yti.terminology.api.TermedRequester;
import fi.vm.yti.terminology.api.model.termed.Attribute;
import fi.vm.yti.terminology.api.model.termed.GenericNode;
import fi.vm.yti.terminology.api.util.JsonUtils;
import fi.vm.yti.terminology.api.util.Parameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fi.vm.yti.terminology.api.model.termed.VocabularyNodeType.TerminologicalVocabulary;
import static fi.vm.yti.terminology.api.model.termed.VocabularyNodeType.Vocabulary;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpMethod.GET;

@Service
public class PublicApiTermedService {

    private final TermedRequester termedRequester;

    @Autowired
    public PublicApiTermedService(TermedRequester termedRequester) {
        this.termedRequester = termedRequester;
    }

    @SuppressWarnings("Duplicates")
    @NotNull List<PublicApiVocabulary> getVocabularyList() {
        Parameters params = new Parameters();
        params.add("select", "id");
        params.add("select", "uri");
        params.add("select", "code");
        params.add("select", "properties.prefLabel");
        params.add("select", "properties.status");
        params.add("select", "type");


        params.add("where",
                "type.id:" + Vocabulary +
                        " OR type.id:" + TerminologicalVocabulary);

        params.add("max", "-1");

        List<GenericNode> vocabulariesFromNodeTrees = requireNonNull(termedRequester.exchange("/node-trees", GET, params, new ParameterizedTypeReference<List<GenericNode>>() {}));
        return extractResultFromNodeTrees(vocabulariesFromNodeTrees);
    }

    private List<PublicApiVocabulary> extractResultFromNodeTrees(List<GenericNode> vocabsFromNodeTrees) {
        List<PublicApiVocabulary> result = new ArrayList<>();
        List<String> codesAlreadyAdded = new ArrayList<>();
            for (GenericNode genericNode: vocabsFromNodeTrees) {
                    PublicApiVocabulary vocabulary = new PublicApiVocabulary();
                    vocabulary.setId(genericNode.getType().getGraphId());
                    vocabulary.setUri(genericNode.getUri());

                    vocabulary.setPrefLabel(prefLabelAsLocalizable(genericNode));
                    vocabulary.setStatus(getStatus(genericNode));
                    if (!codesAlreadyAdded.contains(genericNode.getType().getGraphId().toString())) {
                        result.add(vocabulary);
                        codesAlreadyAdded.add(genericNode.getType().getGraphId().toString());
                    }
            }
        return result;
    }

    public  HashMap<String, String> prefLabelAsLocalizable(GenericNode node) {
        HashMap<String, String> result = new HashMap<>();

        Map<String, List<Attribute>> attributes = node.getProperties();
        if (attributes.keySet().contains("prefLabel")) {
            for (Attribute prefLabel: node.getProperties().get("prefLabel")) {
                result.put(prefLabel.getLang(), prefLabel.getValue());
            }
        }

        return result;
    }

    public  String getStatus(GenericNode node) {
        String result = null;
        Map<String, List<Attribute>> attributes = node.getProperties();
        if (attributes.keySet().contains("status")) {
            for (Attribute stat: node.getProperties().get("status")) {
                result=stat.getValue(); 
            }
        }
        return result;
    }}
