package oasis.model.applications;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("scopeCardinalities")
public class ScopeCardinalities {

  private String serviceProviderId;

  @JsonProperty
  @ApiModelProperty()
  private List<ScopeCardinality> values;

  private long modified;

  public String getServiceProviderId() {
    return serviceProviderId;
  }

  public void setServiceProviderId(String serviceProviderId) {
    this.serviceProviderId = serviceProviderId;
  }

  public List<ScopeCardinality> getValues() {
    return values;
  }

  public void setValues(List<ScopeCardinality> values) {
    this.values = values;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
