package oasis.model.applications;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonRootName("serviceProvider")
public class ServiceProvider {
  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String name;

  @JsonProperty
  @ApiModelProperty()
  private List<ScopeCardinality> scopeCardinalities;

  @JsonProperty
  @ApiModelProperty
  private long modified;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<ScopeCardinality> getScopeCardinalities() {
    return scopeCardinalities;
  }

  public void setScopeCardinalities(List<ScopeCardinality> scopeCardinalities) {
    this.scopeCardinalities = scopeCardinalities;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
