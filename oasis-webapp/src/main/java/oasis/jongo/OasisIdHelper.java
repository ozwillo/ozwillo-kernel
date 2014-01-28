package oasis.jongo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import oasis.model.annotations.Id;

public class OasisIdHelper {
  private static final Map<Class<?>, Field> oasisFieldCache = new HashMap<>();

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
    if (oasisFieldCache.containsKey(clazz)) {
      return oasisFieldCache.get(clazz);
    }

    for (Field f : clazz.getDeclaredFields()) {
      if (f.isAnnotationPresent(Id.class)) {
        oasisFieldCache.put(clazz, f);
        return f;
      }
    }
    Field f = findOasisIdField(clazz.getSuperclass());
    oasisFieldCache.put(clazz, f);
    return f;
  }
}
