package oasis.jongo.applications.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.immutables.value.Value;
import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.LocalePriorityList;
import com.ibm.icu.util.ULocale;

import oasis.model.applications.v2.SimpleCatalogEntry;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.Service;

@Value.Nested
public class JongoCatalogEntryRepository implements CatalogEntryRepository {

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

  @Override
  public Iterable<SimpleCatalogEntry> search(final SearchRequest request) {
    final FindHelper findHelper = FindHelper.create(request);
    return FluentIterable.from(
        Iterables.mergeSorted(
            Arrays.asList(
                findHelper.findAll(getApplicationsCollection(), CatalogEntry.EntryType.APPLICATION),
                findHelper.findAll(getServicesCollection(), CatalogEntry.EntryType.SERVICE)
            ),
            findHelper.getComparator()
        )
    )
        .filter(findHelper.getSupportedLocalesPredicate())
        .transform(findHelper.getDisplayLocaleFunction())
        .skip(request.start())
        .limit(request.limit());
  }

  private static class FindHelper {
    private static final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
        ResourceBundle.Control.FORMAT_DEFAULT);
    private static final ULocale UNKNOWN_LOCALE = new ULocale("und");

    static FindHelper create(final SearchRequest request) {
      ImmutableList.Builder<String> queryParts = ImmutableList.builder();
      ImmutableList.Builder<Object> params = ImmutableList.builder();
      addIfNotEmpty(request.geographical_area(), "geographical_areas", queryParts, params);
      addIfNotEmpty(request.restricted_area(), "restricted_areas", queryParts, params);
      addIfNotEmpty(request.target_audience(), "target_audience", queryParts, params);
      addIfNotEmpty(request.payment_option(), "payment_option", queryParts, params);
      addIfNotEmpty(request.category_id(), "category_ids", queryParts, params);
      // TODO: return entries with visible:false but which are visible to the current user
      queryParts.add("visible: true");
      queryParts.add("status: { $ne: # } }");
      params.add(Service.Status.NOT_AVAILABLE);

      ImmutableMap.Builder<String, Integer> fields = ImmutableMap.builder();
      if (request.displayLocale().isPresent()) {
        // Include all catalog entry fields.
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
        fields.put("visible", 1);
        fields.put("contacts", 1);
        fields.put("screenshot_uris", 1);
        fields.put("modified", 1); // used to compute ETag eventually
        for (Locale candidateLocale : control.getCandidateLocales("", request.displayLocale().get().toLocale())) {
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

      final Optional<LocalePriorityList> supportedLocales;
      if (request.supported_locale().isEmpty()) {
        supportedLocales = Optional.absent();
      } else {
        supportedLocales = Optional.of(LocalePriorityList.add(Iterables.toArray(request.supported_locale(), ULocale.class)).build());
      }

      return new FindHelper(
          Joiner.on(", ").appendTo(new StringBuilder().append("{ "), queryParts.build()).append(" }").toString(),
          params.build().toArray(),
          fields.build(),
          supportedLocales,
          request.displayLocale()
      );
    }

    private static void addIfNotEmpty(Collection<?> col, String fieldName,
        ImmutableList.Builder<String> queryParts, ImmutableList.Builder<Object> params) {
      if (col.isEmpty()) {
        return;
      }
      queryParts.add("#: { $in: # }");
      params.add(fieldName, col);
    }

    private final String query;
    private final Object[] queryParams;
    private final ImmutableMap<String, Integer> fields;
    private final Comparator<SimpleCatalogEntry> comparator;
    private final Predicate<SimpleCatalogEntry> supportedLocalesPredicate;
    private final Function<SimpleCatalogEntry, SimpleCatalogEntry> displayLocaleFunction;

    private FindHelper(String query, Object[] queryParams, ImmutableMap<String, Integer> fields,
        final Optional<LocalePriorityList> supportedLocales, final Optional<ULocale> displayLocale) {
      this.query = query;
      this.queryParams = queryParams;
      this.fields = fields;
      // XXX: we can't sort on the server due to https://jira.mongodb.org/browse/SERVER-1920,
      // so we're forced to load and then sort everything here, in memory!
      this.comparator = new Comparator<SimpleCatalogEntry>() {
        final ULocale locale = displayLocale.or(ULocale.ROOT);
        final Collator collator = Collator.getInstance(locale);

        @Override
        public int compare(SimpleCatalogEntry o1, SimpleCatalogEntry o2) {
          return ComparisonChain.start()
              .compare(o1.getName().get(locale), o2.getName().get(locale), collator)
              .result();
        }
      };
      this.supportedLocalesPredicate = supportedLocales.isPresent()
          ? new Predicate<SimpleCatalogEntry>() {
              final LocalePriorityList requestedLocales = supportedLocales.get();

              @Override
              public boolean apply(SimpleCatalogEntry input) {
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
                    new LocaleMatcher(entrySupportedLocales.build()).getBestMatch(this.requestedLocales));
              }
            }
          : Predicates.<SimpleCatalogEntry>alwaysTrue();
      this.displayLocaleFunction = displayLocale.isPresent()
          ? new Function<SimpleCatalogEntry, SimpleCatalogEntry>() {
              final ULocale locale = displayLocale.get();

              @Override
              public SimpleCatalogEntry apply(SimpleCatalogEntry catalogEntry) {
                catalogEntry.restrictLocale(locale);
                return catalogEntry;
              }
            }
          : Functions.<SimpleCatalogEntry>identity();
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

    public Iterable<SimpleCatalogEntry> findAll(MongoCollection collection, final CatalogEntry.EntryType type) {
      ArrayList<SimpleCatalogEntry> results = Lists.newArrayList(collection
          .find(query, queryParams)
          .projection("#", fields)
          .as(SimpleCatalogEntry.class)
          .iterator());
      Collections.sort(results, comparator);
      // Now return the results while setting the entry type (lazily).
      return FluentIterable.from(results)
          .transform(new Function<SimpleCatalogEntry, SimpleCatalogEntry>() {
            @Override
            public SimpleCatalogEntry apply(SimpleCatalogEntry input) {
              input.setType(type);
              return input;
            }
          });
    }
  }
}
