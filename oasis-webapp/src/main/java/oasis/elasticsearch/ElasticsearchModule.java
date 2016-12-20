/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
package oasis.elasticsearch;

import java.net.URI;

import org.immutables.value.Value;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

@Value.Enclosing
public class ElasticsearchModule extends AbstractModule {

  public static final String ELASTICSEARCH = "elasticsearch";

  @Value.Immutable
  public interface Settings {
    URI url();
  }

  public static ElasticsearchModule create(Config config) {
    return new ElasticsearchModule(ImmutableElasticsearchModule.Settings.builder()
        .url(URI.create(config.getString("url")))
        .build());
  }

  private final Settings settings;

  public ElasticsearchModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }
}
