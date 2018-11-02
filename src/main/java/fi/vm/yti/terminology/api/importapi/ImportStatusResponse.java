package fi.vm.yti.terminology.api.importapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

// Don't marshall null values
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImportStatusResponse {
    String status;
    String progress;
    String statistics;
    String payload;
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProgress() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress = progress;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatistics() {
        return statistics;
    }

    public void setStatistics(String statistics) {
        this.statistics = statistics;
    }

    public String toString(){
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static  ImportStatusResponse fromString(String objStr){
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(objStr,ImportStatusResponse.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
        } catch(IOException ioe){
            ioe.printStackTrace();
        }
        return null;
    }
}
