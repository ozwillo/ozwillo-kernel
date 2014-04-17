OASIS Kernel
============

The OASIS Kernel provides

* OASIS Core services:
    - Authentication and authorizations
    - Application catalog
    - User management, profile and graph

* OASIS Support services:
    - User notifications
    - Publish/Subscribe Event bus
    - Application log collector

OASIS Core services
-------------------

The OASIS Core services are the glue between all the applications in the OASIS ecosystem.
The main service, that every single application will use, is the single sign-on.
But single sign-on requires applications to be registered first in a catalog.
And single sign-on would be only half-baked if it didn't provide information about the user.
OASIS goes further than just user profiles though, and provides a full graph
with relations between users, organizations or groups of users.
Finally, the promise of OASIS is to give users a total control on their data,
so authorizations are baked directly into the OASIS Core services.

### Authentication and authorizations

OASIS Authentication and authorizations service is an implementation of international standards:

* [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html) (using the Authorization Code flow only)
* [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig) (provider configuration only)
* [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html) (RP-Initiated Logout only for now)
* [OAuth Token Introspection](https://tools.ietf.org/html/draft-richer-oauth-introspection)
* [OAuth Token Revocation (RFC 7009)](https://tools.ietf.org/html/rfc7009)

Other standards not implemented by the OASIS Kernel but under consideration (i.e. might be implemented in the future):

* [OpenID Connect Dynamic Client Registration 1.0](https://openid.net/specs/openid-connect-registration-1_0.html)
* [OAuth 2.0 Resource Set Registration](https://tools.ietf.org/html/draft-hardjono-oauth-resource-reg)

### Application catalog

The application catalog is two-fold: it holds a catalog of applications proposed by providers,
and the instances of those applications as picked or bought by users or organizations.

Each application (or instance of an application) can be composed of a so-called Service Provider
and a number of so-called Data Providers, each being optional.
The Service Provider is the user-visible (GUI) part of the application,
whereas the Data Providers make the data available for other applications to use.

Signing in to a Service Provider uses OpenID Connect 1.0,
while authorizations to access data at a Data Provider uses OAuth 2.0.

### User management, profile and graph

TBD

Oasis Support services
----------------------

### User notification

Applications can send notifications to users in the form of a short text and a URL.
Those notifications will be displayed to the user on the OASIS Portal.

### Publish/Subscribe Event bus

The event bus is used to send inter-application events and create loosely coupled workflows,
where each step of the flow is an application that doesn't know exactly which other applications are part of the flow.

### Application log collector

The OASIS Kernel collects application logs to help establishing KPIs for the OASIS Platform and ecosystem.

