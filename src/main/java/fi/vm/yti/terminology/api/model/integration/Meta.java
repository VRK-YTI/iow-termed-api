 package fi.vm.yti.terminology.api.model.integration;

 import com.fasterxml.jackson.annotation.JsonInclude;
 import com.fasterxml.jackson.annotation.JsonInclude.Include;
 import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
 import io.swagger.annotations.ApiModel;
 import java.text.ParseException;
 import java.util.Date;
 import javax.xml.bind.annotation.XmlType;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 
 @XmlType(
    propOrder = {"code", "message", "pageSize", "from", "resultCount", "totalResults", "after", "afterResourceUrl", "nextPage"}
 )
 @ApiModel(
    value = "Meta",
    description = "Meta information model for API responses."
 )
 @JsonInclude(Include.NON_EMPTY)
 public class Meta {
    private static final Logger LOG = LoggerFactory.getLogger(Meta.class);
    private Integer code;
    private String message;
    private Integer pageSize;
    private Integer from;
    private Integer resultCount;
    private Integer totalResults;
    private Date after;
    private String afterResourceUrl;
    private String nextPage;
    private String entityIdentifier;
    private String nonTranslatableMessage;
 
    public Meta() {
    }
 
    public Meta(Integer code, Integer pageSize, Integer from, String after) {
       this.code = code;
       this.pageSize = pageSize;
       this.from = from;
       this.after = parseAfterFromString(after);
    }
 
    public Meta(Integer code, Integer pageSize, Integer from, String after, String entityIdentifier) {
       this.code = code;
       this.pageSize = pageSize;
       this.from = from;
       this.after = parseAfterFromString(after);
       this.entityIdentifier = entityIdentifier;
    }
 
    public Meta(Integer code, Integer pageSize, Integer from, String after, String entityIdentifier, String nonTranslatableMessage) {
       this.code = code;
       this.pageSize = pageSize;
       this.from = from;
       this.after = parseAfterFromString(after);
       this.entityIdentifier = entityIdentifier;
       this.nonTranslatableMessage = nonTranslatableMessage;
    }
 
    public Integer getCode() {
       return this.code;
    }
 
    public void setCode(Integer code) {
       this.code = code;
    }
 
    public String getMessage() {
       return this.message;
    }
 
    public void setMessage(String message) {
       this.message = message;
    }
 
    public Integer getPageSize() {
       return this.pageSize;
    }
 
    public void setPageSize(Integer pageSize) {
       this.pageSize = pageSize;
    }
 
    public Integer getFrom() {
       return this.from;
    }
 
    public void setFrom(Integer from) {
       this.from = from;
    }
 
    public Integer getResultCount() {
       return this.resultCount;
    }
 
    public void setResultCount(Integer resultCount) {
       this.resultCount = resultCount;
    }
 
    public Integer getTotalResults() {
       return this.totalResults;
    }
 
    public void setTotalResults(Integer totalResults) {
       this.totalResults = totalResults;
    }
 
    public Date getAfter() {
       return this.after != null ? new Date(this.after.getTime()) : null;
    }
 
    public void setAfter(Date after) {
       if (after != null) {
          this.after = new Date(after.getTime());
       } else {
          this.after = null;
       }
    }

    public void setAfter(String afterStr) {
      after = parseAfterFromString(afterStr);
       if (after != null) {
          this.after = new Date(after.getTime());
       } else {
          this.after = null;
       }
 
    }
 
    public String getAfterResourceUrl() {
       return this.afterResourceUrl;
    }
 
    public void setAfterResourceUrl(String afterResourceUrl) {
       this.afterResourceUrl = afterResourceUrl;
    }
 
    public String getNextPage() {
       return this.nextPage;
    }
 
    public void setNextPage(String nextPage) {
       this.nextPage = nextPage;
    }
 
    public static Date parseAfterFromString(String after) {
       if (after != null) {
          ISO8601DateFormat dateFormat = new ISO8601DateFormat();
 
          try {
             return dateFormat.parse(after);
          } catch (ParseException var3) {
             LOG.error("Parsing date from string failed: " + var3.getMessage());
          }
       }
 
       return null;
    }
 
    public String getEntityIdentifier() {
       return this.entityIdentifier;
    }
 
    public void setEntityIdentifier(String entityIdentifier) {
       this.entityIdentifier = entityIdentifier;
    }
 
    public String getNonTranslatableMessage() {
       return this.nonTranslatableMessage;
    }
 
    public void setNonTranslatableMessage(String nonTranslatableMessage) {
       this.nonTranslatableMessage = nonTranslatableMessage;
    }
 }
