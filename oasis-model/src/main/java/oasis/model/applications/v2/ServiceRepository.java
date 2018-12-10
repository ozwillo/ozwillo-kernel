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

import oasis.model.InvalidVersionException;

public interface ServiceRepository {
  Service createService(Service service);

  Service getService(String serviceId);

  Iterable<Service> getServicesOfInstance(String instanceId);

  Service getServiceByRedirectUri(String instanceId, String redirect_uri);

  Service getServiceByPostLogoutRedirectUri(String instanceId, String post_logout_redirect_uri);

  boolean deleteService(String serviceId, long[] versions) throws InvalidVersionException;

  Service updateService(Service service, long[] versions) throws InvalidVersionException;

  int deleteServicesOfInstance(String instanceId);

  int changeServicesStatusForInstance(String instanceId, Service.Status status);

  Service addPortal(String serviceId, String portalId, long[] versions) throws InvalidVersionException;

  Service removePortal(String serviceId, String portalId, long[] versions) throws InvalidVersionException;
}
