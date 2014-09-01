package oasis.model.applications.v2;

import javax.annotation.Nonnull;

import oasis.model.annotations.Id;

public class AccessControlEntry {
  @Id
  private String id;
  private String instance_id;
  private String user_id;
  private String creator_id;

  public AccessControlEntry() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy the {@link #id} field.
   */
  public AccessControlEntry(@Nonnull AccessControlEntry other) {
    instance_id = other.getInstance_id();
    user_id = other.getUser_id();
    creator_id = other.getCreator_id();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getInstance_id() {
    return instance_id;
  }

  public void setInstance_id(String instance_id) {
    this.instance_id = instance_id;
  }

  public String getUser_id() {
    return user_id;
  }

  public void setUser_id(String user_id) {
    this.user_id = user_id;
  }

  public String getCreator_id() {
    return creator_id;
  }

  public void setCreator_id(String creator_id) {
    this.creator_id = creator_id;
  }
}
