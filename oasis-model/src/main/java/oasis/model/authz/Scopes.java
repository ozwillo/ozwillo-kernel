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
package oasis.model.authz;

public interface Scopes {
  // OpenID Connect 1.0
  public static final String OPENID = "openid";
  public static final String PROFILE = "profile";
  public static final String EMAIL = "email";
  public static final String ADDRESS = "address";
  public static final String PHONE = "phone";
  public static final String OFFLINE_ACCESS = "offline_access";

  // Portal
  public static final String PORTAL = "portal";
}
