HOWTO Sign in a user?
=====================

Signing in a user uses [OpenID Connect 1.0](https://openid.net/specs/openid-connect-core-1_0.html).

Prerequisites
-------------

Your application needs to be registered into the catalog.
It has to be _instantiated_ so that you've been provided a `client_id` and `client_secret`.
It must have declared at least one service, with registered `redirect_uris`.

Your application needs to be able to maintain a session to identify a signed-in user.

1. Authentication and authorization request
--------------------------------------------

Your application will request authentication of the user
and (optionally) authorizations to access data at other application instances (or in the Data Core or the Kernel)
as a single step.

1. Create two secure randoms
   (e.g. using `java.security.SecureRandom` in Java, or `openssl_random_pseudo_bytes` in PHP)
   that we'll call _state random_ and _nonce_,
   and store them in a session in your application linked to the user's browser.
2. Redirect to the _authorization endpoint_ with your `client_id` and service's `redirect_uri`.
   The `scope` must contain at least `openid`,
   the `state` contains your _state random_ (but can contain application-specific state),
   and the `nonce` is your _nonce_ random.
   The syntax of the `state` and `nonce` are up to you, the Kernel will only echo them as-is.


2. Authentication and authorization response
--------------------------------------------

After authentication of the user and approval of the scopes by him,
the Kernel generates a temporary _authorization code_ that it sends back to your application at its `redirect_uri`.
The `state` is echoed back to the `redirect_uri`.

The first thing to do is to very that a session exists for the user's browser,
and that its _state random_ matches the one from the `state` parameter echoed back from the Kernel.
This _state random_ is used to mitigate cross-site request forgery (CSRF) attacks.

3. Exchange the code for a token
--------------------------------

Your application needs to make an HTTP request (server-to-server) to the _token endpoint_
to exchange the received _authorization code_ for an _access token_ (and optionally a _refresh token_).

Your application authenticates to the Kernel using HTTP Basic with its `client_id\ and `client_secret`.
The request must contain the exact same `redirect_uri` as in the authentication request.

In exchange, the Kernel responds with an `access_token`,
the delay (in seconds) the token `expires_in`,
the `scope` granted by the token (which might be different from those you initially asked),
and an `id_token` in the form of a signed Json Web Token.

The `id_token`'s signature should be validated using the public key published at the _keys endpoint_.
Its payload contains the `nonce` you initially sent in the authentication request and should match the one from the session.
It also contains the user's identifier (`sub`) and, as an Ozwillo-specific extension to the specification,
optional `app_user` and  `app_admin` boolean properties.
When present and equal to `true`, `app_user` indicates that the user has been assigned the application (generaly by his IT manager).
When present and equal to `true`, `app_admin` indicates that the user is the one who bought the application,
or is an administrator for the organization who bought the application.
Note that a user can have `app_admin` set to `true` without `app_user` being `true`.

Once you have obtained an `access_token`, the user is successfully authenticated.
You should forget about the _state random_ and _nonce_,
and you must store the `id_token` as-is (in textual, encoded form) in the session for use to later sign the user out.
You can redirect the user to your application, which will treat him as an authenticated user thanks to some information you'd have put into the session.

4. Retrieve information about the user
--------------------------------------

Depending on the scopes you requested, you can make an HTTP request (server-to-server) to the _user info endpoint_ to retrieve information about the user.

Your application authenticates to the Kernel using OAuth 2.0 Bearer authentication, providing the `access_token` you previous received in exchange for the `code`.
The Kernel responds with a JSON document, but you can opt in for a JWT using content negotiation with an `Accept` request header.

Among the information you can retrieve from the Kernel about the user are his first and last name, and his preferred locale and timezone.
Those are important to personalize the user experience to his choices, globally stored into the Kernel and shared by all applications in the Ozwillo ecosystem.

Error cases
-----------

The Kernel can redirect to your `redirect_uri` with an `error` instead of a `code`.
That can happen if the user refused to authenticate or to authorize your application,
or if you provided wrong values (`client_id`, `scope` or `redirect_uri`).

Mismatching _state random_ or _nonce_, or an invalid JWT signature, must also be treated as errors.
As a result, the user must not be signed in to your application.

In any case, you should display an error page to the user;
or possibly (in some cases) get the user back to anonymous (if supported by your application).
