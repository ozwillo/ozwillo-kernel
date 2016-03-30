/**
 * Ozwillo Kernel
 * Copyright (C) 2016  Atol Conseils & Développements
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
package oasis.web.authn;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import oasis.urls.Urls;
import oasis.web.StaticResources;

@Path("/a/check_session_iframe")
public class CheckSessionIframePage {

  @Inject Urls urls;

  @Context UriInfo uriInfo;

  @GET
  public Response get() throws IOException {
    @Nullable Response redirectResponse = UserCanonicalBaseUriFilter.maybeRedirectToCanonicalUri(urls, uriInfo);
    if (redirectResponse != null) {
      return redirectResponse;
    }

    return StaticResources.getResource("oasis-ui/check_session_iframe.html");
  }
}
