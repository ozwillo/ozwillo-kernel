/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
package oasis.model.applications.v2;

public class Application extends CatalogEntry {
  private String instantiation_uri;
  private String instantiation_secret;
  private String cancellation_uri;
  private String cancellation_secret;
  private boolean visible;

  public Application() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Application(Application other) {
    super(other);
    instantiation_uri = other.getInstantiation_uri();
    instantiation_secret = other.getInstantiation_secret();
    cancellation_uri = other.getCancellation_uri();
    cancellation_secret = other.getCancellation_secret();
    visible = other.isVisible();
  }

  @Override
  public EntryType getType() {
    return EntryType.APPLICATION;
  }

  public String getInstantiation_uri() {
    return instantiation_uri;
  }

  public void setInstantiation_uri(String instantiation_uri) {
    this.instantiation_uri = instantiation_uri;
  }

  public String getInstantiation_secret() {
    return instantiation_secret;
  }

  public void setInstantiation_secret(String instantiation_secret) {
    this.instantiation_secret = instantiation_secret;
  }

  public String getCancellation_uri() {
    return cancellation_uri;
  }

  public void setCancellation_uri(String cancellation_uri) {
    this.cancellation_uri = cancellation_uri;
  }

  public String getCancellation_secret() {
    return cancellation_secret;
  }

  public void setCancellation_secret(String cancellation_secret) {
    this.cancellation_secret = cancellation_secret;
  }

  @Override
  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }
}
