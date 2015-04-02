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
