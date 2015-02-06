package oasis.model.applications.v2;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.immutables.value.Value;

import com.google.common.base.Optional;
import com.ibm.icu.util.ULocale;

@Value.Nested
public interface CatalogEntryRepository {
  Iterable<CatalogEntry> search(SearchRequest request);

  @Value.Immutable
  interface SearchRequest {
    Optional<ULocale> displayLocale();
    int start();
    int limit();
    // TODO: handle full-text search
    // Optional<String> query();
    List<ULocale> supported_locale();
    Set<URI> geographical_area();
    Set<URI> restricted_area();
    Set<CatalogEntry.TargetAudience> target_audience();
    Set<CatalogEntry.PaymentOption> payment_option();
    Set<String> category_id();
  }
}
