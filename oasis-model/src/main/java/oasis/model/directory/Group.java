package oasis.model.directory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("group")
public class Group {

  @JsonProperty
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String name;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String organizationId;

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

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }
}
