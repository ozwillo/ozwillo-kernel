package oasis.jongo.directory;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.directory.Group;

@JsonRootName("group")
class JongoGroup extends Group implements HasModified {

  @JsonProperty
  private List<String> agentIds;

  private long modified = System.currentTimeMillis();

  JongoGroup() {
    super();
  }

  JongoGroup(@Nonnull Group other) {
    super(other);
  }

  public List<String> getAgentIds() {
    return agentIds;
  }

  public void setAgentIds(List<String> agentIds) {
    this.agentIds = agentIds;
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
