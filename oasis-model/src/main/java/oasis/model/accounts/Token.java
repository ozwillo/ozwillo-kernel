package oasis.model.accounts;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonTypeInfo(use=JsonTypeInfo.Id.MINIMAL_CLASS, property="_type")
public abstract class Token {
  @JsonView(TokenViews.Serializer.class)
  @ApiModelProperty(required = true)
  @Id
  private String id;

  @JsonView(TokenViews.Serializer.class)
  @JsonProperty
  @ApiModelProperty(required = true)
  private Date creationTime;

  @JsonView(TokenViews.Serializer.class)
  @JsonProperty
  @ApiModelProperty(required = true)
  private long timeToLive;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Date getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(Date creationTime) {
    this.creationTime = creationTime;
  }

  public long getTimeToLive() {
    return timeToLive;
  }

  public void setTimeToLive(long timeToLive) {
    this.timeToLive = timeToLive;
  }

  // TODO : Add properties about Application and Scopes
}
