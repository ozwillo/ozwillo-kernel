package oasis.model.authn;

import org.joda.time.Duration;
import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonTypeInfo(use=JsonTypeInfo.Id.MINIMAL_CLASS, property="_type")
public abstract class Token {
  @Id
  private String id;

  @JsonProperty
  private Instant creationTime;

  @JsonProperty
  private Duration timeToLive;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Instant getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(Instant creationTime) {
    this.creationTime = creationTime;
  }

  public Duration getTimeToLive() {
    return timeToLive;
  }

  public void setTimeToLive(Duration timeToLive) {
    this.timeToLive = timeToLive;
  }
}
