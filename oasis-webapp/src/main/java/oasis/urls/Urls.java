package oasis.urls;

import java.net.URI;

import org.immutables.value.Value;

import com.google.common.base.Optional;

@Value.Immutable
public interface Urls {
  Optional<URI> canonicalBaseUri();

  Optional<URI> landingPage();

  Optional<URI> myOasis();

  Optional<URI> myProfile();

  Optional<URI> myApps();

  Optional<URI> myNetwork();
}
