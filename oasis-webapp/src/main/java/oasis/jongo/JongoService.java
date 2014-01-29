package oasis.jongo;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.jongo.Jongo;
import org.jongo.marshall.Marshaller;
import org.jongo.marshall.jackson.JacksonIdFieldSelector;
import org.jongo.marshall.jackson.JacksonMapper;
import org.jongo.marshall.jackson.bson4jackson.BsonModule;
import org.jongo.marshall.jackson.bson4jackson.MongoBsonFactory;
import org.jongo.marshall.jackson.configuration.MapperModifier;
import org.jongo.marshall.jackson.configuration.PropertyModifier;
import org.jongo.query.BsonQueryFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Preconditions;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;

import oasis.jongo.guice.JongoModule;

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
    jongoConnection = new Jongo(mongoConnection.getDB(settings.mongoURI.getDatabase()), new OasisMapper.Builder()
        .registerModule(new JodaModule())
        .addModifier(new MapperModifier() {
          @Override
          public void modify(ObjectMapper mapper) {
            mapper.setSerializationInclusion(Include.NON_EMPTY); // instead of NON_NULL
          }
        })
        .build());
  }

  public void stop() {
    mongoConnection.close();
  }

}
