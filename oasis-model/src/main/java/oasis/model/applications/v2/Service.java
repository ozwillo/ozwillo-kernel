package oasis.model.applications.v2;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.annotations.Id;

public class Service extends CatalogEntry {
  private String local_id;
  private String instance_id;
  @JsonProperty private Boolean restricted;
  private String service_uri;
  private String notification_uri;
  private Set<String> redirect_uris;
  private Set<String> post_logout_redirect_uris;
  private String subscription_uri;
  private String subscription_secret;

  public Service() {
    redirect_uris = new LinkedHashSet<>();
    post_logout_redirect_uris = new LinkedHashSet<>();
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Service(Service other) {
    super(other);
    local_id = other.getLocal_id();
    instance_id = other.getInstance_id();
    restricted = other.getRestricted();
    service_uri = other.getService_uri();
    notification_uri = other.getNotification_uri();
    redirect_uris = new LinkedHashSet<>(other.getRedirect_uris());
    post_logout_redirect_uris = new LinkedHashSet<>(other.getPost_logout_redirect_uris());
    subscription_uri = other.getSubscription_uri();
    subscription_secret = other.getSubscription_secret();
  }

  public String getLocal_id() {
    return local_id;
  }

  public void setLocal_id(String local_id) {
    this.local_id = local_id;
  }

  @Override
  public EntryType getType() {
    return EntryType.SERVICE;
  }

  @JsonIgnore
  public boolean isRestricted() {
    return Boolean.TRUE.equals(restricted);
  }

  public Boolean getRestricted() {
    return restricted;
  }

  public void setRestricted(Boolean restricted) {
    this.restricted = restricted;
  }

  @Override
  public boolean isVisible() {
    // a restricted service cannot be visible.
    return super.isVisible() && !isRestricted();
  }

  public String getService_uri() {
    return service_uri;
  }

  public void setService_uri(String service_uri) {
    this.service_uri = service_uri;
  }

  public String getNotification_uri() {
    return notification_uri;
  }

  public void setNotification_uri(String notification_uri) {
    this.notification_uri = notification_uri;
  }

  public Set<String> getRedirect_uris() {
    return redirect_uris;
  }

  public void setRedirect_uris(Set<String> redirect_uris) {
    this.redirect_uris = redirect_uris;
  }

  public Set<String> getPost_logout_redirect_uris() {
    return post_logout_redirect_uris;
  }

  public void setPost_logout_redirect_uris(Set<String> post_logout_redirect_uris) {
    this.post_logout_redirect_uris = post_logout_redirect_uris;
  }

  public String getSubscription_uri() {
    return subscription_uri;
  }

  public void setSubscription_uri(String subscription_uri) {
    this.subscription_uri = subscription_uri;
  }

  public String getSubscription_secret() {
    return subscription_secret;
  }

  public void setSubscription_secret(String subscription_secret) {
    this.subscription_secret = subscription_secret;
  }

  public String getInstance_id() {
    return instance_id;
  }

  public void setInstance_id(String instance_id) {
    this.instance_id = instance_id;
  }
}
