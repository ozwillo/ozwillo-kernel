package oasis.jongo;

import org.jongo.bson.BsonDocument;
import org.jongo.marshall.Marshaller;
import org.jongo.marshall.MarshallingException;
import org.jongo.marshall.jackson.JacksonEngine;

public class OasisMarshaller implements Marshaller {
  private final Marshaller delegate;

  public OasisMarshaller(JacksonEngine delegate) {
    this.delegate = delegate;
  }

  @Override
  public BsonDocument marshall(Object pojo) throws MarshallingException {
    OasisIdHelper.updateOasisIdField(pojo);
    return delegate.marshall(pojo);
  }
}
