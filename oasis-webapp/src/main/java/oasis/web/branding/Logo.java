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
package oasis.web.branding;

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.web.StaticResources;
import oasis.web.utils.ResponseFactory;

@Path("/")
public class Logo {

  @Inject BrandRepository brandRepository;

  @GET
  @Path("/images/logo/small_{brandId}.png")
  @Produces("image/png")
  public Response small(@PathParam("brandId") String brandId) throws IOException {
    if (BrandInfo.DEFAULT_BRAND.equals(brandId)) {
      return StaticResources.getResource("oasis-ui/images/logo/small_ozwillo.png");
    }

    byte[] logo = brandRepository.getSmallLogo(brandId);
    if (logo == null) {
      return ResponseFactory.NOT_FOUND;
    }

    return Response.ok().entity(logo).build();
  }

  @GET
  @Path("/images/logo/large_{brandId}.png")
  @Produces("image/png")
  public Response large(@PathParam("brandId") String brandId) throws IOException {
    if (BrandInfo.DEFAULT_BRAND.equals(brandId)) {
      return StaticResources.getResource("oasis-ui/images/logo/large_ozwillo.png");
    }

    String logo = brandRepository.getLargeLogo(brandId);
    if (logo == null) {
      return ResponseFactory.NOT_FOUND;
    }

    return StaticResources.getResource(logo);
  }
}
