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
package oasis.urls;

import java.net.URI;

import org.immutables.value.Value;

import com.google.common.base.Optional;

@Value.Immutable
public interface Urls {
  Optional<URI> canonicalBaseUri();

  Optional<URI> landingPage();

  Optional<URI> myOasis();

  Optional<URI> myProfile();

  Optional<URI> myApps();

  Optional<URI> myNetwork();

  Optional<URI> developerDoc();

  Optional<URI> privacyPolicy();

  Optional<URI> termsOfService();
}
