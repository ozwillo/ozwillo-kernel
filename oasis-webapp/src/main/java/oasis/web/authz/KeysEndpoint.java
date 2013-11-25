package oasis.web.authz;

import java.security.interfaces.RSAPublicKey;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.BaseEncoding;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.openidconnect.OpenIdConnectModule;

@Path("/a/keys")
@Api(value = "/a/keys", description = "Keys API")
public class KeysEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(KeysEndpoint.class);
  private static final BaseEncoding BASE64_ENCODING = BaseEncoding.base64Url().omitPadding();

  @Inject OpenIdConnectModule.Settings settings;

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
    jsonWebKeySet.rsaJsonWebKeys = new RsaJsonWebKey[]{rsaJsonWebKey};

    return Response.ok().entity(jsonWebKeySet).build();
  }

  @ApiModel
  private static class JsonWebKeySet {
    @JsonProperty("keys")
    @ApiModelProperty(required = true)
    private RsaJsonWebKey[] rsaJsonWebKeys;
  }

  @ApiModel
  private static class RsaJsonWebKey {
    @JsonProperty("kty")
    @ApiModelProperty(required = true)
    private String keyType = "RSA";

    @JsonProperty("use")
    @ApiModelProperty(required = true)
    private String keyUse = "sig";

    @JsonProperty("alg")
    @ApiModelProperty()
    private String algorithm = "RS256";

    @JsonProperty("kid")
    @ApiModelProperty()
    private String keyId;

    @JsonProperty("n")
    @ApiModelProperty(required = true, value = "Base 64 encoded string")
    private String modulus;

    @JsonProperty("e")
    @ApiModelProperty(required = true, value = "Base 64 encoded string")
    private String exponent;
  }
}
