package oasis.jongo;

import org.jongo.Mapper;
import org.jongo.ObjectIdUpdater;
import org.jongo.ReflectiveObjectIdUpdater;
import org.jongo.marshall.Marshaller;
import org.jongo.marshall.Unmarshaller;
import org.jongo.marshall.jackson.JacksonEngine;
import org.jongo.marshall.jackson.JacksonIdFieldSelector;
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
      ObjectIdUpdater objectIdUpdater = new OasisIdUpdater(new JacksonIdFieldSelector());
      return new OasisMapper(jacksonEngine, queryFactory, objectIdUpdater);
    }

    @Override
    protected Builder getBuilderInstance() {
      return this;
    }
  }
}
