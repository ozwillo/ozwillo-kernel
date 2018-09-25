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
package oasis.web.applications;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Strings;
import com.google.common.collect.Streams;
import com.ibm.icu.util.ULocale;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.ImmutableCatalogEntryRepository;
import oasis.model.applications.v2.SimpleCatalogEntry;
import oasis.model.authn.AccessToken;
import oasis.model.bootstrap.ClientIds;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;

public abstract class AbstractMarketSearchEndpoint {
  @Inject CatalogEntryRepository catalogEntryRepository;
  @Inject AccountRepository accountRepository;

  @Context SecurityContext context;

  @GET
  @OAuth
  @Produces(MediaType.APPLICATION_JSON)
  public Response get(
      @Nullable @QueryParam("hl") ULocale locale,
      @DefaultValue("0") @QueryParam("start") int start,
      @DefaultValue("25") @QueryParam("limit") int limit,
      @Nullable @QueryParam("q") String query,
      @Nullable @QueryParam("supported_locale") List<ULocale> supported_locale,
      @Nullable @QueryParam("geographical_areas") Set<URI> geographical_areas,
      @Nullable @QueryParam("restricted_areas") Set<URI> restricted_areas,
      @Nullable @QueryParam("target_audience") Set<CatalogEntry.TargetAudience> target_audience,
      @Nullable @QueryParam("payment_option") Set<CatalogEntry.PaymentOption> payment_option,
      @Nullable @QueryParam("category_id") Set<String> category_id
  ) {
    return post(locale, start, limit, query, supported_locale, geographical_areas, restricted_areas, target_audience, payment_option, category_id);
  }

  @POST
  @OAuth
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  public Response post(
      @Nullable @FormParam("hl") ULocale locale,
      @DefaultValue("0") @FormParam("start") int start,
      @DefaultValue("25") @FormParam("limit") int limit,
      @Nullable @FormParam("q") String query,
      @Nullable @QueryParam("supported_locale") List<ULocale> supported_locale,
      @Nullable @QueryParam("geographical_area") Set<URI> geographical_area,
      @Nullable @QueryParam("restricted_area") Set<URI> restricted_area,
      @Nullable @FormParam("target_audience") Set<CatalogEntry.TargetAudience> target_audience,
      @Nullable @FormParam("payment_option") Set<CatalogEntry.PaymentOption> payment_option,
      @Nullable @FormParam("category_id") Set<String> category_id
  ) {
    final AccessToken accessToken = ((OAuthPrincipal) context.getUserPrincipal()).getAccessToken();

    if (locale == null && context.getUserPrincipal() != null) {
      String accountId = accessToken.getAccountId();
      UserAccount account = accountRepository.getUserAccountById(accountId);
      locale = account.getLocale();
    }

    String trimmedQuery = query;
    if (query != null) {
      trimmedQuery = Strings.emptyToNull(query.trim());
    }

    ImmutableCatalogEntryRepository.SearchRequest.Builder requestBuilder = ImmutableCatalogEntryRepository.SearchRequest.builder()
        .displayLocale(locale)
        .start(start)
        .limit(limit)
        .query(trimmedQuery)
        .addAllSupported_locale(preProcess(supported_locale))
        .addAllGeographical_area(preProcess(geographical_area))
        .addAllRestricted_area(preProcess(restricted_area))
        .addAllTarget_audience(preProcess(target_audience))
        .addAllPayment_option(preProcess(payment_option))
        .addAllCategory_id(preProcessStr(category_id));
    final Iterator<SimpleCatalogEntry> results = doSearch(accessToken, requestBuilder);
    return Response.ok()
        .entity(new GenericEntity<Iterator<SimpleCatalogEntry>>(results) {})
        .build();
  }

  protected abstract Iterator<SimpleCatalogEntry> doSearch(
      AccessToken accessToken, ImmutableCatalogEntryRepository.SearchRequest.Builder requestBuilder);

  private Iterable<String> preProcessStr(@Nullable Collection<String> strings) {
    return preProcessToStream(strings)
        .filter(s -> !s.isEmpty())
        ::iterator;
  }

  private <T> Iterable<T> preProcess(@Nullable Collection<T> objects) {
    return preProcessToStream(objects)::iterator;
  }

  private <T> Stream<T> preProcessToStream(@Nullable Collection<T> objects) {
    if (objects == null) {
      return Stream.empty();
    }
    return objects.stream().filter(Objects::nonNull);
  }
}
