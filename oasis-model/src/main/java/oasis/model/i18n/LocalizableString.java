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
package oasis.model.i18n;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = LocalizableStringSerializer.class)
@JsonDeserialize(using = LocalizableStringDeserializer.class)
public class LocalizableString extends LocalizableValue<String> {

  public LocalizableString(String rootValue) {
    super(rootValue);
  }

  public LocalizableString() {
    super();
  }

  public LocalizableString(LocalizableString src) {
    super(src);
  }

  private LocalizableString(LocalizableString src, boolean modifiable) {
    super(src, modifiable);
  }

  @Override
  public LocalizableString unmodifiable() {
    return modifiable ? new LocalizableString(this, true) : this;
  }
}
