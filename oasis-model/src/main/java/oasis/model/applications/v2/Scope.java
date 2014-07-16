package oasis.model.applications.v2;

import javax.annotation.Nullable;

import oasis.model.i18n.LocalizableString;

public class Scope {
  private String id;
  @Nullable private String instance_id;
  private String local_id;
  private LocalizableString name = new LocalizableString();
  private LocalizableString description = new LocalizableString();

  public String getId() {
    if (id == null) {
      computeId();
    }
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

  public String getLocal_id() {
    return local_id;
  }

  public void setLocal_id(String local_id) {
    this.local_id = local_id;
  }

  public LocalizableString getName() {
    return name;
  }

  public void setName(LocalizableString name) {
    this.name = name;
  }

  public LocalizableString getDescription() {
    return description;
  }

  public void setDescription(LocalizableString description) {
    this.description = description;
  }

  public void computeId() {
    id = computeId(instance_id, local_id);
  }

  public static String computeId(@Nullable String instance_id, String local_id) {
    return instance_id == null ? local_id : instance_id + ":" + local_id;
  }
}
