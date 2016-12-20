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

import com.ibm.icu.util.ULocale;

import oasis.model.i18n.LocalizableString;

public class SimpleCatalogEntry extends CatalogEntry {
  private EntryType type;
  private boolean visible;

  public SimpleCatalogEntry() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public SimpleCatalogEntry(CatalogEntry other) {
    super(other);
    visible = other.isVisible();
  }

  @Override
  public EntryType getType() {
    return type;
  }

  public void setType(EntryType type) {
    this.type = type;
  }

  public void restrictLocale(ULocale locale) {
    setName(new LocalizableString(getName().get(locale)));
    setDescription(new LocalizableString(getDescription().get(locale)));
    setIcon(new LocalizableString(getIcon().get(locale)));
    setTos_uri(new LocalizableString(getTos_uri().get(locale)));
    setPolicy_uri(new LocalizableString(getPolicy_uri().get(locale)));
  }

  @Override
  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }
}
