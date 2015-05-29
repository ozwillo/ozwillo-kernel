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
package oasis.usecases;

import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import com.ibm.icu.util.ULocale;

import oasis.auth.RedirectUri;
import oasis.model.applications.v2.Service;

public class ServiceValidator {
  @Nullable public String validateService(Service service, String instance_id) {
    if (Strings.isNullOrEmpty(service.getLocal_id())) {
      return "Service missing local_id";
    }
    if (service.getInstance_id() != null && !service.getInstance_id().equals(instance_id)) {
      return "Bad service instance_id";
    }
    // TODO: validate all provided names (here, we enforce presence of a ROOT value)
    if (Strings.isNullOrEmpty(service.getName().get(ULocale.ROOT))) {
      return "Service missing name";
    }
    // XXX: description?
    // TODO: validate all provided URIs (here, we enforce presence of a ROOT value)
    if (!isValidHttpUri(service.getTos_uri().get(ULocale.ROOT))) {
      return "Service missing tos_uri";
    }
    // TODO: validate all provided URIs (here, we enforce presence of a ROOT value)
    if (!isValidHttpUri(service.getPolicy_uri().get(ULocale.ROOT))) {
      return "Service missing policy_uri";
    }
    // TODO: validate all provided URIs (here, we enforce presence of a ROOT value)
    if (!isValidHttpUri(service.getIcon().get(ULocale.ROOT))) {
      return "Service missing icon";
    }
    for (String screenshotUri : service.getScreenshot_uris()) {
      if (!isValidHttpUri(screenshotUri)) {
        return "Invalid screenshot_uri: " + screenshotUri;
      }
    }
    if (service.getContacts().isEmpty()) {
      return "Service missing contacts";
    }
    for (String contact : service.getContacts()) {
      if (!isValidContactUri(contact)) {
        return "Invalid contact: " + contact;
      }
    }
    if (service.getPayment_option() == null) {
      return "Service missing payment_option";
    }
    if (service.getTarget_audience().isEmpty()) {
      return "Service missing target_audience";
    }
    // XXX: check for nulls in target_audience?
    if (!isValidHttpUri(service.getService_uri())) {
      return "Service missing service_uri";
    }
    if (service.getNotification_uri() != null && !isValidHttpUri(service.getNotification_uri())) {
      return "Invalid notification_uri: " + service.getNotification_uri();
    }
    if (service.getRedirect_uris().isEmpty()) {
      return "Service missing redirect_uris";
    }
    for (String redirect_uri : service.getRedirect_uris()) {
      if (redirect_uri == null || !RedirectUri.isValid(redirect_uri)) {
        return "Invalid redirect_uri: " + redirect_uri;
      }
    }
    for (String post_logout_redirect_uri : service.getPost_logout_redirect_uris()) {
      if (post_logout_redirect_uri == null || !RedirectUri.isValid(post_logout_redirect_uri)) {
        return "Invalid post_logout_redirect_uri: " + post_logout_redirect_uri;
      }
    }
    if (service.getSubscription_uri() != null && !isValidHttpUri(service.getSubscription_uri())) {
      return "Invalid subscription_uri: " + service.getSubscription_uri();
    }
    if (service.getSubscription_uri() != null && Strings.isNullOrEmpty(service.getSubscription_secret())) {
      return "Service missing subscription_secret when subscription_uri is provider";
    }
    for (URI geographical_area : service.getGeographical_areas()) {
      if (!isValidDataCoreUri(geographical_area)) {
        return "Invalid interesting_area: " + geographical_area;
      }
    }
    for (URI restricted_area : service.getRestricted_areas()) {
      if (!isValidDataCoreUri(restricted_area)) {
        return "Invalid restricted_area: " + restricted_area;
      }
    }
    return null;
  }

  private boolean isValidDataCoreUri(URI uri) {
    // XXX: for now we only check that it's an HTTP URI, there's room for improvement.
    return isValidHttpUri(uri);
  }

  /**
   * An HTTP URI must be absolute, with an http or https scheme.
   */
  private boolean isValidHttpUri(String uri) {
    if (Strings.isNullOrEmpty(uri)) {
      return false;
    }

    final URI parsedUri;
    try {
      parsedUri = new URI(uri);
    } catch (URISyntaxException use) {
      return false;
    }

    return isValidHttpUri(parsedUri);
  }

  private boolean isValidHttpUri(@Nullable URI uri) {
    if (uri == null) {
      return false;
    }

    if (!uri.isAbsolute() || uri.isOpaque()) {
      return false;
    }

    if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
      return false;
    }

    return true;
  }

  /**
   * A contact URI can be http, https, mailto or tel, but must be absolute.
   */
  private boolean isValidContactUri(String uri) {
    if (Strings.isNullOrEmpty(uri)) {
      return false;
    }

    final URI parsedUri;
    try {
      parsedUri = new URI(uri);
    } catch (URISyntaxException use) {
      return false;
    }

    if (!parsedUri.isAbsolute()) {
      return false;
    }

    if ("http".equals(parsedUri.getScheme()) || "https".equals(parsedUri.getScheme())) {
      return !parsedUri.isOpaque();
    }
    if ("mailto".equals(parsedUri.getScheme()) || "tel".equals(parsedUri.getScheme())) {
      return parsedUri.isOpaque();
    }
    // disallowed scheme
    return false;
  }
}
