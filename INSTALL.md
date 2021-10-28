Ozwillo Kernel Installation
===========================

The Ozwillo Kernel is a standalone Java application (no application server needed)
and is distributed as a Debian package.

Prerequisites
-------------

The only software dependency needed on the server running the Ozwillo Kernel
is a Java Virtual Machine, version 1.7 at a minimum.

The Ozwillo Kernel however depends on companion services:
 * A reverse proxy as a TLS termination endpoint.
   It must forward the `Host` header as-is (no `X-Forwarded-Host`)
   and send an `X-Forwarded-Proto: https` header
   (it must also ensure `X-Forwarded-Proto` cannot be faked from the outside.)
   No need to say it must redirect HTTP traffic to HTTPS,
   with an appropriate `Strict-Transport-Security` header.
 * an SMTP server
 * a MongoDB database (tested with version 2.6)
 * an ElasticSearch server reachable through HTTP

In production, when installed through the Debian package,
it also depends on a Logstash agent
listening on TCP port 11111
and generally routing messages (JSON, one message per line) to an ElasticSearch server.

Building from sources
---------------------

The Debian package is not deployed to any public repository (yet),
so it must be built from sources.

Building the Ozwillo Kernel only requires
a Java Development Kit (same version as will be used in production),
Internet access (to download dependencies, including the build tool itself),
and Git (necessary to compute the version number).

Just run `./gradlew build` to download all dependencies and build the project;
the Debian package will be created in `oasis-dist/build/distributions/oasis_{version}.deb`,
along with an `oasis-dist/build/distributions/oasis_{version}.changes` file
for easy deployment to a Debian repository (such as reprepro).

Installing the package
----------------------

When installing the Debian package:
 * `oasis` user and group are created
 * the application is installed in `/usr/lib/oasis`
 * configuration is created in `/etc/oasis/` and `/etc/default/oasis`
 * a SystemV service is created at `/etc/init.d/oasis`
 * logs will be output in `/var/log/oasis`,
   and audit logs streamed to Logstash on localhost through TCP on port 11111

The configuration file is expected to be found at `/etc/oasis/oasis.conf`
but is not mandatory (see below), so the package doesn't create any.

Configuration
-------------

The Ozwillo Kernel can start with its default configuration:
 * SMTP server, MongoDB database, and ElasticSearch server on localhost on their default port;
   MongoDB server with an `oasis` database;
   a `catalog-entry` index will be created in ElasticSearch on startup
 * sender email address infered from the user (`oasis`) and machine names
 * listening on port 8080
 * using `/etc/oasis/public.key` and `/etc/oasis/private.key` to sign ID Tokens and JWTs,
   and generating them on startup if they do not exist
 * assuming no Ozwillo Portal (ideal during development)

In a production environment, a few things will need to be configured then.

The `/etc/oasis/oasis.conf` file is in [HOCON format](https://github.com/typesafehub/config/blob/master/HOCON.md).

Properties that should generally be configured on a production server are:
 * `oasis.mongo.uri`: a `mongodb://` URI to reach the database; defaults to `mongodb://localhost:27017/oasis`
 * `oasis.elasticsearch.url`: an HTTP URL to reach the ElasticSearch server; defaults to `http://localhost:9200`
 * `oasis.mail.server`: an SMTP URI to reach the SMTP server; defaults to `smtp://localhost`;
   can be setup as either `smtp://` or `smtps://`, specify the host, port, and credentials
   (see [`javax.mail.URLName`](https://docs.oracle.com/javaee/7/api/javax/mail/URLName.html))
 * `oasis.mail.from`: the sender's mail address, can be in the form `Ozwillo <ozwillo@example.com>`
   (see [`javax.mail.internet.InternetAddress`](https://docs.oracle.com/javaee/7/api/javax/mail/internet/InternetAddress.html))
   The default value is the current user at the current machine (as computed by [`InternetAddress.getLocalAddress(null)`](https://docs.oracle.com/javaee/7/api/javax/mail/internet/InternetAddress.html#getLocalAddress-javax.mail.Session-)).
 * `oasis.urls.landing-page`: the URL to redirect to when reaching the root path of the server;
   will generally be the public Ozwillo website, e.g. `https://www.ozwillo.com`.
 * `oasis.urls.portal-base-uri`: the URL to the Ozwillo Portal
 * `oasis.urls.path.my-oasis`: the path (relative to oasis.urls.portal-base-uri) to the Ozwillo Portal's _My Ozwillo_ page
 * `oasis.urls.path.my-profile`: the path (relative to oasis.urls.portal-base-uri) to the Ozwillo Portal's _My Profile_ page
 * `oasis.urls.path.popup-profile`: the path (relative to oasis.urls.portal-base-uri) to the Ozwillo Portal's page to edit the user's profile in a popup window
 * `oasis.urls.path.my-apps`: the path (relative to oasis.urls.portal-base-uri) to the Ozwillo Portal's _My Applications_ page
 * `oasis.urls.path.my-network`: the path (relative to oasis.urls.portal-base-uri) to the Ozwillo Portal's _My Network_ page
 * `oasis.urls.developer-doc`: the URL to the developer documention's website
 * `oasis.urls.privacy-policy`: the URL to Ozwillo's Privacy Policy page
 * `oasis.urls.terms-of-service`: the URL to Ozwillo's Terms Of Service page

Other properties that can be configured;
default values, if any, are defined in [`oasis-webapp/src/main/resources/reference.conf`](https://github.com/ozwillo/ozwillo-kernel/blob/master/oasis-webapp/src/main/resources/reference.conf):
 * `oasis.urls.canonical-base-uri`: when the Ozwillo Kernel is served under several host names,
   one must be defined as the _canonical_ one for user interactions through their browser.
   This is needed so that cookies (thus authentication) work as expected,
   and users will automatically be redirected to this host in case they reach the server with another name.
   It is an URL, in the form `https://accounts.ozwillo.com`.
 * `oasis.http.port`: the port to listen on; defaults to 8080
 * `oasis.http.client.logging-level`: an [OkHttp logging level](https://square.github.io/okhttp/3.x/logging-interceptor/okhttp3/logging/HttpLoggingInterceptor.Level.html),
   (case-insensitive) for debugging outgoing HTTP requests.
 * `oasis.auditlog.disabled`: to disable the audit logs entirely
 * `oasis.mail.starttls.enable`: to disable STARTTLS, as it can cause issues with some SMTP servers;
   enabled by default.
 * `oasis.auth.private-key-path` and `oasis.auth.public-key-path`: the paths to the private and public keys respectively (relative to the configuration file).
   The files must be an RSA key-pair with private key in PKCS#8 format, and public key in X509 format.
   If the files don't exist, they'll be created on first launch (RSA 2048 bits).
   If only the private key exists, the public key will be automatically extracted.
   It's an error if only the public key exists.
 * `oasis.auth.enable-client-certificates`: a boolean (defaults to `false`) to turn authentication through client certificates on.
   The reverse-proxy is expected to send the client certificate's Subject DN and Issuer DN as HTTP request headers `X-SSL-Client-Subject-DN` and `X-SSL-Client-Issuer-DN` respectively
   (it must also ensure those won't came from the outside.)
 * `oasis.auth.authorization-code-duration`: duration of the OAuth 2.0 authorization codes.
 * `oasis.auth.access-token-duration`: duration of the OAuth 2.0 access tokens.
 * `oasis.auth.refresh-token-duration`: duration of the OAuth 2.0 refresh tokens (only delivered when using the `offline_access` scope)
 * `oasis.auth.id-token-duration`: duration of the OpenID Connect 1.0 ID Tokens, used to authenticate sessions. 
 * `oasis.auth.sid-token-duration`: duration of the session ID tokens (duration of user sessions at the Kernel)
 * `oasis.auth.account-activation-token-duration`: duration of one-time tokens used for account activation.
 * `oasis.auth.change-password-token-duration`: duration of one-time tokens used for password reset.
 * `oasis.auth.jwt-bearer-duration`: duration of OAuth 2.0 JWT Bearer tokens used during app-instance provisioning.
 * `oasis.auth.password-minimum-length`: minimum length of user passwords.
 * `oasis.userdirectory.invitation-token-duration`: duration of one-time tokens used for invitations (to join an organization or to use an app-instance).

Logging configuration is in `/etc/oasis/log4j2.xml`.
By default (as installed by the Debian package), it writes logs to disk in `/var/log/oasis/`,
rotating the files every day;
and streams audit logs both to `localhost` on port 11111, and to disk in `/var/log/oasis/`.
Storing audit logs to disk allows _replaying_ them in case Logstash fails;
the audit logs are generated one event per line, in JSON.

A typical Logstash configuration would be to stream the events to an ElasticSearch server (here on `localhost`):
```
input {
  tcp {
    format => "json"
    port => 11111
    type => "oasis"
  }
}
output {
  elasticsearch {
    host => "localhost"
    protocol => "http"
    workers => "2"
  }
}
```

Bootstrapping
-------------

Before the Kernel can actually be used, its database needs to be bootstrapped.
This is done through a command-line tool:

```bash
java -cp "/usr/lib/oasis/lib/*" oasis.tools.Bootstrap -c /etc/oasis/oasis.conf <options>
```

Options are:
 * `-a` or `--admin`: **required**, the email address of the super administrator.
 * `-p` or `--password`: the administrator's password;
   one will be generated and printed to the standard output if not given.
 * `-r` or `--redirect-uri`: **required**, the Ozwillo Portal's `redirect_uri`.
 * `-plr` or `--post-logout-redirect-uri`: **required**, the Ozwillo Portal's `post_logout_redirect_uri`.

The tool will create:
 * the OpenID Connect 1.0 scopes,
 * a super administrator user,
 * an _“Ozwillo”_ organization, whose administrator will be the super administrator user,
 * the Ozwillo Portal application, instance, and service,
 * the Ozwillo DataCore application and instance (note: it does **not** create the _playground_ service.)

Periodic tasks
--------------

Two tools (see _command-line tools_ section below) need to be run every night to purge soft-deleted entities.
Those are `oasis.tools.PurgeDeletedOrganization` and `oasis.tools.PurgeDeletedAppInstance`.

The periodic tasks aren't automatically created on install and need to be scheduled manually.
This can be done with `crontab -u oasis -e` for example:
```
0 0 * * *	java -cp "/usr/lib/oasis/lib/*" oasis.tools.PurgeDeletedOrganization -c /etc/oasis/oasis.conf 2>/var/log/oasis/PurgeDeletedOrganization.stderr >/var/log/oasis/PurgeDeletedOrganization.stdout
0 0 * * *	java -cp "/usr/lib/oasis/lib/*" oasis.tools.PurgeDeletedAppInstance -c /etc/oasis/oasis.conf 2>/var/log/oasis/PurgeDeletedAppInstance.stderr >/var/log/oasis/PurgeDeletedAppInstance.stdout
```

The audit logs sent to Logstash (and streamed to disk!) can be quite heavyweight,
so it might be a good idea to also schedule tasks to delete old log files,
and to at least close (if not delete) indices in ElasticSearch (if Logstash streams to ElasticSearch)
to avoid filling the disks and guarantee good performances of ElasticSearch. 

Command-line tools
------------------

A number of command-line tools can be used for maintenance.
Each one is run by invoking Java with its class name
and passing the configuration file as a `-c` option
(see above with `oasis.tools.Bootstrap`.)
Most tools have a `-n` (or `--dry-run`) option that will not make any change,
and can be used to try a command before actually applying it.
All tools have an `--help` option that will (should!) display all possible options.

`oasis.tools.IndexApplication` adds or removes an application (whose ID is passed as argument) to/from the ElasticSearch catalog index.
This needs to be run whenever an application is added or removed, or its visibility status is changed.

`oasis.tools.InitializeCatalogIndex` rebuilds the ElasticSearch catalog index.
The index is expected to be empty when the tool is run (hence its name _“initialize”_.)

`oasis.tools.PurgeDeletedOrganization` deletes all organizations that have been in _deleted_ state for more than 7 days, and notify their admin members.

`oasis.tools.PurgeDeletedAppInstance` deletes all app-instances that have been in _stopped_ state for more than 7 days.
This will call the `destruction_uri` and notify admin users.
The logs need to be monitored from time to time for instances whose `destruction_uri` fails. 

`oasis.tools.DeleteAppInstance` can forcibly delete app-instances,
by their ID (`--instance`),
instantiator user ID (`--creator`; only when not instantiated for an organization),
organization (`--organization`; the organization they've been instanted for),
or application (`--application`).
This will **not** call the `destruction_uri` or notify admin users and should be used very sparingly.
This can be useful when the instance has already been deprovisioned by the provider (generally a test instance),
and normal deprovisioning through the Ozwillo Portal would fail;
or in any other case where the `destruction_uri` fails (but beware that it doesn't notify admin users).
