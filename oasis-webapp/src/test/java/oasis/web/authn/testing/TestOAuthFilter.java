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
package oasis.web.authn.testing;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;

import net.ltgt.resteasy.testing.TestSecurityFilter;
import oasis.model.authn.AccessToken;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;

@OAuth
@Priority(Priorities.AUTHENTICATION - 1)
public class TestOAuthFilter extends TestSecurityFilter {

  public TestOAuthFilter(AccessToken accessToken) {
    super(new OAuthPrincipal(accessToken), true, "OAUTH_BEARER");
  }
}
