package oasis.model.authn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joda.time.Duration;
import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import oasis.model.annotations.Id;

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "_type")
public abstract class Token {
  @Id
  private String id;
  @JsonProperty
  private Instant creationTime;
  @JsonProperty
  private Duration timeToLive;
  @JsonProperty
  private List<String> ancestorIds = new ArrayList<>();

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

  public List<String> getAncestorIds() {
    return Collections.unmodifiableList(ancestorIds);
  }

  public void setAncestorIds(List<String> ancestorIds) {
    this.ancestorIds = new ArrayList<>(ancestorIds);
  }

  public void setParent(Token parent) {
    this.ancestorIds = new ArrayList<>();
    this.ancestorIds.addAll(parent.getAncestorIds());
    this.ancestorIds.add(parent.getId());
  }
}
