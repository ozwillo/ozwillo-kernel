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
package oasis.model.applications.v2;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ibm.icu.util.ULocale;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

public abstract class CatalogEntry extends CommonProperties {
  @Id private String id;
  private PaymentOption payment_option;
  private List<TargetAudience> target_audience;
  private List<String> category_ids;
  private LocalizableString tos_uri;
  private LocalizableString policy_uri;
  private List<String> contacts;
  private List<String> screenshot_uris;
  private List<ULocale> supported_locales;
  private Set<URI> geographical_areas;
  private Set<URI> restricted_areas;

  protected CatalogEntry() {
    target_audience = new ArrayList<>();
    category_ids = new ArrayList<>();
    tos_uri = new LocalizableString();
    policy_uri = new LocalizableString();
    contacts = new ArrayList<>();
    screenshot_uris = new ArrayList<>();
    supported_locales = new ArrayList<>();
    geographical_areas = new HashSet<>();
    restricted_areas = new HashSet<>();
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  protected CatalogEntry(CatalogEntry other) {
    super(other);
    payment_option = other.getPayment_option();
    target_audience = new ArrayList<>(other.getTarget_audience());
    category_ids = new ArrayList<>(other.getCategory_ids());
    tos_uri = new LocalizableString(other.getTos_uri());
    policy_uri = new LocalizableString(other.getPolicy_uri());
    contacts = new ArrayList<>(other.getContacts());
    screenshot_uris = new ArrayList<>(other.getScreenshot_uris());
    supported_locales = new ArrayList<>(other.getSupported_locales());
    geographical_areas = new HashSet<>(other.getGeographical_areas());
    restricted_areas = new HashSet<>(other.getRestricted_areas());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public abstract EntryType getType();

  public PaymentOption getPayment_option() {
    return payment_option;
  }

  public void setPayment_option(PaymentOption payment_option) {
    this.payment_option = payment_option;
  }

  public List<TargetAudience> getTarget_audience() {
    return target_audience;
  }

  public void setTarget_audience(List<TargetAudience> target_audience) {
    this.target_audience = target_audience;
  }

  public List<String> getCategory_ids() {
    return category_ids;
  }

  public void setCategory_ids(List<String> category_ids) {
    this.category_ids = category_ids;
  }

  public abstract boolean isVisible();

  public LocalizableString getTos_uri() {
    return tos_uri;
  }

  public void setTos_uri(LocalizableString tos_uri) {
    this.tos_uri = tos_uri;
  }

  public LocalizableString getPolicy_uri() {
    return policy_uri;
  }

  public void setPolicy_uri(LocalizableString policy_uri) {
    this.policy_uri = policy_uri;
  }

  public List<String> getContacts() {
    return contacts;
  }

  public void setContacts(List<String> contacts) {
    this.contacts = contacts;
  }

  public List<String> getScreenshot_uris() {
    return screenshot_uris;
  }

  public void setScreenshot_uris(List<String> screenshot_uris) {
    this.screenshot_uris = screenshot_uris;
  }

  public List<ULocale> getSupported_locales() {
    return supported_locales;
  }

  public void setSupported_locales(List<ULocale> supported_locales) {
    this.supported_locales = supported_locales;
  }

  public Set<URI> getGeographical_areas() {
    return geographical_areas;
  }

  public void setGeographical_areas(Set<URI> geographical_areas) {
    this.geographical_areas = geographical_areas;
  }

  public Set<URI> getRestricted_areas() {
    return restricted_areas;
  }

  public void setRestricted_areas(Set<URI> restricted_areas) {
    this.restricted_areas = restricted_areas;
  }

  public static enum EntryType {
    APPLICATION, SERVICE
  }

  public static enum TargetAudience {
    CITIZENS, PUBLIC_BODIES, COMPANIES
  }

  public static enum PaymentOption {
    FREE, PAID
  }
}
