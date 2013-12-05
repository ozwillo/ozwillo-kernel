package oasis.jongo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.bson.types.ObjectId;
import org.jongo.ReflectiveObjectIdUpdater;

public class OasisIdUpdater extends ReflectiveObjectIdUpdater {

  private final Map<Class<?>, Field> oasisFieldCache = new HashMap<Class<?>, Field>();

  public OasisIdUpdater(IdFieldSelector idFieldSelector) {
    super(idFieldSelector);
  }

  @Override
  public void setObjectId(Object target, ObjectId id) {
    super.setObjectId(target, id);
    OasisIdHelper.updateOasisIdField(target);
  }
}
