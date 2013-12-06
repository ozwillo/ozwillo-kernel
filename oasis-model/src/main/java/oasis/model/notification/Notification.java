package oasis.model.notification;

import javax.annotation.Nonnull;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonRootName("notification")
public class Notification {
  public enum Status {
    READ,
    UNREAD
  }

  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty
  private String userId;

  @JsonProperty
  @ApiModelProperty
  private String applicationId;

  @JsonProperty
  @ApiModelProperty
  private String data;

  @JsonProperty
  @ApiModelProperty
  private String message;

  @JsonProperty
  @ApiModelProperty
  private Instant time;

  @JsonProperty
  @ApiModelProperty(dataType = "String", allowableValues = "READ,UNREAD")
  private Status status = Status.UNREAD;

  public Notification() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Notification(@Nonnull Notification other) {
    this.applicationId = other.getApplicationId();
    this.data = other.getData();
    this.message = other.getMessage();
    this.status = other.getStatus();
    this.time = other.getTime();
    this.userId = other.getUserId();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getApplicationId() {
    return applicationId;
  }

  public void setApplicationId(String applicationId) {
    this.applicationId = applicationId;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getTime() {
    return time;
  }

  public void setTime(Instant time) {
    this.time = time;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }
}
