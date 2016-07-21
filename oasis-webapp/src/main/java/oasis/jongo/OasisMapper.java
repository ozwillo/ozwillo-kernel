/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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

import org.jongo.Mapper;
import org.jongo.ObjectIdUpdater;
import org.jongo.marshall.Marshaller;
import org.jongo.marshall.Unmarshaller;
import org.jongo.marshall.jackson.JacksonEngine;
import org.jongo.marshall.jackson.configuration.AbstractMappingBuilder;
import org.jongo.query.BsonQueryFactory;
import org.jongo.query.QueryFactory;

public class OasisMapper implements Mapper {

  private final JacksonEngine jacksonEngine;
  private final QueryFactory queryFactory;
  private final ObjectIdUpdater objectIdUpdater;

  private OasisMapper(JacksonEngine jacksonEngine, QueryFactory queryFactory, ObjectIdUpdater objectIdUpdater) {
    this.jacksonEngine = jacksonEngine;
    this.queryFactory = queryFactory;
    this.objectIdUpdater = objectIdUpdater;
  }

  @Override
  public Marshaller getMarshaller() {
    return jacksonEngine;
  }

  @Override
  public Unmarshaller getUnmarshaller() {
    return jacksonEngine;
  }

  @Override
  public ObjectIdUpdater getObjectIdUpdater() {
    return objectIdUpdater;
  }

  @Override
  public QueryFactory getQueryFactory() {
    return queryFactory;
  }

  public static class Builder extends AbstractMappingBuilder<Builder> {

    public Mapper build() {
      JacksonEngine jacksonEngine = new JacksonEngine(createMapping());
      QueryFactory queryFactory = new BsonQueryFactory(new OasisMarshaller(jacksonEngine));
      ObjectIdUpdater objectIdUpdater = new OasisIdUpdater(jacksonEngine.getObjectMapper());
      return new OasisMapper(jacksonEngine, queryFactory, objectIdUpdater);
    }

    @Override
    protected Builder getBuilderInstance() {
      return this;
    }
  }
}
