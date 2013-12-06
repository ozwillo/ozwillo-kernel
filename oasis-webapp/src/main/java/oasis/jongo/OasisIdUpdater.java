package oasis.jongo;

import org.bson.types.ObjectId;
import org.jongo.ReflectiveObjectIdUpdater;

public class OasisIdUpdater extends ReflectiveObjectIdUpdater {

  public OasisIdUpdater(IdFieldSelector idFieldSelector) {
    super(idFieldSelector);
  }

  @Override
  public void setObjectId(Object target, ObjectId id) {
    super.setObjectId(target, id);
    OasisIdHelper.updateOasisIdField(target);
  }
}
