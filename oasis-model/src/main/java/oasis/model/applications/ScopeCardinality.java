package oasis.model.applications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("scopeCardinality")
public class ScopeCardinality {
  @JsonProperty
  @ApiModelProperty(required = true)
  private String scopeId;

  @JsonProperty
  @ApiModelProperty
  private Integer min;

  @JsonProperty
  @ApiModelProperty
  private Integer max;

  // TODO: Manage I18N
  @JsonProperty
  @ApiModelProperty
  private String motivations;

  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  public Integer getMin() {
    return min;
  }

  public void setMin(Integer min) {
    this.min = min;
  }

  public Integer getMax() {
    return max;
  }

  public void setMax(Integer max) {
    this.max = max;
  }

  public String getMotivations() {
    return motivations;
  }

  public void setMotivations(String motivations) {
    this.motivations = motivations;
  }
}
