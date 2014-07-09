package oasis.jongo.applications;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.google.common.collect.ImmutableList;

import oasis.jongo.etag.HasModified;
import oasis.jongo.eventbus.JongoSubscription;
import oasis.model.applications.Application;

@JsonRootName("application")
public class JongoApplication extends Application implements HasModified {

  @JsonProperty
  private ImmutableList<JongoDataProvider> dataProviders = ImmutableList.of();

  @JsonProperty
  private JongoServiceProvider serviceProvider;

  @JsonProperty
  private ImmutableList<JongoSubscription> subscriptions = ImmutableList.of();

  private long modified = System.currentTimeMillis();

  JongoApplication() {
    super();
  }

  JongoApplication(@Nonnull Application other) {
    super(other);
  }

  public ImmutableList<JongoDataProvider> getDataProviders() {
    return dataProviders;
  }

  public void setDataProviders(List<JongoDataProvider> dataProviders) {
    this.dataProviders = ImmutableList.copyOf(dataProviders);
  }

  public JongoServiceProvider getServiceProvider() {
    return serviceProvider;
  }

  public void setServiceProvider(JongoServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  public ImmutableList<JongoSubscription> getSubscriptions() {
    return subscriptions;
  }

  public void setSubscriptions(List<JongoSubscription> subscriptions) {
    this.subscriptions = ImmutableList.copyOf(subscriptions);
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }

}
