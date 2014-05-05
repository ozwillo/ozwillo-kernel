package oasis.model.applications;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

@JsonRootName("application")
public class Application {
  public enum ApplicationType {
    CLASS,
    INSTANCE
  }

  public enum InstantiationType {
    COPY,
    TENANT
  }

  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty(required = true)
  private LocalizableString name = new LocalizableString();

  @JsonProperty
  @ApiModelProperty(required = true)
  private String iconUri; // XXX: should this be localizable?

  @JsonProperty
  @ApiModelProperty
  private String instanceAdmin;

  @JsonProperty
  @ApiModelProperty
  private String classAdmin;

  /**
   * Applications exposed in catalog:
   * - template application (ApplicationType == CLASS)
   * - single-instance application (ApplicationType == INSTANCE && parentApplicationId == null)
   */
  @JsonProperty
  @ApiModelProperty
  private boolean exposedInCatalog;

  @JsonProperty
  @ApiModelProperty
  private ApplicationType applicationType;

  @JsonProperty
  @ApiModelProperty
  private InstantiationType instantiationType;

  @JsonProperty
  @ApiModelProperty
  private String parentApplicationId;

  public Application() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Application(@Nonnull Application other) {
    this.name = new LocalizableString(other.getName());
    this.iconUri = other.getIconUri();
    this.instanceAdmin = other.getInstanceAdmin();
    this.classAdmin = other.getClassAdmin();
    this.exposedInCatalog = other.isExposedInCatalog();
    this.applicationType = other.getApplicationType();
    this.instantiationType = other.getInstantiationType();
    this.parentApplicationId = other.getParentApplicationId();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public LocalizableString getName() {
    return name.unmodifiable();
  }

  public void setName(LocalizableString name) {
    this.name = new LocalizableString(name);
  }

  public String getIconUri() {
    return iconUri;
  }

  public void setIconUri(String iconUri) {
    this.iconUri = iconUri;
  }

  public String getInstanceAdmin() {
    return instanceAdmin;
  }

  public void setInstanceAdmin(String instanceAdmin) {
    this.instanceAdmin = instanceAdmin;
  }

  public String getClassAdmin() {
    return classAdmin;
  }

  public void setClassAdmin(String classAdmin) {
    this.classAdmin = classAdmin;
  }

  public boolean isExposedInCatalog() {
    return exposedInCatalog;
  }

  public void setExposedInCatalog(boolean exposedInCatalog) {
    this.exposedInCatalog = exposedInCatalog;
  }

  public ApplicationType getApplicationType() {
    return applicationType;
  }

  public void setApplicationType(ApplicationType applicationType) {
    this.applicationType = applicationType;
    computeExposition();
  }

  public InstantiationType getInstantiationType() {
    return instantiationType;
  }

  public void setInstantiationType(InstantiationType instantiationType) {
    this.instantiationType = instantiationType;
  }

  public String getParentApplicationId() {
    return parentApplicationId;
  }

  public void setParentApplicationId(String parentApplicationId) {
    this.parentApplicationId = parentApplicationId;
    computeExposition();
  }

  public boolean isTenant() {
    return (ApplicationType.INSTANCE == applicationType && InstantiationType.TENANT == instantiationType);
  }

  public void computeExposition() {
    this.exposedInCatalog = (Application.ApplicationType.CLASS == this.applicationType || this.parentApplicationId == null);
  }
}
