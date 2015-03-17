package oasis.jest.applications.v2;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import javax.inject.Inject;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
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
import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.client.JestResultHandler;
import io.searchbox.core.Delete;
import io.searchbox.core.DeleteByQuery;
import io.searchbox.core.Index;
import io.searchbox.indices.CreateIndex;
import oasis.jest.JestBootstrapper;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.SimpleCatalogEntry;
import oasis.model.i18n.LocalizableModule;

public class JestCatalogEntryRepository implements JestBootstrapper {
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
}
