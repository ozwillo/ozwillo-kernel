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
