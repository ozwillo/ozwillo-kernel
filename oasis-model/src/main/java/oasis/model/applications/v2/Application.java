package oasis.model.applications.v2;

public class Application extends CatalogEntry {
  private String instantiation_uri;
  private String instantiation_secret;
  private String cancellation_uri;
  private String cancellation_secret;

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
    cancellation_uri = other.getCancellation_uri();
    cancellation_secret = other.getCancellation_secret();
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

  public String getCancellation_uri() {
    return cancellation_uri;
  }

  public void setCancellation_uri(String cancellation_uri) {
    this.cancellation_uri = cancellation_uri;
  }

  public String getCancellation_secret() {
    return cancellation_secret;
  }

  public void setCancellation_secret(String cancellation_secret) {
    this.cancellation_secret = cancellation_secret;
  }
}
