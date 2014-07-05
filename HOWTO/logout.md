HOWTO Sign out a user?
======================

Signing out a user is a 3-step process.

1. Revoke all known tokens
--------------------------

To prevent those tokens from being used maliciously would they be leaked, the first step
is to use the _revocation endpoint_ as defined by [RFC 7009](https://tools.ietf.org/html/rfc7009)
to revoke all the tokens known to the application.

Authentication to the _revocation endpoint_ is done using the same mechanism and
credentials as at the _token endpoint_ to exchange authentication codes for tokens.

Note: if you have a _refresh token_ and all your _access tokens_ are derived from that
_refresh token_, then it's enough to revoke the _refresh token_ as that will
automatically revoke all the tokens derived from it.

2. Invalidate the application's local session
---------------------------------------------

Whichever mean the application uses to maintain the user session, it should be
invalidated so that when the user comes back to the application (and/or if he had the
application opened in several windows or tabs), he won't be recognized and will trigger
the authentication process with the OASIS Kernel.

In Java Servlets that could be `req.logout()` or `req.getSession().invalidate()`.

In PHP, that would be `session_destroy()` along with expiring the cookie with name
`session_name()`.

3. Single Sign-Out
------------------

If the user is still signed in on the OASIS Kernel, going back to the application will
(should) transparently sign-in him back. This would be a surprising behavior for the user
so he must be given the choice to sign out from the whole OASIS platform. This is done
using the _end session endpoint_ as defined by [RP-Initiated Logout in OpenID Connect
Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html#RPLogout).

Note: in addition to the `id_token_hint` and `post_logout_redirect_uri` parameters
defined in _draft 19_ of the _OpenID Connect Session Management 1.0_ specification, the
OASIS Kernel allows a `state` parameter that will be echo'd back to the
`post_logout_redirect_uri` after the user signed out, as with the similarly-named request
parameter at the _authorization endpoint_.
