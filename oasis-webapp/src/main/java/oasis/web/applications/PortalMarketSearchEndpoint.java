/**
 * Ozwillo Kernel
 * Copyright (C) 2018  The Ozwillo Kernel Authors
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
package oasis.web.applications;

import java.util.Iterator;

import javax.ws.rs.Path;

import oasis.model.applications.v2.ImmutableCatalogEntryRepository;
import oasis.model.applications.v2.SimpleCatalogEntry;
import oasis.model.authn.AccessToken;
import oasis.web.authn.Portal;

@Path("/p/m/search")
@Portal
public class PortalMarketSearchEndpoint extends AbstractMarketSearchEndpoint {
  @Override
  protected Iterator<SimpleCatalogEntry> doSearch(
      AccessToken accessToken, ImmutableCatalogEntryRepository.SearchRequest.Builder requestBuilder) {
    return catalogEntryRepository.search(
        requestBuilder.portal(null).build()
    ).iterator();
  }
}
