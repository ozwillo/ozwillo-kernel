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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;

import com.ibm.icu.util.ULocale;

public class LocaleParamConverterProvider implements ParamConverterProvider {
  @Override
  @SuppressWarnings("unchecked")
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (ULocale.class.equals(rawType)) {
      return (ParamConverter<T>) new LocaleParamConverter();
    }
    return null;
  }

  private static class LocaleParamConverter implements ParamConverter<ULocale> {
    @Override
    public ULocale fromString(String value) {
      return ULocale.forLanguageTag(value);
    }

    @Override
    public String toString(ULocale value) {
      return value.toLanguageTag();
    }
  }
}
