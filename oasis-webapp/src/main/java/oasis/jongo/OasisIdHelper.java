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

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import oasis.model.annotations.Id;

public class OasisIdHelper {
  private static final Map<Class<?>, Optional<Field>> oasisFieldCache = new ConcurrentHashMap<>();

  public static String generateId() {
    return UUID.randomUUID().toString();
  }

  public static void updateOasisIdField(Object target) {
    Field idField = OasisIdHelper.findOasisIdField(target.getClass());
    if (idField != null) {
      if (!idField.getType().equals(String.class)) {
        throw new IllegalArgumentException("Unable to set Oasis Id on class: " + target.getClass() + " (id field is not a String)");
      }

      idField.setAccessible(true);
      try {
        if (idField.get(target) == null) {
          idField.set(target, generateId());
        }
      } catch (IllegalAccessException e) {
        throw new IllegalArgumentException("Unable to set Oasis Id on class: " + target.getClass() + " (id field is not accessible)");
      }
    }
  }

  static Field findOasisIdField(Class<?> clazz) {
    if (Object.class == clazz) {
      return null;
    }
    // XXX: restrict to classes in an oasis.* package?

    Optional<Field> oasisField = oasisFieldCache.get(clazz);
    if (oasisField != null) {
      // Present in the cache
      return oasisField.orElse(null);
    }

    for (Field f : clazz.getDeclaredFields()) {
      if (f.isAnnotationPresent(Id.class)) {
        oasisFieldCache.put(clazz, Optional.of(f));
        return f;
      }
    }
    Field f = findOasisIdField(clazz.getSuperclass());
    oasisFieldCache.put(clazz, Optional.ofNullable(f));
    return f;
  }
}
