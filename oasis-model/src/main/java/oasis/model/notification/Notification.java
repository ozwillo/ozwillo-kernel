package oasis.model.notification;

import javax.annotation.Nonnull;

import org.joda.time.Instant;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

public class Notification {
  public enum Status {
    READ,
    UNREAD
  }

  @Id
  private String id;
  private String user_id;
  private String instance_id;
  private String service_id;
  private LocalizableString message;
  private LocalizableString action_uri;
  private LocalizableString action_label;
  private Instant time;
  private Status status = Status.UNREAD;

  public Notification() {
    message = new LocalizableString();
    action_uri = new LocalizableString();
    action_label = new LocalizableString();
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Notification(@Nonnull Notification other) {
    this.user_id = other.getUser_id();
    this.instance_id = other.getInstance_id();
    this.service_id = other.getService_id();
    this.message = new LocalizableString(other.getMessage());
    this.action_uri = new LocalizableString(other.getAction_uri());
    this.action_label = new LocalizableString(other.getAction_label());
    this.time = other.getTime();
    this.status = other.getStatus();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUser_id() {
    return user_id;
  }

  public void setUser_id(String user_id) {
    this.user_id = user_id;
  }

  public String getInstance_id() {
    return instance_id;
  }

  public void setInstance_id(String instance_id) {
    this.instance_id = instance_id;
  }

  public String getService_id() {
    return service_id;
  }

  public void setService_id(String service_id) {
    this.service_id = service_id;
  }

  public LocalizableString getAction_uri() {
    return action_uri;
  }

  public void setAction_uri(LocalizableString action_uri) {
    this.action_uri = action_uri;
  }

  public LocalizableString getAction_label() {
    return action_label;
  }

  public void setAction_label(LocalizableString action_label) {
    this.action_label = action_label;
  }

  public LocalizableString getMessage() {
    return message;
  }

  public void setMessage(LocalizableString message) {
    this.message = message;
  }

  public Instant getTime() {
    return time;
  }

  public void setTime(Instant time) {
    this.time = time;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }
}
