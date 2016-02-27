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
package oasis.web;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.io.Resources;

import oasis.urls.Urls;
import oasis.web.utils.ResponseFactory;

@Path("/")
public class StaticResources {

  @Inject Urls urls;

  @GET
  @Path("")
  @Produces(MediaType.TEXT_HTML)
  public Response home() {
    if (urls.landingPage().isPresent()) {
      return Response.seeOther(urls.landingPage().get()).build();
    }
    return ResponseFactory.NOT_FOUND;
  }

  @GET
  @Path("/favicon.ico")
  @Produces("image/vnd.microsoft.icon")
  public Response favicon() throws IOException {
    return getResource("favicon.ico");
  }

  @GET
  @Path("/manifest.json")
  @Produces(MediaType.APPLICATION_JSON)
  public Response manifest() throws IOException {
    return getResource("manifest.json");
  }

  @GET
  @Path("/browserconfig.xml")
  @Produces(MediaType.APPLICATION_XML)
  public Response browserconfig() throws IOException {
    return getResource("browserconfig.xml");
  }

  @GET
  @Path("{resource: .+\\.css}")
  @Produces("text/css")
  public Response css(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.jpg}")
  @Produces("image/jpg")
  public Response jpg(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.png}")
  @Produces("image/png")
  public Response png(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.svg}")
  @Produces("image/svg+xml")
  public Response svg(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.woff}")
  @Produces("application/font-woff")
  public Response woff(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.ttf}")
  @Produces("application/x-font-ttf")
  public Response ttf(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.js}")
  @Produces("application/javascript")
  public Response js(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  private Response getResource(String resourceName) throws IOException {
    final URL resource;
    try {
      resource = Resources.getResource("oasis-ui/" + resourceName);
    } catch (IllegalArgumentException iae) {
      return ResponseFactory.NOT_FOUND;
    }

    URLConnection conn = resource.openConnection();
    Response.ResponseBuilder response = Response.ok()
        .entity(conn.getInputStream());

    long lastModified = conn.getLastModified();
    if (lastModified != 0) {
      response.lastModified(new Date(lastModified));
    }

    return response.build();
  }
}
