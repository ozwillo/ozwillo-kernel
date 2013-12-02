package oasis.model.applications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonRootName("subscription")
public class Subscription {

  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty(required = true)
  String webHook;

  @JsonProperty
  @ApiModelProperty
  String secret;

  @JsonProperty
  @ApiModelProperty(required = true)
  String eventType; // Unique (gives the application for an organisation)

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getWebHook() {
    return webHook;
  }

  public void setWebHook(String webHook) {
    this.webHook = webHook;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }
}
