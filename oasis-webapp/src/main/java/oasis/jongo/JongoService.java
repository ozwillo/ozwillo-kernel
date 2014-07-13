package oasis.jongo;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.jongo.Jongo;
import org.jongo.marshall.jackson.configuration.MapperModifier;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Preconditions;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;

import oasis.jongo.guice.JongoModule;
import oasis.model.i18n.LocalizableModule;

@Singleton
public class JongoService implements Provider<Jongo> {

  private Mongo mongoConnection;
  private Jongo jongoConnection;

  private final JongoModule.Settings settings;
  private final Provider<Set<JongoBootstrapper>> bootstrappers;

  @Inject
  JongoService(JongoModule.Settings settings, Provider<Set<JongoBootstrapper>> bootstrappers) {
    this.settings = settings;
    this.bootstrappers = bootstrappers;
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
        .registerModule(new GuavaModule())
        .registerModule(new LocalizableModule())
        .addModifier(new MapperModifier() {
          @Override
          public void modify(ObjectMapper mapper) {
            mapper.setSerializationInclusion(Include.NON_EMPTY); // instead of NON_NULL
          }
        })
        .build());

    for (JongoBootstrapper bootstrapper : bootstrappers.get()) {
      bootstrapper.bootstrap();
    }
  }

  public void stop() {
    mongoConnection.close();
  }

}
