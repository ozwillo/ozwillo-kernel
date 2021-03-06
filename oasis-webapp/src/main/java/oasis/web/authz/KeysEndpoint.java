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
package oasis.web.authz;

import java.security.interfaces.RSAPublicKey;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;

import oasis.auth.AuthModule;

@Path("/a/keys")
public class KeysEndpoint {
  public static final String JSONWEBKEY_PK_ID = "oasis.openid-connect.public-key";

  @Inject AuthModule.Settings settings;

  @GET
  @Produces("application/jwk-set+json")
  public Response get() {
    RSAPublicKey publicKey = (RSAPublicKey) settings.keyPair.getPublic();

    JsonWebKeySet jsonWebKeySet = new JsonWebKeySet();
    RsaJsonWebKey rsaJsonWebKey = new RsaJsonWebKey(publicKey);
    rsaJsonWebKey.setKeyId(JSONWEBKEY_PK_ID);
    jsonWebKeySet.addJsonWebKey(rsaJsonWebKey);

    return Response.ok().entity(jsonWebKeySet.toJson()).build();
  }
}
