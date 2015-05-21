Ozwillo Kernel
============

The Ozwillo Kernel provides

* Ozwillo Core services:
    - Authentication and authorizations
    - Application catalog
    - User management, profile and graph

* Ozwillo Support services:
    - User notifications
    - Publish/Subscribe Event bus
    - Application log collector

Ozwillo Core services
-------------------

The Ozwillo Core services are the glue between all the applications in the OASIS ecosystem.
The main service, that every single application will use, is the single sign-on.
But single sign-on requires applications to be registered first in a catalog.
And single sign-on would be only half-baked if it didn't provide information about the user.
Ozwillo goes further than just user profiles though, and provides a full graph
with relations between users, organizations or groups of users.
Finally, the promise of Ozwillo is to give users a total control on their data,
so authorizations are baked directly into the Ozwillo Core services.

### Authentication and authorizations

Ozwillo Authentication and authorizations service is an implementation of international standards:

* [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html) (using the Authorization Code flow only)
* [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig) (provider configuration only)
* [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html) (RP-Initiated Logout only for now)
* [OAuth Token Introspection](https://tools.ietf.org/html/draft-richer-oauth-introspection)
* [OAuth Token Revocation (RFC 7009)](https://tools.ietf.org/html/rfc7009)

Other standards not implemented by the Ozwillo Kernel but under consideration (i.e. might be implemented in the future):

* [OpenID Connect Dynamic Client Registration 1.0](https://openid.net/specs/openid-connect-registration-1_0.html)
* [OAuth 2.0 Resource Set Registration](https://tools.ietf.org/html/draft-hardjono-oauth-resource-reg)
* [Providing User Authentication Information to OAuth 2.0 Clients](http://tools.ietf.org/html/draft-hunt-oauth-v2-user-a4c)

### Application catalog

The application catalog is three-fold: it holds a catalog of applications proposed by providers,
the instances of those applications as picked or bought by users or organizations,
and the services provided by those application instances.

An Application Instance is a piece of software running in the context of an individual or organization who picked our bought it.
It can make its data available fo other application instances to use.

A Service is a user-visible (GUI) part of an application instance,
there can be several services per application instance,
and each one can be public or private,
and/or be assigned to specific users.

It doesn't matter how application instances are deployed, i.e. whether they're tenants in a
single running piece of software or is specifically installed/deployed for a particular
user or organization.

Signing in to a Service uses OpenID Connect 1.0,
while authorizations to access data at an Application Instance uses OAuth 2.0. 

### User management, profile and graph

TBD

Oasis Support services
----------------------

### User notification

Applications can send notifications to users in the form of a short text and a URL.
Those notifications will be displayed to the user on the Ozwillo Portal.

### Publish/Subscribe Event bus

The event bus is used to send inter-application events and create loosely coupled workflows,
where each step of the flow is an application that doesn't know exactly which other applications are part of the flow.

### Application log collector

The Ozwillo Kernel collects application logs to help establishing KPIs for the OASIS Platform and ecosystem.

