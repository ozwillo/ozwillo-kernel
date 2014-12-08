package oasis.model.applications.v2;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

public class AppInstance extends CommonProperties {
  @Id private String id;
  private String application_id;
  private InstantiationStatus status;
  /** ID of the user who created the instance. */
  private String instantiator_id;
  private Set<NeededScope> needed_scopes;
  private String destruction_uri;
  private String destruction_secret;
  @JsonProperty private Boolean redirect_uri_validation_disabled;

  public AppInstance() {
    status = InstantiationStatus.PENDING;
    needed_scopes = new LinkedHashSet<>();
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public AppInstance(AppInstance other) {
    super(other);
    application_id = other.getApplication_id();
    status = other.getStatus();
    instantiator_id = other.getInstantiator_id();
    needed_scopes = new LinkedHashSet<>(other.getNeeded_scopes().size());
    for (NeededScope scope : other.getNeeded_scopes()) {
      needed_scopes.add(new NeededScope(scope));
    }
    destruction_uri = other.getDestruction_uri();
    destruction_secret = other.getDestruction_secret();
    redirect_uri_validation_disabled = other.redirect_uri_validation_disabled;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getApplication_id() {
    return application_id;
  }

  public void setApplication_id(String application_id) {
    this.application_id = application_id;
  }

  public InstantiationStatus getStatus() {
    return status;
  }

  public void setStatus(InstantiationStatus status) {
    this.status = status;
  }

  public String getInstantiator_id() {
    return instantiator_id;
  }

  public void setInstantiator_id(String instantiator_id) {
    this.instantiator_id = instantiator_id;
  }

  public Set<NeededScope> getNeeded_scopes() {
    return needed_scopes;
  }

  public void setNeeded_scopes(Set<NeededScope> needed_scopes) {
    this.needed_scopes = needed_scopes;
  }

  public String getDestruction_uri() {
    return destruction_uri;
  }

  public void setDestruction_uri(String destruction_uri) {
    this.destruction_uri = destruction_uri;
  }

  public String getDestruction_secret() {
    return destruction_secret;
  }

  public void setDestruction_secret(String destruction_secret) {
    this.destruction_secret = destruction_secret;
  }

  @JsonIgnore
  public boolean isRedirect_uri_validation_disabled() {
    return Boolean.TRUE.equals(redirect_uri_validation_disabled);
  }

  public void unsetRedirect_uri_validation_disabled() {
    this.redirect_uri_validation_disabled = null;
  }

  public static class NeededScope {
    private String scope_id;
    private LocalizableString motivation;

    public NeededScope() {
      motivation = new LocalizableString();
    }

    public NeededScope(NeededScope other) {
      scope_id = other.getScope_id();
      motivation = new LocalizableString(other.getMotivation());
    }

    public String getScope_id() {
      return scope_id;
    }

    public void setScope_id(String scope_id) {
      this.scope_id = scope_id;
    }

    public LocalizableString getMotivation() {
      return motivation;
    }

    public void setMotivation(LocalizableString motivation) {
      this.motivation = motivation;
    }
  }

  public enum InstantiationStatus {
    PENDING, RUNNING
  }
}
