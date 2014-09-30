package oasis.web.providers;

import static com.fasterxml.jackson.databind.DeserializationFeature.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;

import oasis.model.i18n.LocalizableModule;
import oasis.web.utils.ResponseFactory;

/**
 * Handles JSON parsing and mapping exceptions when reading.
 *
 * <p>jackson-jaxrs-base comes with exception mappers for those, but we don't want exceptions
 * thrown afterwards to possibly leak sensible information. Only exception thrown when parsing
 * the request body should result in 4xx errors with an informative message, other Jackson
 * exceptions should result in a 5xx opaque message (with details logged for later analysis).
 */
public class JacksonJsonProvider extends com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider {

  public JacksonJsonProvider() {
    super(new ObjectMapper()
        .registerModule(new JodaModule())
        .registerModule(new GuavaModule())
        .registerModule(new LocalizableModule())
        .disable(FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(FAIL_ON_NUMBERS_FOR_ENUMS)
        .setSerializationInclusion(JsonInclude.Include.NON_EMPTY));
  }

  @Override
  public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException {
    try {
      return super.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);
    } catch (JsonParseException jpe) {
      throw new BadRequestException(ResponseFactory.build(Response.Status.BAD_REQUEST, jpe.getMessage()), jpe);
    } catch (JsonMappingException jme) {
      throw new WebApplicationException(jme, ResponseFactory.unprocessableEntity(jme.getMessage()));
    }
  }
}
