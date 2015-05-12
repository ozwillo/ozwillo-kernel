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
package oasis.model.applications.v2;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.immutables.value.Value;

import com.google.common.base.Optional;
import com.ibm.icu.util.ULocale;

@Value.Nested
public interface CatalogEntryRepository {
  Iterable<SimpleCatalogEntry> search(SearchRequest request);

  @Value.Immutable
  interface SearchRequest {
    Optional<ULocale> displayLocale();
    int start();
    int limit();
    Optional<String> query();
    List<ULocale> supported_locale();
    Set<URI> geographical_area();
    Set<URI> restricted_area();
    Set<CatalogEntry.TargetAudience> target_audience();
    Set<CatalogEntry.PaymentOption> payment_option();
    Set<String> category_id();
  }
}
