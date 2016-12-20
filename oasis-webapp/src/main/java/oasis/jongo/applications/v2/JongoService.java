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
package oasis.jongo.applications.v2;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.Service;

public class JongoService extends Service implements HasModified {

  @JsonProperty
  private Long created; // XXX: not exposed, only initialized once

  private long modified = System.currentTimeMillis();

  public JongoService() {
    super();
  }

  public JongoService(@Nonnull Service other) {
    super(other);
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }

  void initCreated() {
    created = System.currentTimeMillis();
  }
}
