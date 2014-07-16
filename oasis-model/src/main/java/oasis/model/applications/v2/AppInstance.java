package oasis.model.applications.v2;

import java.util.LinkedHashSet;
import java.util.Set;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

public class AppInstance extends CommonProperties {
  @Id private String id;
  private String application_id;
  private InstantiationStatus status;
  /** ID of the user who created the instance. */
  private String instantiator_id;
  private Set<NeededScope> needed_scopes;

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
    needed_scopes = new LinkedHashSet<>(other.getNeeded_scopes().size());
    for (NeededScope scope : other.getNeeded_scopes()) {
      needed_scopes.add(new NeededScope(scope));
    }
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
