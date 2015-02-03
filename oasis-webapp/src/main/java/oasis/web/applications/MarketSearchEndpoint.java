package oasis.web.applications;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.ibm.icu.util.ULocale;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.ImmutableCatalogEntryRepository;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;

@Path("/m/search")
@Api(value = "market-search", description = "Searches the market catalog")
@OAuth
@Produces(MediaType.APPLICATION_JSON)
public class MarketSearchEndpoint {
  @Inject CatalogEntryRepository catalogEntryRepository;
  @Inject AccountRepository accountRepository;

  @Context SecurityContext context;

  @GET
  @ApiOperation(
      value = "Searches the market catalog",
      response = CatalogEntry.class,
      responseContainer = "Array"
  )
  public Response get(
      @Nullable @QueryParam("hl") ULocale locale,
      @DefaultValue("0") @QueryParam("start") int start,
      @DefaultValue("25") @QueryParam("limit") int limit,
      // TODO: handle full-text search
      // @Nullable @QueryParam("q") String query,
      @Nullable @QueryParam("geographical_areas") Set<URI> geographical_areas,
      @Nullable @QueryParam("restricted_areas") Set<URI> restricted_areas,
      @Nullable @QueryParam("target_audience") Set<CatalogEntry.TargetAudience> target_audience,
      @Nullable @QueryParam("payment_option") Set<CatalogEntry.PaymentOption> payment_option,
      @Nullable @QueryParam("category_id") Set<String> category_id
  ) {
    return post(locale, start, limit, geographical_areas, restricted_areas, target_audience, payment_option, category_id);
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response post(
      @Nullable @FormParam("hl") ULocale locale,
      @DefaultValue("0") @FormParam("start") int start,
      @DefaultValue("25") @FormParam("limit") int limit,
      // TODO: handle full-text search
      // @Nullable @FormParam("q") String query,
      @Nullable @QueryParam("geographical_areas") Set<URI> geographical_areas,
      @Nullable @QueryParam("restricted_areas") Set<URI> restricted_areas,
      @Nullable @FormParam("target_audience") Set<CatalogEntry.TargetAudience> target_audience,
      @Nullable @FormParam("payment_option") Set<CatalogEntry.PaymentOption> payment_option,
      @Nullable @FormParam("category_id") Set<String> category_id
  ) {
    if (locale == null && context.getUserPrincipal() != null) {
      String accountId = ((OAuthPrincipal) context.getUserPrincipal()).getAccessToken().getAccountId();
      UserAccount account = accountRepository.getUserAccountById(accountId);
      locale = account.getLocale();
    }
    // TODO: use ElasticSearch
    // TODO: add information about apps the user has already "bought"
    CatalogEntryRepository.SearchRequest request = ImmutableCatalogEntryRepository.SearchRequest.builder()
        .displayLocale(Optional.fromNullable(locale))
        .start(start)
        .limit(limit)
        .addAllGeographical_areas(preProcess(geographical_areas))
        .addAllRestricted_areas(preProcess(restricted_areas))
        .addAllTarget_audience(preProcess(target_audience))
        .addAllPayment_option(preProcess(payment_option))
        .addAllCategory_id(preProcessStr(category_id))
        .build();
    return Response.ok()
        .entity(new GenericEntity<Iterable<CatalogEntry>>(
            catalogEntryRepository.search(request)
        ) {})
        .build();
  }

  private Iterable<String> preProcessStr(@Nullable Set<String> s) {
    return Iterables.filter(preProcess(s), Predicates.not(Predicates.equalTo("")));
  }

  private <T> Iterable<T> preProcess(@Nullable Set<T> objects) {
    if (objects == null) {
      return Collections.emptySet();
    }
    return FluentIterable.from(objects)
        .filter(Predicates.notNull());
  }
}
