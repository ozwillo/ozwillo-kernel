package oasis.web.applications;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.ServiceRepository;

@Path("/m/search")
@Api(value = "market-search", description = "Searches the market catalog")
public class MarketSearchEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(MarketSearchEndpoint.class);

  @Inject ApplicationRepository applicationRepository;
  @Inject ServiceRepository serviceRepository;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Searches the market catalog",
      response = CatalogEntry.class,
      responseContainer = "Array"
  )
  public Response search(
      // TODO: add search criterias
      @Nullable @QueryParam("hl") final Locale locale,
      @DefaultValue("0") @QueryParam("start") int start,
      @DefaultValue("25") @QueryParam("limit") int limit) {
    // TODO: use ElasticSearch
    // TODO: filter to return only data for the locale passed in parameter (if any)
    // TODO: add information about apps the user has already "bought"
    return Response.ok()
        .entity(new GenericEntity<List<CatalogEntry>>(
            FluentIterable.from(
                Iterables.mergeSorted(
                    Arrays.asList(
                        applicationRepository.getVisibleApplications(),
                        serviceRepository.getVisibleServices()
                    ),
                    new Comparator<CatalogEntry>() {
                      Locale l = Objects.firstNonNull(locale, Locale.ROOT);

                      @Override
                      public int compare(CatalogEntry o1, CatalogEntry o2) {
                        return ComparisonChain.start()
                            .compare(o1.getName().get(l), o2.getName().get(l))
                            .result();
                      }
                    }
                )
            )
                .skip(start)
                .limit(limit)
                .transform(new Function<CatalogEntry, CatalogEntry>() {
                  @Nullable
                  @Override
                  public CatalogEntry apply(final @Nullable CatalogEntry input) {
                    // Filter to only send CatalogEntry fields (avoids sending secrets over the wire)
                    return new CatalogEntry(input) {
                      final EntryType type = input.getType();

                      {
                        setId(input.getId());
                      }

                      @Override
                      public EntryType getType() {
                        return type;
                      }
                    };
                  }
                })
                .toList()
        ) {})
        .build();
  }
}
