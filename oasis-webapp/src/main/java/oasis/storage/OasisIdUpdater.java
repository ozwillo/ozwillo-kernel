package oasis.storage;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.jongo.ReflectiveObjectIdUpdater;

import oasis.model.annotations.Id;

public class OasisIdUpdater extends ReflectiveObjectIdUpdater {

  private final Map<Class<?>, Field> oasisFieldCache = new HashMap<Class<?>, Field>();

  public OasisIdUpdater(IdFieldSelector idFieldSelector) {
    super(idFieldSelector);
  }

  @Override
  public void setObjectId(Object target, ObjectId id) {
    super.setObjectId(target, id);
    Field idField = findOasisIdField(target.getClass());
    if (idField == null) {
      return;
    }

    if (!isAnEmptyOasisId(target, idField) || !idField.getType().equals(String.class)) {
      throw new IllegalArgumentException("Unable to set Oasis Id on class: " + target.getClass());
    }
    String uuid = UUID.randomUUID().toString();
    updateOasisField(target, uuid, idField);
  }

  private boolean isAnEmptyOasisId(Object target, Field field) {
    try {
      field.setAccessible(true);
      return field.get(target) == null;
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Unable to obtain value from field" + field.getName() + ", class: " + target.getClass(), e);
    }
  }

  private void updateOasisField(Object target, String uuid, Field field) {
    try {
      if (field.getType().equals(String.class)) {
        field.set(target, uuid);
      }
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Unable to set Oasis Id on class: " + target.getClass());
    }
  }

  private Field findOasisIdField(Class<?> clazz) {
    if (oasisFieldCache.containsKey(clazz)) {
      return oasisFieldCache.get(clazz);
    }

    while (!Object.class.equals(clazz)) {
      for (Field f : clazz.getDeclaredFields()) {
        if (f.isAnnotationPresent(Id.class)) {
          oasisFieldCache.put(clazz, f);
          return f;
        }
      }
      clazz = clazz.getSuperclass();
    }
    return null;
  }
}
