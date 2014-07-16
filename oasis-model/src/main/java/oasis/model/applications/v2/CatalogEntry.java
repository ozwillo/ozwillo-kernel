package oasis.model.applications.v2;

import java.util.ArrayList;
import java.util.List;

import oasis.model.annotations.Id;

public abstract class CatalogEntry extends CommonProperties {
  @Id private String id;
  private PaymentOption payment_option;
  private TargetAudience target_audience;
  private List<String> category_ids;
  private boolean visible;

  protected CatalogEntry() {
    category_ids = new ArrayList<>();
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  protected CatalogEntry(CatalogEntry other) {
    super(other);
    payment_option = other.getPayment_option();
    target_audience = other.getTarget_audience();
    category_ids = new ArrayList<>(other.getCategory_ids());
    visible = other.isVisible();
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

  public TargetAudience getTarget_audience() {
    return target_audience;
  }

  public void setTarget_audience(TargetAudience target_audience) {
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
