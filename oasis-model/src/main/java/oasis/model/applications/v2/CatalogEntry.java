package oasis.model.applications.v2;

import java.util.ArrayList;
import java.util.List;

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

  protected CatalogEntry() {
    target_audience = new ArrayList<>();
    category_ids = new ArrayList<>();
    tos_uri = new LocalizableString();
    policy_uri = new LocalizableString();
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
