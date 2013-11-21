package oasis.storage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.jongo.Jongo;
import org.jongo.marshall.jackson.JacksonIdFieldSelector;
import org.jongo.marshall.jackson.JacksonMapper;

import com.google.common.base.Preconditions;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;

@Singleton
public class JongoService implements Provider<Jongo> {

  private Mongo mongoConnection;
  private Jongo jongoConnection;

  private final JongoModule.Settings settings;

  @Inject
  JongoService(JongoModule.Settings settings) {
    this.settings = settings;
  }

  @Override
  public Jongo get() {
    Preconditions.checkState(jongoConnection != null, "Thou shalt start tha JongoService");
    return jongoConnection;
  }

  public void start() throws Exception {
    mongoConnection = new MongoClient(settings.mongoURI);
    jongoConnection = new Jongo(mongoConnection.getDB(settings.mongoURI.getDatabase()), new JacksonMapper.Builder()
        .withObjectIdUpdater(new OasisIdUpdater(new JacksonIdFieldSelector()))
        .build());
  }

  public void stop() {
    mongoConnection.close();
  }

}
