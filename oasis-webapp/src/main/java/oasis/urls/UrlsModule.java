package oasis.urls;

import java.net.URI;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

public class UrlsModule extends AbstractModule {

  private final Urls urls;

  public UrlsModule(Urls urls) {
    this.urls = urls;
  }

  public static UrlsModule create(Config config) {
    return new UrlsModule(ImmutableUrls.builder()
        .canonicalBaseUri(get(config, "canonical-base-uri"))
        .landingPage(get(config, "landing-page"))
        .build());
  }

  private static Optional<URI> get(Config config, String key) {
    if (!config.hasPath(key)) {
      return Optional.absent();
    }
    return Optional.of(URI.create(config.getString(key)));
  }

  @Override
  protected void configure() {
    bind(Urls.class).toInstance(urls);
  }
}
