package oasis.jongo.directory;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.google.common.collect.ImmutableList;

import oasis.jongo.etag.HasModified;
import oasis.model.directory.Group;

@JsonRootName("group")
class JongoGroup extends Group implements HasModified {

  @JsonProperty
  private ImmutableList<String> agentIds = ImmutableList.of();

  private long modified = System.currentTimeMillis();

  JongoGroup() {
    super();
  }

  JongoGroup(@Nonnull Group other) {
    super(other);
  }

  public ImmutableList<String> getAgentIds() {
    return agentIds;
  }

  public void setAgentIds(List<String> agentIds) {
    this.agentIds = ImmutableList.copyOf(agentIds);
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
