/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.model.applications.v2;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Service extends CatalogEntry {
  private String local_id;
  private String instance_id;
  private boolean visible;
  @JsonProperty private Boolean restricted;
  private String service_uri;
  private String notification_uri;
  private Set<String> redirect_uris;
  private Set<String> post_logout_redirect_uris;
  private String subscription_uri;
  private String subscription_secret;
  private Status status;

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
    status = other.getStatus();
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
  public boolean isAccessRestricted() {
    return !isVisible();
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
    return visible && !Boolean.TRUE.equals(getRestricted());
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
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

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Visibility getVisibility() {
    if (Boolean.TRUE.equals(getRestricted())) {
      return Visibility.NEVER_VISIBLE;
    }
    return isVisible() ? Visibility.VISIBLE : Visibility.HIDDEN;
  }

  public AccessControl getAccess_control() {
    if (Boolean.TRUE.equals(getRestricted())) {
      return AccessControl.ALWAYS_RESTRICTED;
    }
    return isVisible() ? AccessControl.ANYONE : AccessControl.RESTRICTED;
  }

  public static enum Status {
    AVAILABLE, NOT_AVAILABLE;

    public static Status forAppInstanceStatus(AppInstance.InstantiationStatus instantiationStatus) {
      switch (instantiationStatus) {
        case RUNNING:
          return AVAILABLE;
        case PENDING:
          throw new IllegalArgumentException("Pending app-instance shouldn't have services...");
        case STOPPED:
        default:
          return NOT_AVAILABLE;
      }
    }
  }

  public static enum Visibility {
    VISIBLE,
    HIDDEN,
    NEVER_VISIBLE;
  }

  public static enum AccessControl {
    ANYONE,
    RESTRICTED,
    ALWAYS_RESTRICTED;
  }
}
