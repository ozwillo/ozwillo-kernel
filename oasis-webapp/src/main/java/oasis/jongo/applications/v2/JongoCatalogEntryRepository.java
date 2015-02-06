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
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;

import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.i18n.LocalizableString;

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
  public Iterable<CatalogEntry> search(final SearchRequest request) {
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
        .skip(request.start())
        .limit(request.limit());
  }

  private static class FindHelper {
    private static final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
        ResourceBundle.Control.FORMAT_DEFAULT);

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
        // Note: we cannot list all CatalogEntry fields because of localized fields.
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

      return new FindHelper(
          Joiner.on(", ").appendTo(new StringBuilder().append("{ "), queryParts.build()).append(" }").toString(),
          params.build().toArray(),
          fields.build(),
          request.start() + request.limit(),
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
    private final int limit;
    private final Optional<ULocale> displayLocale;
    private final Comparator<CatalogEntry> comparator;

    private FindHelper(String query, Object[] queryParams, ImmutableMap<String, Integer> fields,
        int limit, Optional<ULocale> displayLocale) {
      this.query = query;
      this.queryParams = queryParams;
      this.fields = fields;
      this.limit = limit;
      this.displayLocale = displayLocale;
      // XXX: we can't sort on the server due to https://jira.mongodb.org/browse/SERVER-1920,
      // so we're forced to load and then sort everything here, in memory!
      this.comparator = new Comparator<CatalogEntry>() {
        final ULocale locale = FindHelper.this.displayLocale.or(ULocale.ROOT);
        final Collator collator = Collator.getInstance(locale);

        @Override
        public int compare(CatalogEntry o1, CatalogEntry o2) {
          return ComparisonChain.start()
              .compare(o1.getName().get(locale), o2.getName().get(locale), collator)
              .result();
        }
      };
    }

    public Comparator<CatalogEntry> getComparator() {
      return comparator;
    }

    public Iterable<CatalogEntry> findAll(MongoCollection collection, final CatalogEntry.EntryType type) {
      ArrayList<JongoCatalogEntry> results = Lists.newArrayList(collection
          .find(query, queryParams)
          .projection("#", fields)
          .as(JongoCatalogEntry.class)
          .iterator());
      Collections.sort(results, comparator);
      // We can now safely forget about the elements past 'limit':
      if (results.size() > limit) {
        results.subList(limit, results.size()).clear();
      }
      // Now return the results while setting the entry type (lazily).
      return FluentIterable.from(results)
          .transform(new Function<JongoCatalogEntry, CatalogEntry>() {
            @Override
            public CatalogEntry apply(JongoCatalogEntry input) {
              input.setType(type);
              if (displayLocale.isPresent()) {
                input.setName(new LocalizableString(input.getName().get(displayLocale.get())));
                input.setDescription(new LocalizableString(input.getDescription().get(displayLocale.get())));
                input.setIcon(new LocalizableString(input.getIcon().get(displayLocale.get())));
                input.setTos_uri(new LocalizableString(input.getTos_uri().get(displayLocale.get())));
                input.setPolicy_uri(new LocalizableString(input.getPolicy_uri().get(displayLocale.get())));
              }
              return input;
            }
          });
    }
  }
}
