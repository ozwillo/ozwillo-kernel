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
package oasis.userdirectory;

import java.util.concurrent.TimeUnit;

import org.immutables.value.Value;
import org.joda.time.Duration;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

@Value.Nested
public class UserDirectoryModule extends AbstractModule {
  @Value.Immutable
  public static interface Settings {
    Duration invitationTokenDuration();
  }

  public static UserDirectoryModule create(Config config) {
    return new UserDirectoryModule(ImmutableUserDirectoryModule.Settings.builder()
        .invitationTokenDuration(Duration.millis(config.getDuration("invitation-token-duration", TimeUnit.MILLISECONDS)))
        .build());
  }

  private final Settings settings;

  public UserDirectoryModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }
}
