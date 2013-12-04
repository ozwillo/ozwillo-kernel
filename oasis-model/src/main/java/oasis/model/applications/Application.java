package oasis.model.applications;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonRootName("application")
public class Application {
  public enum ApplicationType {
    CLASS, INSTANCE
  }

  public enum InstantiationType {
    COPY, TENANT
  }

  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String name;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String iconUri;

  @JsonProperty
  @ApiModelProperty
  private List<DataProvider> dataProviders;

  @JsonProperty
  @ApiModelProperty
  private ServiceProvider serviceProvider;

  @JsonProperty
  @ApiModelProperty
  private List<Subscription> subscriptions;

  @JsonProperty
  @ApiModelProperty
  private String instanceAdmin;

  @JsonProperty
  @ApiModelProperty
  private String classAdmin;

  /**
   * Applications exposed in catalog:
   *  - template application (ApplicationType == CLASS)
   *  - single-instance application (ApplicationType == INSTANCE && parentApplicationId == null)
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

  @JsonProperty
  @ApiModelProperty
  private long modified;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIconUri() {
    return iconUri;
  }

  public void setIconUri(String iconUri) {
    this.iconUri = iconUri;
  }

  public List<DataProvider> getDataProviders() {
    return dataProviders;
  }

  public void setDataProviders(List<DataProvider> dataProviders) {
    this.dataProviders = dataProviders;
  }

  public ServiceProvider getServiceProvider() {
    return serviceProvider;
  }

  public void setServiceProvider(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }


  public List<Subscription> getSubscriptions() {
    return subscriptions;
  }

  public void setSubscriptions(List<Subscription> subscriptions) {
    this.subscriptions = subscriptions;
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

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }

  public boolean isTenant(){
    return (ApplicationType.INSTANCE == applicationType && InstantiationType.TENANT == instantiationType);
  }

  public void computeExposition() {
    this.exposedInCatalog = (Application.ApplicationType.CLASS == this.applicationType || this.parentApplicationId == null);
  }
}
