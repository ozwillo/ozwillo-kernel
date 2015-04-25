package oasis.web.authz;

import java.security.interfaces.RSAPublicKey;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import com.google.common.io.BaseEncoding;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.auth.AuthModule;

@Path("/a/keys")
@Api(value = "/a/keys", description = "Keys API")
public class KeysEndpoint {
  private static final BaseEncoding BASE64_ENCODING = BaseEncoding.base64Url().omitPadding();
  public static final String JSONWEBKEY_PK_ID = "oasis.openid-connect.public-key";

  @Inject AuthModule.Settings settings;

  @GET
  @Produces("application/jwk-set+json")
  @ApiOperation(
      value = "Retrieve the public key used for OpenID Connect.",
      notes = "Returns a JSON Web Key Set containing the public key. See the <a href='http://tools.ietf.org/html/draft-ietf-jose-json-web-key-18'>RFC</a> for more informations about JWKS."
  )
  public Response get() {
    RSAPublicKey publicKey = (RSAPublicKey) settings.keyPair.getPublic();

    JsonWebKeySet jsonWebKeySet = new JsonWebKeySet();
    RsaJsonWebKey rsaJsonWebKey = new RsaJsonWebKey();
    rsaJsonWebKey.modulus = BASE64_ENCODING.encode(publicKey.getModulus().toByteArray());
    rsaJsonWebKey.exponent = BASE64_ENCODING.encode(publicKey.getPublicExponent().toByteArray());
    rsaJsonWebKey.keyId = JSONWEBKEY_PK_ID;
    jsonWebKeySet.rsaJsonWebKeys = new RsaJsonWebKey[]{rsaJsonWebKey};

    return Response.ok().entity(jsonWebKeySet).build();
  }
}
