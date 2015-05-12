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
