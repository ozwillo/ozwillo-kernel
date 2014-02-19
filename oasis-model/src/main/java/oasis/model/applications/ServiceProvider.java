package oasis.model.applications;

import java.util.ArrayList;
import java.util.Collections;
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
  private List<ScopeCardinality> scopeCardinalities = new ArrayList<>();

  public ServiceProvider() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public ServiceProvider(ServiceProvider other) {
    this.name = other.getName();
    if (other.getScopeCardinalities() != null) {
      this.scopeCardinalities = new ArrayList<>(other.getScopeCardinalities());
    }
  }

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
    return Collections.unmodifiableList(scopeCardinalities);
  }

  public void setScopeCardinalities(List<ScopeCardinality> scopeCardinalities) {
    this.scopeCardinalities = new ArrayList<>(scopeCardinalities);
  }
}
