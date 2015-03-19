package oasis.jest.applications.v2;

import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.io.Resources;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.indices.CreateIndex;
import oasis.jest.JestBootstrapper;

public class JestCatalogEntryRepository implements JestBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(JestCatalogEntryRepository.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String INDEX_NAME = "catalog-entry";

  private final JestClient jestClient;

  @Inject JestCatalogEntryRepository(JestClient jestClient) {
    this.jestClient = jestClient;
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
