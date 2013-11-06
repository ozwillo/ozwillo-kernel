package oasis.model.applications;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("scopeCardinalities")
public class ScopeCardinalities {
  @JsonProperty
  @ApiModelProperty()
  private ScopeCardinality[] values;

  @JsonIgnore
  private long modified;

  public ScopeCardinality[] getValues() {
    return values;
  }

  public void setValues(ScopeCardinality[] values) {
    this.values = values;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
