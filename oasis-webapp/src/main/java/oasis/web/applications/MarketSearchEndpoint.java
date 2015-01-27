package oasis.web.applications;

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
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.ibm.icu.util.ULocale;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.ImmutableCatalogEntryRepository;

@Path("/m/search")
@Api(value = "market-search", description = "Searches the market catalog")
@Produces(MediaType.APPLICATION_JSON)
public class MarketSearchEndpoint {
  @Inject CatalogEntryRepository catalogEntryRepository;

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
      @Nullable @QueryParam("territory_id") Set<String> territory_id,
      @Nullable @QueryParam("target_audience") Set<CatalogEntry.TargetAudience> target_audience,
      @Nullable @QueryParam("payment_option") Set<CatalogEntry.PaymentOption> payment_option,
      @Nullable @QueryParam("category_id") Set<String> category_id
  ) {
    return post(locale, start, limit, territory_id, target_audience, payment_option, category_id);
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response post(
      @Nullable @FormParam("hl") ULocale locale,
      @DefaultValue("0") @FormParam("start") int start,
      @DefaultValue("25") @FormParam("limit") int limit,
      // TODO: handle full-text search
      // @Nullable @FormParam("q") String query,
      @Nullable @FormParam("territory_id") Set<String> territory_id,
      @Nullable @FormParam("target_audience") Set<CatalogEntry.TargetAudience> target_audience,
      @Nullable @FormParam("payment_option") Set<CatalogEntry.PaymentOption> payment_option,
      @Nullable @FormParam("category_id") Set<String> category_id
  ) {
    // TODO: use ElasticSearch
    // TODO: add information about apps the user has already "bought"
    CatalogEntryRepository.SearchRequest request = ImmutableCatalogEntryRepository.SearchRequest.builder()
        .displayLocale(Optional.fromNullable(locale))
        .start(start)
        .limit(limit)
        .addAllTerritory_id(preProcessStr(territory_id))
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
    if (s == null) {
      return Collections.emptySet();
    }
    return FluentIterable.from(s)
        .filter(Predicates.notNull())
        .filter(Predicates.not(Predicates.equalTo("")));
  }

  private <T extends Enum<T>> Iterable<T> preProcess(@Nullable Set<T> s) {
    if (s == null) {
      return Collections.emptySet();
    }
    return FluentIterable.from(s)
        .filter(Predicates.notNull());
  }
}
