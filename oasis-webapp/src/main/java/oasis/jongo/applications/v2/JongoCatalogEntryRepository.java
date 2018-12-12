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
package oasis.jongo.applications.v2;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.immutables.value.Value;
import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.primitives.Longs;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.LocalePriorityList;
import com.ibm.icu.util.ULocale;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.SimpleCatalogEntry;
import oasis.model.bootstrap.ClientIds;

@Value.Enclosing
public class JongoCatalogEntryRepository implements CatalogEntryRepository, JongoBootstrapper {

  private final Jongo jongo;

  @Inject
  JongoCatalogEntryRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getApplicationsCollection() {
    return jongo.getCollection(JongoApplicationRepository.APPLICATIONS_COLLECTION);
  }

  private MongoCollection getServicesCollection() {
    return jongo.getCollection(JongoServiceRepository.SERVICES_COLLECTION);
  }

  static <T extends CatalogEntry> T addPortal(MongoCollection collection, Class<T> clazz, String type, String catalogEntryId, String portalId, long[] versions) throws InvalidVersionException {
    T result = collection.findAndModify("{ id: #, modified: { $in: # } }", catalogEntryId, Longs.asList(versions))
        .with("{ $addToSet: { portals: # } }", portalId)
        .returnNew()
        .as(clazz);
    if (result == null) {
      if (collection.count("{ id: # }", catalogEntryId) > 0) {
        throw new InvalidVersionException(type, catalogEntryId);
      }
      return null;
    }
    return result;
  }

  static <T extends CatalogEntry> T removePortal(MongoCollection collection, Class<T> clazz, String type, String catalogEntryId, String portalId, long[] versions) throws InvalidVersionException {
    T result = collection.findAndModify("{ id: #, modified: { $in: # } }", catalogEntryId, Longs.asList(versions))
        .with("{ $pull: { portals: # } }", portalId)
        .returnNew()
        .as(clazz);
    if (result == null) {
      if (collection.count("{ id: # }", catalogEntryId) > 0) {
        throw new InvalidVersionException(type, catalogEntryId);
      }
      return null;
    }
    return result;
  }

  @Override
  public void bootstrap() {
    Stream.of(getApplicationsCollection(), getServicesCollection()).forEach(collection -> {
      collection.update("{ portals: { $exists: false } }")
          .multi()
          .with("{ $set: { portals: [ # ] } }", ClientIds.PORTAL);

      collection.ensureIndex("{ portals: 1 }");
    });
  }

  @Override
  public Iterable<SimpleCatalogEntry> search(final SearchRequest request) {
    final FindHelper findHelper = FindHelper.create(request);
    return Stream.concat(
        findHelper.findAll(getApplicationsCollection(), CatalogEntry.EntryType.APPLICATION),
        findHelper.findAll(getServicesCollection(), CatalogEntry.EntryType.SERVICE)
    )
        .filter(findHelper.getSupportedLocalesPredicate())
        .sorted(findHelper.getComparator())
        .map(findHelper.getDisplayLocaleFunction())
        .skip(request.start())
        .limit(request.limit())
        ::iterator;
  }

  private static class FindHelper {
    private static final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
        ResourceBundle.Control.FORMAT_DEFAULT);
    private static final ULocale UNKNOWN_LOCALE = new ULocale("und");

    static FindHelper create(final SearchRequest request) {
      StringJoiner query = new StringJoiner(", ", "{ ", " }");
      Stream.Builder<Object> params = Stream.builder();
      addIfNotEmpty(request.geographical_area(), "geographical_areas", query, params);
      addIfNotEmpty(request.restricted_area(), "restricted_areas", query, params);
      addIfNotEmpty(request.target_audience(), "target_audience", query, params);
      addIfNotEmpty(request.payment_option(), "payment_option", query, params);
      addIfNotEmpty(request.category_id(), "category_ids", query, params);
      if (!Strings.isNullOrEmpty(request.portal())) {
        query.add("portals: #");
        params.add(request.portal());
      }
      // TODO: return entries with visible:false but which are visible to the current user
      // TODO: return entries with visible:false / visibility:HIDDEN/NEVER_VISIBLE but which are visible to the current user
      // visible:true is for applications (and old services); visibility:'VISIBLE' for newer services
      query.add("$or: [ { visible: true }, { visibility: 'VISIBLE' } ]");
      query.add("status: { $ne: # }");
      params.add(Service.Status.NOT_AVAILABLE);

      ImmutableMap.Builder<String, Integer> fields = ImmutableMap.builder();
      if (request.displayLocale() != null) {
        // Include all catalog entry fields.
        // (EXCEPT 'visible', explicitly set to 'true' later; see above wrt visible vs. visibility)
        // For localizable fields, include derivatives computed from the requested locale (e.g. if the
        // requested locale is fr-FR, include "name", "name#fr", and "name#fr-FR").
        // TODO: compute from model classes instead of hard-coding the list.
        fields.put("id", 1);
        fields.put("provider_id", 1);
        fields.put("supported_locales", 1);
        fields.put("geographical_areas", 1);
        fields.put("restricted_areas", 1);
        fields.put("payment_option", 1);
        fields.put("target_audience", 1);
        fields.put("category_ids", 1);
        fields.put("contacts", 1);
        fields.put("screenshot_uris", 1);
        fields.put("portals", 1);
        fields.put("modified", 1); // used to compute ETag eventually
        for (Locale candidateLocale : control.getCandidateLocales("", request.displayLocale().toLocale())) {
          final String suffix = Locale.ROOT.equals(candidateLocale)
              ? ""
              : "#" + candidateLocale.toLanguageTag();
          fields.put("name" + suffix, 1);
          fields.put("description" + suffix, 1);
          fields.put("icon" + suffix, 1);
          fields.put("tos_uri" + suffix, 1);
          fields.put("policy_uri" + suffix, 1);
        }
      } else {
        // Exclude all application- and service-specific fields.
        // TODO: compute from model classes instead of hard-coding the list.
        // Note: we cannot list all IndexedCatalogEntry fields because of localized fields.
        fields.put("instantiation_uri", 0);
        fields.put("instantiation_secret", 0);
        fields.put("cancellation_uri", 0);
        fields.put("cancellation_secret", 0);

        fields.put("local_id", 0);
        fields.put("instance_id", 0);
        fields.put("service_uri", 0);
        fields.put("notification_uri", 0);
        fields.put("redirect_uris", 0);
        fields.put("post_logout_redirect_uris", 0);
        fields.put("subscription_uri", 0);
        fields.put("subscription_secret", 0);
      }

      final @Nullable LocalePriorityList supportedLocales;
      if (request.supported_locale().isEmpty()) {
        supportedLocales = null;
      } else {
        supportedLocales = LocalePriorityList.add(Iterables.toArray(request.supported_locale(), ULocale.class)).build();
      }

      return new FindHelper(
          query.toString(),
          params.build().toArray(),
          fields.build(),
          supportedLocales,
          request.displayLocale()
      );
    }

    private static void addIfNotEmpty(Collection<?> col, String fieldName,
        StringJoiner queryParts, Stream.Builder<Object> params) {
      if (col.isEmpty()) {
        return;
      }
      queryParts.add("#: { $in: # }");
      params.add(fieldName).add(col);
    }

    private final String query;
    private final Object[] queryParams;
    private final ImmutableMap<String, Integer> fields;
    private final Comparator<SimpleCatalogEntry> comparator;
    private final Predicate<SimpleCatalogEntry> supportedLocalesPredicate;
    private final Function<SimpleCatalogEntry, SimpleCatalogEntry> displayLocaleFunction;

    private FindHelper(String query, Object[] queryParams, ImmutableMap<String, Integer> fields,
        final @Nullable LocalePriorityList supportedLocales, final @Nullable ULocale displayLocale) {
      this.query = query;
      this.queryParams = queryParams;
      this.fields = fields;
      // XXX: we can't sort on the server due to https://jira.mongodb.org/browse/SERVER-1920,
      // so we're forced to load and then sort everything here, in memory!
      this.comparator = new Comparator<SimpleCatalogEntry>() {
        final ULocale locale = displayLocale == null ? ULocale.ROOT : displayLocale;
        final Collator collator = Collator.getInstance(locale);

        @Override
        public int compare(SimpleCatalogEntry o1, SimpleCatalogEntry o2) {
          return ComparisonChain.start()
              .compare(o1.getName().get(locale), o2.getName().get(locale), collator)
              .result();
        }
      };
      this.supportedLocalesPredicate = supportedLocales == null
          ? Predicates.alwaysTrue()
          : input -> {
              if (input.getSupported_locales() == null || input.getSupported_locales().isEmpty()) {
                // Entries without supported_locales are NOT listed at all when filtering on that field!
                return false;
              }
              // UNKNOWN_LOCALE is the first, so will be returned if nothing else matches.
              LocalePriorityList.Builder entrySupportedLocales = LocalePriorityList.add(UNKNOWN_LOCALE);
              for (ULocale locale : input.getSupported_locales()) {
                if (UNKNOWN_LOCALE.equals(locale)) {
                  continue; // Don't add UNKNOWN_LOCALE twice, it would no longer be first
                }
                entrySupportedLocales.add(locale);
              }
              return !UNKNOWN_LOCALE.equals(
                  new LocaleMatcher(entrySupportedLocales.build()).getBestMatch(supportedLocales));
            };
      this.displayLocaleFunction = displayLocale == null
          ? Function.identity()
          : catalogEntry -> {
              catalogEntry.restrictLocale(displayLocale);
              return catalogEntry;
            };
    }

    public Comparator<SimpleCatalogEntry> getComparator() {
      return comparator;
    }

    public Predicate<SimpleCatalogEntry> getSupportedLocalesPredicate() {
      return supportedLocalesPredicate;
    }

    public Function<SimpleCatalogEntry, SimpleCatalogEntry> getDisplayLocaleFunction() {
      return displayLocaleFunction;
    }

    public Stream<SimpleCatalogEntry> findAll(MongoCollection collection, final CatalogEntry.EntryType type) {
      return Streams.stream(
          collection
              .find(query, queryParams)
              .projection("#", fields)
              .as(SimpleCatalogEntry.class)
              .iterator())
          .peek(input -> {
            input.setType(type);
            // XXX: also forcibly set visible to true (see above wrt visible vs. visibility)
            input.setVisible(true);
          });
    }
  }
}
