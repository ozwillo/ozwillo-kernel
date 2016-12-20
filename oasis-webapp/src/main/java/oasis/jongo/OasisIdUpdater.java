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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.JacksonObjectIdUpdater;

public class OasisIdUpdater extends JacksonObjectIdUpdater {

  public OasisIdUpdater(ObjectMapper objectMapper) {
    super(objectMapper);
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
