package oasis.model.applications.v2;

public class Application extends CatalogEntry {
  private String instantiation_uri;
  private String instantiation_secret;

  public Application() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Application(Application other) {
    super(other);
    instantiation_uri = other.getInstantiation_uri();
    instantiation_secret = other.getInstantiation_secret();
  }

  @Override
  public EntryType getType() {
    return EntryType.APPLICATION;
  }

  public String getInstantiation_uri() {
    return instantiation_uri;
  }

  public void setInstantiation_uri(String instantiation_uri) {
    this.instantiation_uri = instantiation_uri;
  }

  public String getInstantiation_secret() {
    return instantiation_secret;
  }

  public void setInstantiation_secret(String instantiation_secret) {
    this.instantiation_secret = instantiation_secret;
  }
}
