package oasis.model.applications.v2;

import oasis.model.i18n.LocalizableString;

public abstract class CommonProperties {
  private LocalizableString name;
  private LocalizableString description;
  private LocalizableString icon;
  private String provider_id;

  protected CommonProperties() {
    name = new LocalizableString();
    description = new LocalizableString();
    icon = new LocalizableString();
  }

  /**
   * Copy constructor.
   */
  protected CommonProperties(CommonProperties other) {
    name = new LocalizableString(other.getName());
    description = new LocalizableString(other.getDescription());
    icon = new LocalizableString(other.getIcon());
    provider_id = other.getProvider_id();
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

  public LocalizableString getIcon() {
    return icon;
  }

  public void setIcon(LocalizableString icon) {
    this.icon = icon;
  }

  public String getProvider_id() {
    return provider_id;
  }

  public void setProvider_id(String provider_id) {
    this.provider_id = provider_id;
  }
}
