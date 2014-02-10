package oasis.jongo;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Optional;

import oasis.model.annotations.Id;

public class OasisIdHelper {
  private static final Map<Class<?>, Optional<Field>> oasisFieldCache = new ConcurrentHashMap<>();

  public static void updateOasisIdField(Object target) {
    Field idField = OasisIdHelper.findOasisIdField(target.getClass());
    if (idField != null) {
      if (!idField.getType().equals(String.class)) {
        throw new IllegalArgumentException("Unable to set Oasis Id on class: " + target.getClass() + " (id field is not a String)");
      }

      idField.setAccessible(true);
      try {
        if (idField.get(target) == null) {
          idField.set(target, UUID.randomUUID().toString());
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
      return oasisField.orNull();
    }

    for (Field f : clazz.getDeclaredFields()) {
      if (f.isAnnotationPresent(Id.class)) {
        oasisFieldCache.put(clazz, Optional.of(f));
        return f;
      }
    }
    Field f = findOasisIdField(clazz.getSuperclass());
    oasisFieldCache.put(clazz, Optional.fromNullable(f));
    return f;
  }
}
