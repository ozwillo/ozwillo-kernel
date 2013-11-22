package oasis.model.applications;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("scopes")
public class Scopes {

  private String dataProviderId;

  @JsonProperty
  @ApiModelProperty
  private List<String> values;

  private long modified;

  public String getDataProviderId() {
    return dataProviderId;
  }

  public void setDataProviderId(String dataProviderId) {
    this.dataProviderId = dataProviderId;
  }

  public List<String> getValues() {
    return values;
  }

  public void setValues(List<String> values) {
    this.values = values;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
