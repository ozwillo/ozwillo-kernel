package oasis.model.applications.v2;

import java.util.ArrayList;
import java.util.List;
import com.ibm.icu.util.ULocale;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

public abstract class CatalogEntry extends CommonProperties {
  @Id private String id;
  private PaymentOption payment_option;
  private List<TargetAudience> target_audience;
  private List<String> category_ids;
  private boolean visible;
  private LocalizableString tos_uri;
  private LocalizableString policy_uri;
  private List<String> contacts;
  private List<String> screenshot_uris;
  private List<ULocale> supported_locales;

  protected CatalogEntry() {
    target_audience = new ArrayList<>();
    category_ids = new ArrayList<>();
    tos_uri = new LocalizableString();
    policy_uri = new LocalizableString();
    contacts = new ArrayList<>();
    screenshot_uris = new ArrayList<>();
    supported_locales = new ArrayList<>();
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  protected CatalogEntry(CatalogEntry other) {
    super(other);
    payment_option = other.getPayment_option();
    target_audience = new ArrayList<>(other.getTarget_audience());
    category_ids = new ArrayList<>(other.getCategory_ids());
    visible = other.isVisible();
    tos_uri = new LocalizableString(other.getTos_uri());
    policy_uri = new LocalizableString(other.getPolicy_uri());
    contacts = new ArrayList<>(other.getContacts());
    screenshot_uris = new ArrayList<>(other.getScreenshot_uris());
    supported_locales = new ArrayList<>(other.getSupported_locales());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public abstract EntryType getType();

  public PaymentOption getPayment_option() {
    return payment_option;
  }

  public void setPayment_option(PaymentOption payment_option) {
    this.payment_option = payment_option;
  }

  public List<TargetAudience> getTarget_audience() {
    return target_audience;
  }

  public void setTarget_audience(List<TargetAudience> target_audience) {
    this.target_audience = target_audience;
  }

  public List<String> getCategory_ids() {
    return category_ids;
  }

  public void setCategory_ids(List<String> category_ids) {
    this.category_ids = category_ids;
  }

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public LocalizableString getTos_uri() {
    return tos_uri;
  }

  public void setTos_uri(LocalizableString tos_uri) {
    this.tos_uri = tos_uri;
  }

  public LocalizableString getPolicy_uri() {
    return policy_uri;
  }

  public void setPolicy_uri(LocalizableString policy_uri) {
    this.policy_uri = policy_uri;
  }

  public List<String> getContacts() {
    return contacts;
  }

  public void setContacts(List<String> contacts) {
    this.contacts = contacts;
  }

  public List<String> getScreenshot_uris() {
    return screenshot_uris;
  }

  public void setScreenshot_uris(List<String> screenshot_uris) {
    this.screenshot_uris = screenshot_uris;
  }

  public List<ULocale> getSupported_locales() {
    return supported_locales;
  }

  public void setSupported_locales(List<ULocale> supported_locales) {
    this.supported_locales = supported_locales;
  }

  public static enum EntryType {
    APPLICATION, SERVICE
  }

  public static enum TargetAudience {
    CITIZENS, PUBLIC_BODIES, COMPANIES
  }

  public static enum PaymentOption {
    FREE, PAID
  }
}
