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
package oasis.jongo;

import java.io.IOException;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.joda.time.Instant;
import org.jongo.Jongo;
import org.jongo.marshall.jackson.configuration.MapperModifier;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Preconditions;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;

import de.undercouch.bson4jackson.BsonGenerator;
import de.undercouch.bson4jackson.serializers.BsonSerializer;
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

  @SuppressWarnings("deprecation")
  public void start() throws Exception {
    mongoConnection = new MongoClient(settings.mongoURI);
    jongoConnection = new Jongo(mongoConnection.getDB(settings.mongoURI.getDatabase()), new OasisMapper.Builder()
        .registerModule(new CustomJodaModule())
        .registerModule(new GuavaModule())
        .registerModule(new LocalizableModule())
        .addModifier(new MapperModifier() {
          @Override
          public void modify(ObjectMapper mapper) {
            mapper.setSerializationInclusion(Include.NON_EMPTY); // instead of NON_NULL
          }
        })
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .build());

    for (JongoBootstrapper bootstrapper : bootstrappers.get()) {
      bootstrapper.bootstrap();
    }
  }

  public void stop() {
    mongoConnection.close();
  }

  static class CustomJodaModule extends JodaModule {
    CustomJodaModule() {
      super();
      addSerializer(Instant.class, new BsonSerializer<Instant>() {
        @Override
        public void serialize(Instant instant, BsonGenerator bsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
          bsonGenerator.writeDateTime(instant.toDate());
        }
      });
      addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
        @Override
        public Instant deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
          return new Instant(jp.getEmbeddedObject());
        }
      });
    }
  }
}
