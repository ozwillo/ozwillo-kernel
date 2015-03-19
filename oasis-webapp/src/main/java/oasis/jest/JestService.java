package oasis.jest;

import static com.google.common.base.Preconditions.checkState;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import oasis.elasticsearch.ElasticsearchModule;

@Singleton
public class JestService implements Provider<JestClient> {
  private JestClient client;

  private final ElasticsearchModule.Settings elasticsearchSettings;
  private final Provider<Set<JestBootstrapper>> bootstrappers;

  @Inject JestService(ElasticsearchModule.Settings elasticsearchSettings,
      Provider<Set<JestBootstrapper>> bootstrappers) {
    this.elasticsearchSettings = elasticsearchSettings;
    this.bootstrappers = bootstrappers;
  }

  @Override
  public JestClient get() {
    checkState(client != null, "Thou shalt start tha ElasticSearchService");
    return client;
  }

  public void start() {
    JestClientFactory clientFactory = new JestClientFactory();
    HttpClientConfig clientConfig = new HttpClientConfig.Builder(elasticsearchSettings.url().toString())
        .multiThreaded(true)
        .build();
    clientFactory.setHttpClientConfig(clientConfig);
    client = clientFactory.getObject();

    for (JestBootstrapper bootstrapper : bootstrappers.get()) {
      bootstrapper.bootstrap();
    }
  }

  public void stop() {
    if (client == null) {
      return;
    }
    client.shutdownClient();
  }
}
