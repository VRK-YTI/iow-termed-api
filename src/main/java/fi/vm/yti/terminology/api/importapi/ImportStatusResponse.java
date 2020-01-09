package fi.vm.yti.terminology.api.importapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "status",
        "processingTotal",
        "processingProgress",
        "resultsCreated",
        "resultsWarning",
        "resultsError",
        "statusMessage"
})
public class ImportStatusResponse implements Serializable
{
    // Enum states for status
    public enum Status {
        QUEUED, PREPROCESSING, PROCESSING, POSTPROCESSING, SUCCESS, SUCCESS_WITH_ERRORS, FAILURE, NOT_FOUND
    };

    /**
     * Current job status. This is not required to go through all phases, but is
     * required to end up in either SUCCESS or FAILURE.
     */
    @JsonProperty("status")
    private Status status;
    /**
     * For phase PROCESSING the total goal. May change during processing. Must be
     * given if processingProgress is given. Unspecified for other phases.
     */
    @JsonProperty("processingTotal")
    private Integer processingTotal;
    /**
     * For phase PROCESSING the current progress, between 0 and processingTotal.
     * Maybe null, meaning "unknown". Unspecified for other phases.
     */
    @JsonProperty("processingProgress")
    private Integer processingProgress;
    /**
     * Total number of created entities. May differ from processingTotal for various
     * reasons, e.g., different level of abstraction, dropped (unsupported)
     * entities, and erroneous entities (resultsError).
     */
    @JsonProperty("resultsCreated")
    private Integer resultsCreated;
    /**
     * Total number of generated warnings. These most likely concern the created
     * entities, but indicate that the source data did not meet requirements.
     */
    @JsonProperty("resultsWarning")
    private Integer resultsWarning;
    /**
     * Total number of generated errors. These should relate to erroneous entities
     * which could not be created, although error number is not required to match
     * entity count.
     */
    @JsonProperty("resultsError")
    private Integer resultsError;
    @JsonProperty("statusMessage")
    /**
     * Actual human readable explanation message
     */
    private List<ImportStatusMessage> statusMessage = new ArrayList<ImportStatusMessage>();

    private final static long serialVersionUID = -1763183565228005833L;

    /**
     * No args constructor for use in serialization
     *
     */
    public ImportStatusResponse() {
    }

    /**
     *
     * @param status
     * @param resultsWarning
     * @param resultsError
     * @param resultsCreated
     * @param processingProgress
     * @param processingTotal
     * @param statusMessage
     */
    public ImportStatusResponse(Status status, Integer processingTotal, Integer processingProgress, Integer resultsCreated, Integer resultsWarning, Integer resultsError, List<ImportStatusMessage> statusMessage) {
        super();
        this.status = status;
        this.processingTotal = processingTotal;
        this.processingProgress = processingProgress;
        this.resultsCreated = resultsCreated;
        this.resultsWarning = resultsWarning;
        this.resultsError = resultsError;
        this.statusMessage = statusMessage;
    }

    @JsonProperty("status")
    public Status getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(Status status) {
        this.status = status;
    }

    @JsonProperty("processingTotal")
    public Integer getProcessingTotal() {
        return processingTotal;
    }

    @JsonProperty("processingTotal")
    public void setProcessingTotal(Integer processingTotal) {
        this.processingTotal = processingTotal;
    }

    @JsonProperty("processingProgress")
    public Integer getProcessingProgress() {
        return processingProgress;
    }

    @JsonProperty("processingProgress")
    public void setProcessingProgress(Integer processingProgress) {
        this.processingProgress = processingProgress;
    }

    @JsonProperty("resultsCreated")
    public Integer getResultsCreated() {
        return resultsCreated;
    }

    @JsonProperty("resultsCreated")
    public void setResultsCreated(Integer resultsCreated) {
        this.resultsCreated = resultsCreated;
    }

    @JsonProperty("resultsWarning")
    public Integer getResultsWarning() {
        return resultsWarning;
    }

    @JsonProperty("resultsWarning")
    public void setResultsWarning(Integer resultsWarning) {
        this.resultsWarning = resultsWarning;
    }

    @JsonProperty("resultsError")
    public Integer getResultsError() {
        return resultsError;
    }

    @JsonProperty("resultsError")
    public void setResultsError(Integer resultsError) {
        this.resultsError = resultsError;
    }

    @JsonProperty("statusMessage")
    public List<ImportStatusMessage> getStatusMessage() {
        return statusMessage;
    }

    @JsonProperty("statusMessage")
    public void setStatusMessage(List<ImportStatusMessage> statusMessage) {
        this.statusMessage = statusMessage;
    }

    @JsonProperty("statusMessage")
    public void addStatusMessage(ImportStatusMessage statusMessage) {
        this.statusMessage.add(statusMessage);
    }

    @JsonIgnore
    @JsonProperty("statusMessage")
    public void clearStatusMessages() {
        this.statusMessage.clear();
    }

    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static ImportStatusResponse fromString(String objStr) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(objStr, ImportStatusResponse.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
