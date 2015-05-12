/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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

import org.bson.types.ObjectId;
import org.jongo.ReflectiveObjectIdUpdater;

public class OasisIdUpdater extends ReflectiveObjectIdUpdater {

  public OasisIdUpdater(IdFieldSelector idFieldSelector) {
    super(idFieldSelector);
  }

  @Override
  public boolean mustGenerateObjectId(Object pojo) {
    return OasisIdHelper.findOasisIdField(pojo.getClass()) != null;
  }

  @Override
  public void setObjectId(Object target, ObjectId id) {
    super.setObjectId(target, id);
    OasisIdHelper.updateOasisIdField(target);
  }
}
