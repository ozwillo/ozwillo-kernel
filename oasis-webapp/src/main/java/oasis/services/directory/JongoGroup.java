package oasis.services.directory;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.directory.Group;

@JsonRootName("group")
class JongoGroup extends Group {

  @JsonProperty
  @ApiModelProperty
  private List<String> agentIds;

  @JsonProperty
  @ApiModelProperty
  private long modified = System.currentTimeMillis();

  JongoGroup() {
    super();
  }

  JongoGroup(Group other) {
    super(other);
  }

  public List<String> getAgentIds() {
    return agentIds;
  }

  public void setAgentIds(List<String> agentIds) {
    this.agentIds = agentIds;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
