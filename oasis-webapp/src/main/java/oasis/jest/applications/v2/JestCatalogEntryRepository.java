package oasis.jest.applications.v2;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Strings;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.icu.util.ULocale;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.client.JestResultHandler;
import io.searchbox.core.Delete;
import io.searchbox.core.DeleteByQuery;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.indices.CreateIndex;
import oasis.jest.JestBootstrapper;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.SimpleCatalogEntry;
import oasis.model.i18n.LocalizableModule;
import oasis.model.i18n.LocalizableStringHelper;
import oasis.web.i18n.LocaleHelper;

public class JestCatalogEntryRepository implements CatalogEntryRepository, JestBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(JestCatalogEntryRepository.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .registerModule(new GuavaModule())
      .registerModule(new JodaModule())
      .registerModule(new LocalizableModule())
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final String INDEX_NAME = "catalog-entry";

  private final JestClient jestClient;

  @Inject JestCatalogEntryRepository(JestClient jestClient) {
    this.jestClient = jestClient;
  }

  @Override
  public Iterable<SimpleCatalogEntry> search(SearchRequest request) {
    // Only the query is passed through an elasticsearch query (filters doesn't have full text search and scoring enabled)
    // Other parameters are put into an elasticsearch filter for better performance (i.e. cache, no scoring)
    FilteredQueryBuilder filteredQueryBuilder = QueryBuilders.filteredQuery(generateQuery(request), generateFilter(request));
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(filteredQueryBuilder)
        // TODO: Sort on the name if there isn't any query term
        .from(request.start())
        .size(request.limit());
    Search searchAction = new Search.Builder(searchSourceBuilder.toString())
        .addIndex(INDEX_NAME)
        .build();
    try {
      SearchResult searchResult = jestClient.execute(searchAction);
      if (!searchResult.isSucceeded()) {
        logger.error("Error while searching in elasticsearch: {}", searchResult.getErrorMessage());
        return Collections.emptyList();
      }
      return transformSearchResultToCatalogEntries(searchResult, request.displayLocale());
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private QueryBuilder generateQuery(SearchRequest request) {
    if (!request.query().isPresent()) {
      return QueryBuilders.matchAllQuery();
    }

    // FIXME: Elasticsearch seems to lower the score of entries with missing properties in multi match queries
    List<ULocale> locales = getFallbackLocales(request.displayLocale().or(LocaleHelper.DEFAULT_LOCALE));
    String[] nameFields = new String[locales.size()];
    String[] descriptionFields = new String[locales.size()];
    for (int i = 0; i < locales.size(); i++) {
      nameFields[i] = LocalizableStringHelper.serializeKey("name", locales.get(i));
      descriptionFields[i] = LocalizableStringHelper.serializeKey("description", locales.get(i));
    }
    // TODO: Manage boost of each key depending on its locale
    return QueryBuilders.boolQuery()
        .should(QueryBuilders.multiMatchQuery(request.query().get(), nameFields))
        .should(QueryBuilders.multiMatchQuery(request.query().get(), descriptionFields));
  }

  private FilterBuilder generateFilter(SearchRequest request) {
    BoolFilterBuilder mustFiltersBuilder = FilterBuilders.boolFilter();
    if (!request.payment_option().isEmpty()) {
      BoolFilterBuilder shouldFiltersBuilder = FilterBuilders.boolFilter();
      for (CatalogEntry.PaymentOption payment_option : request.payment_option()) {
        shouldFiltersBuilder.should(FilterBuilders.termFilter("payment_option", payment_option.name()));
      }
      mustFiltersBuilder.must(shouldFiltersBuilder);
    }

    if (!request.target_audience().isEmpty()) {
      BoolFilterBuilder shouldFiltersBuilder = FilterBuilders.boolFilter();
      for (CatalogEntry.TargetAudience target_audience : request.target_audience()) {
        shouldFiltersBuilder.should(FilterBuilders.termFilter("target_audience", target_audience.name()));
      }
      mustFiltersBuilder.must(shouldFiltersBuilder);
    }

    if (!request.category_id().isEmpty()) {
      BoolFilterBuilder shouldFiltersBuilder = FilterBuilders.boolFilter();
      for (String category_id : request.category_id()) {
        shouldFiltersBuilder.should(FilterBuilders.termFilter("category_ids", category_id));
      }
      mustFiltersBuilder.must(shouldFiltersBuilder);
    }

    if (!request.supported_locale().isEmpty()) {
      BoolFilterBuilder shouldFiltersBuilder = FilterBuilders.boolFilter();
      // TODO: Do not just filter on supported locales but also on fallbacks
      for (ULocale locale : request.supported_locale()) {
        shouldFiltersBuilder.should(FilterBuilders.termFilter("supported_locales", locale.toLanguageTag()));
      }
      mustFiltersBuilder.must(shouldFiltersBuilder);
    }

    if (!request.restricted_area().isEmpty()) {
      BoolFilterBuilder shouldFiltersBuilder = FilterBuilders.boolFilter();
      for (URI restricted_area : request.restricted_area()) {
        shouldFiltersBuilder.should(FilterBuilders.termFilter("restricted_areas", restricted_area.toString()));
      }
      mustFiltersBuilder.must(shouldFiltersBuilder);
    }

    if (!request.geographical_area().isEmpty()) {
      BoolFilterBuilder shouldFiltersBuilder = FilterBuilders.boolFilter();
      for (URI geographical_area : request.geographical_area()) {
        shouldFiltersBuilder.should(FilterBuilders.termFilter("geographical_areas", geographical_area.toString()));
      }
      mustFiltersBuilder.must(shouldFiltersBuilder);
    }

    if (!mustFiltersBuilder.hasClauses()) {
      return FilterBuilders.matchAllFilter();
    }
    return mustFiltersBuilder;
  }

  private Iterable<SimpleCatalogEntry> transformSearchResultToCatalogEntries(SearchResult searchResult, Optional<ULocale> displayLocale)
      throws Exception {
    JsonArray results = searchResult.getJsonObject()
        .getAsJsonObject("hits")
        .getAsJsonArray("hits");
    List<SimpleCatalogEntry> catalogEntries = Lists.newArrayList();
    for (JsonElement jsonElement : results) {
      JsonObject jsonObject = jsonElement.getAsJsonObject();
      JsonElement source = jsonObject.get("_source");
      SimpleCatalogEntry catalogEntry = OBJECT_MAPPER.readValue(source.toString(), SimpleCatalogEntry.class);
      catalogEntry.setId(jsonObject.get("_id").getAsString());
      catalogEntry.setType(CatalogEntry.EntryType.valueOf(jsonObject.get("_type").getAsString()));
      if (displayLocale.isPresent()) {
        catalogEntry.restrictLocale(displayLocale.get());
      }
      catalogEntries.add(catalogEntry);
    }
    return catalogEntries;
  }

  public ListenableFuture<Void> asyncIndex(CatalogEntry catalogEntry) {
    checkArgument(catalogEntry.isVisible());
    checkNotNull(catalogEntry.getId());

    SimpleCatalogEntry indexableCatalogEntry = new SimpleCatalogEntry(catalogEntry);
    try {
      Index indexAction = new Index.Builder(OBJECT_MAPPER.writeValueAsString(indexableCatalogEntry))
          .index(INDEX_NAME)
          .type(catalogEntry.getType().name())
          .id(indexableCatalogEntry.getId())
          .build();
      return executeAsync(indexAction);
    } catch (Exception e) {
      logger.error("Error while indexing CatalogEntry {}", catalogEntry.getId(), e);
      return Futures.immediateFailedFuture(e);
    }
  }

  public ListenableFuture<Void> asyncDelete(String id, CatalogEntry.EntryType entryType) {
    checkArgument(!Strings.isNullOrEmpty(id));

    try {
      Delete deleteAction = new Delete.Builder(id)
          .index(INDEX_NAME)
          .type(entryType.name())
          .build();
      return executeAsync(deleteAction);
    } catch (Exception e) {
      logger.error("Error while deleting CatalogEntry {} from index", id, e);
      return Futures.immediateFailedFuture(e);
    }
  }

  public ListenableFuture<Void> asyncDeleteServiceByInstance(String instanceId) {
    checkArgument(!Strings.isNullOrEmpty(instanceId));

    try {
      FilteredQueryBuilder queryBuilder = QueryBuilders.filteredQuery(
          QueryBuilders.matchAllQuery(),
          FilterBuilders.termFilter("instance_id", instanceId)
      );
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(queryBuilder);

      DeleteByQuery deleteByQueryAction = new DeleteByQuery.Builder(searchSourceBuilder.toString())
          .addIndex(INDEX_NAME)
          .addType(CatalogEntry.EntryType.SERVICE.name())
          .build();
      return executeAsync(deleteByQueryAction);
    } catch (Exception e) {
      logger.error("Error while deleting entries related to instance {} from index", instanceId, e);
      return Futures.immediateFailedFuture(e);
    }
  }

  private ListenableFuture<Void> executeAsync(final Action<JestResult> action) throws Exception {
    final SettableFuture<Void> settableFuture = SettableFuture.create();
    jestClient.executeAsync(action, new JestResultHandler<JestResult>() {
      @Override
      public void completed(JestResult result) {
        if (!result.isSucceeded()) {
          settableFuture.setException(new ElasticsearchException(result.getErrorMessage()));
          return;
        }

        settableFuture.set(null);
      }

      @Override
      public void failed(Exception ex) {
        settableFuture.setException(ex);
      }
    });
    return settableFuture;
  }

  @Override
  public void bootstrap() {
    try {
      Map<?, ?> map = OBJECT_MAPPER.readValue(Resources.getResource("elasticsearch/CatalogEntryIndexSettings.json"), Map.class);
      CreateIndex createIndex = new CreateIndex.Builder(INDEX_NAME).settings(map).build();
      JestResult jestResult = jestClient.execute(createIndex);
      if (!jestResult.isSucceeded()) {
        // The error message is probably indicating that the index is already created
        logger.info(jestResult.getErrorMessage());
      }
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private static List<ULocale> getFallbackLocales(ULocale locale) {
    List<ULocale> locales = Lists.newArrayList(locale);
    for (ULocale fallbackLocale = locale.getFallback(); fallbackLocale != null; fallbackLocale = fallbackLocale.getFallback()) {
      locales.add(fallbackLocale);
    }
    return locales;
  }
}
