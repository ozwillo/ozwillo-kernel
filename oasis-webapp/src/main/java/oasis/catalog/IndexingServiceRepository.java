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
package oasis.catalog;

import java.util.function.BiConsumer;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oasis.jest.applications.v2.JestCatalogEntryRepository;
import oasis.jongo.applications.v2.JongoServiceRepository;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;

public class IndexingServiceRepository implements ServiceRepository {
  private static final Logger logger = LoggerFactory.getLogger(IndexingServiceRepository.class);

  private final JongoServiceRepository jongoServiceRepository;
  private final JestCatalogEntryRepository jestCatalogEntryRepository;

  @Inject IndexingServiceRepository(JongoServiceRepository jongoServiceRepository,
      JestCatalogEntryRepository jestCatalogEntryRepository) {
    this.jongoServiceRepository = jongoServiceRepository;
    this.jestCatalogEntryRepository = jestCatalogEntryRepository;
  }

  @Override
  public Service createService(Service service) {
    Service createdService = jongoServiceRepository.createService(service);
    if (shouldIndex(createdService)) {
      jestCatalogEntryRepository.asyncIndex(createdService)
          .whenComplete(indexedFutureCallback(service.getId(), CatalogEntry.EntryType.SERVICE));
    }
    return createdService;
  }

  @Override
  public Service getService(String serviceId) {
    return jongoServiceRepository.getService(serviceId);
  }

  @Override
  public Iterable<Service> getServicesOfInstance(String instanceId) {
    return jongoServiceRepository.getServicesOfInstance(instanceId);
  }

  @Override
  public Service getServiceByRedirectUri(String instanceId, String redirect_uri) {
    return jongoServiceRepository.getServiceByRedirectUri(instanceId, redirect_uri);
  }

  @Override
  public Service getServiceByPostLogoutRedirectUri(String instanceId, String post_logout_redirect_uri) {
    return jongoServiceRepository.getServiceByPostLogoutRedirectUri(instanceId, post_logout_redirect_uri);
  }

  @Override
  public Service updateService(Service service, long[] versions) throws InvalidVersionException {
    Service oldService = jongoServiceRepository.getService(service.getId());
    service = jongoServiceRepository.updateService(service, versions);

    if (shouldIndex(service)) {
      jestCatalogEntryRepository.asyncIndex(service)
          .whenComplete(indexedFutureCallback(service.getId(), CatalogEntry.EntryType.SERVICE));
    } else if (shouldIndex(oldService)) {
      // It means it is no more indexable
      jestCatalogEntryRepository.asyncDelete(service.getId(), CatalogEntry.EntryType.SERVICE)
          .whenComplete(deleteIndexFutureCallback(service.getId(), CatalogEntry.EntryType.SERVICE));
    }
    return service;
  }

  @Override
  public boolean deleteService(String serviceId, long[] versions) throws InvalidVersionException {
    boolean deletedService = jongoServiceRepository.deleteService(serviceId, versions);
    if (deletedService) {
      jestCatalogEntryRepository.asyncDelete(serviceId, CatalogEntry.EntryType.SERVICE)
          .whenComplete(deleteIndexFutureCallback(serviceId, CatalogEntry.EntryType.SERVICE));
    }
    return deletedService;
  }

  @Override
  public int deleteServicesOfInstance(String instanceId) {
    int count = jongoServiceRepository.deleteServicesOfInstance(instanceId);
    jestCatalogEntryRepository.asyncDeleteServiceByInstance(instanceId)
        .whenComplete(deleteIndexRelatedToInstanceFutureCallback(instanceId));
    return count;
  }

  @Override
  public int changeServicesStatusForInstance(String instanceId, Service.Status status) {
    int count = jongoServiceRepository.changeServicesStatusForInstance(instanceId, status);
    switch (status) {
      case AVAILABLE:
        for (Service service : getServicesOfInstance(instanceId)) {
          if (shouldIndex(service)) {
            jestCatalogEntryRepository.asyncIndex(service)
                .whenComplete(indexedFutureCallback(service.getId(), CatalogEntry.EntryType.SERVICE));
          }
        }
        break;
      case NOT_AVAILABLE:
        jestCatalogEntryRepository.asyncDeleteServiceByInstance(instanceId)
            .whenComplete(deleteIndexRelatedToInstanceFutureCallback(instanceId));
        break;
      default:
        throw new IllegalArgumentException();
    }
    return count;
  }

  private boolean shouldIndex(Service service) {
    return service.isVisible() && service.getStatus() != Service.Status.NOT_AVAILABLE;
  }

  private BiConsumer<Void, Throwable> indexedFutureCallback(final String id, final CatalogEntry.EntryType entryType) {
    return (result, throwable) -> {
      if (throwable == null) {
        logger.trace("Successfully indexed {} {}", entryType.name(), id);
      } else {
        logger.error("Error when indexing {} {}", entryType.name(), id, throwable);
      }
    };
  }

  private BiConsumer<Void, Throwable> deleteIndexFutureCallback(final String id, final CatalogEntry.EntryType entryType) {
    return (result, throwable) -> {
      if (throwable == null) {
        logger.trace("Successfully removed {} {} from index", entryType.name(), id);
      } else {
        logger.error("Error when removing {} {} from index", entryType.name(), id, throwable);
      }
    };
  }

  private BiConsumer<Void, Throwable> deleteIndexRelatedToInstanceFutureCallback(final String instanceId) {
    return (result, throwable) -> {
      if (throwable == null) {
        logger.trace("Successfully removed catalog entries related to instance {} from index", instanceId);
      } else {
        logger.trace("Error when removing catalog entries related to instance {} from index", instanceId, throwable);
      }
    };
  }
}
