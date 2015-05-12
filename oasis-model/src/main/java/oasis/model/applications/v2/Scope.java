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
package oasis.model.applications.v2;

import javax.annotation.Nullable;

import oasis.model.i18n.LocalizableString;

public class Scope {
  private String id;
  @Nullable private String instance_id;
  private String local_id;
  private LocalizableString name = new LocalizableString();
  private LocalizableString description = new LocalizableString();

  public String getId() {
    if (id == null) {
      computeId();
    }
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getInstance_id() {
    return instance_id;
  }

  public void setInstance_id(String instance_id) {
    this.instance_id = instance_id;
  }

  public String getLocal_id() {
    return local_id;
  }

  public void setLocal_id(String local_id) {
    this.local_id = local_id;
  }

  public LocalizableString getName() {
    return name;
  }

  public void setName(LocalizableString name) {
    this.name = name;
  }

  public LocalizableString getDescription() {
    return description;
  }

  public void setDescription(LocalizableString description) {
    this.description = description;
  }

  public void computeId() {
    id = computeId(instance_id, local_id);
  }

  public static String computeId(@Nullable String instance_id, String local_id) {
    return instance_id == null ? local_id : instance_id + ":" + local_id;
  }
}
