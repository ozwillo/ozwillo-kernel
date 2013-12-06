package oasis.jongo.applications;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.Application;

@JsonRootName("application")
class JongoApplication extends Application implements HasModified {

  @JsonProperty
  private List<JongoDataProvider> dataProviders;

  @JsonProperty
  private JongoServiceProvider serviceProvider;

  @JsonProperty
  private List<JongoSubscription> subscriptions;

  private long modified = System.currentTimeMillis();

  JongoApplication() {
    super();
  }

  JongoApplication(@Nonnull Application other) {
    super(other);
  }

  public List<JongoDataProvider> getDataProviders() {
    return dataProviders;
  }

  public void setDataProviders(List<JongoDataProvider> dataProviders) {
    this.dataProviders = dataProviders;
  }

  public JongoServiceProvider getServiceProvider() {
    return serviceProvider;
  }

  public void setServiceProvider(JongoServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  public List<JongoSubscription> getSubscriptions() {
    return subscriptions;
  }

  public void setSubscriptions(List<JongoSubscription> subscriptions) {
    this.subscriptions = subscriptions;
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }

}
