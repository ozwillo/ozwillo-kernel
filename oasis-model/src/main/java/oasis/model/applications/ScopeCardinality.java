package oasis.model.applications;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("scopeCardinality")
public class ScopeCardinality {
  @JsonProperty
  @ApiModelProperty(required = true)
  private String scope;

  @JsonProperty
  @ApiModelProperty
  private Integer min;

  @JsonProperty
  @ApiModelProperty
  private Integer max;

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
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
}
