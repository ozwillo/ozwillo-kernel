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
package oasis.model.applications.v2;

import oasis.model.i18n.LocalizableString;

public abstract class CommonProperties {
  private LocalizableString name;
  private LocalizableString description;
  private LocalizableString icon;
  private String provider_id;

  protected CommonProperties() {
    name = new LocalizableString();
    description = new LocalizableString();
    icon = new LocalizableString();
  }

  /**
   * Copy constructor.
   */
  protected CommonProperties(CommonProperties other) {
    name = new LocalizableString(other.getName());
    description = new LocalizableString(other.getDescription());
    icon = new LocalizableString(other.getIcon());
    provider_id = other.getProvider_id();
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

  public LocalizableString getIcon() {
    return icon;
  }

  public void setIcon(LocalizableString icon) {
    this.icon = icon;
  }

  public String getProvider_id() {
    return provider_id;
  }

  public void setProvider_id(String provider_id) {
    this.provider_id = provider_id;
  }
}
