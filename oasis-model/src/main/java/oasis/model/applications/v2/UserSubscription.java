package oasis.model.applications.v2;

import oasis.model.annotations.Id;

public class UserSubscription {
  @Id
  private String id;
  private String service_id;
  private String user_id;
  private SubscriptionType subscription_type;
  private String creator_id;

  public UserSubscription() {
  }

  /**
   * Copy Constructor.
   * <p>
   * Does not copy the {@link #id} field.
   */
  public UserSubscription(UserSubscription other) {
    service_id = other.getService_id();
    user_id = other.getUser_id();
    subscription_type = other.getSubscription_type();
    creator_id = other.getCreator_id();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getService_id() {
    return service_id;
  }

  public void setService_id(String service_id) {
    this.service_id = service_id;
  }

  public String getUser_id() {
    return user_id;
  }

  public void setUser_id(String user_id) {
    this.user_id = user_id;
  }

  public SubscriptionType getSubscription_type() {
    return subscription_type;
  }

  public void setSubscription_type(SubscriptionType subscription_type) {
    this.subscription_type = subscription_type;
  }

  public void setCreator_id(String creator_id) {
    this.creator_id = creator_id;
  }

  public String getCreator_id() {
    return creator_id;
  }

  public enum SubscriptionType {
    PERSONAL,
    ORGANIZATION
  }
}
