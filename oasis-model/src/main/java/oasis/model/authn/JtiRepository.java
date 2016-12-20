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
package oasis.model.authn;

import org.joda.time.Instant;

public interface JtiRepository {
  /**
   * Marks the JTI as <i>used</i>, and remember that state until {@code expirationTime}.
   *
   * @return {@code true} if the JTI has been correctly marked,
   *         {@code false} if it was already marked as used.
   */
  boolean markAsUsed(String jti, Instant expirationTime);
}
