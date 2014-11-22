package oasis.model.eventbus;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.annotations.Id;

public class Subscription {

  @Id
  private String id;

  @JsonProperty String webHook;

  @JsonProperty String secret;

  @JsonProperty String eventType;

  @JsonProperty String instance_id;

  public Subscription() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Subscription(@Nonnull Subscription other) {
    this.webHook = other.getWebHook();
    this.secret = other.getSecret();
    this.eventType = other.getEventType();
  }

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

  public String getInstance_id() {
    return instance_id;
  }

  public void setInstance_id(String instance_id) {
    this.instance_id = instance_id;
  }
}
