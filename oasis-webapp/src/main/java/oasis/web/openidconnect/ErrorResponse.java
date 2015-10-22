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
package oasis.web.openidconnect;

import java.net.URI;

import javax.annotation.Nullable;

public class ErrorResponse {
  private String error;
  private @Nullable String error_description;
  private @Nullable URI error_uri;

  public String getError() {
    return error;
  }

  public ErrorResponse setError(String error) {
    this.error = error;
    return this;
  }

  public @Nullable String getError_description() {
    return error_description;
  }

  public ErrorResponse setError_description(@Nullable String error_description) {
    this.error_description = error_description;
    return this;
  }

  public @Nullable URI getError_uri() {
    return error_uri;
  }

  public ErrorResponse setError_uri(@Nullable URI error_uri) {
    this.error_uri = error_uri;
    return this;
  }
}
