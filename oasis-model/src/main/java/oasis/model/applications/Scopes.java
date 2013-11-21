package oasis.model.applications;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("scopes")
public class Scopes {
  @JsonProperty
  @ApiModelProperty()
  private String[] values;

  @JsonIgnore
  private long modified;

  public String[] getValues() {
    return values;
  }

  public void setValues(String[] values) {
    this.values = values;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}