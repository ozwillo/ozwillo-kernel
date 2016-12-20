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
package oasis.jongo.applications.v2;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;

public class JongoApplicationRepository implements ApplicationRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationRepository.class);

  static final String APPLICATIONS_COLLECTION = "applications";

  private final Jongo jongo;

  @Inject JongoApplicationRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Application getApplication(String applicationId) {
    return getApplicationsCollection()
        .findOne("{ id: # }", applicationId)
        .as(JongoApplication.class);
  }

  public Iterable<JongoApplication> getAllInCatalog() {
    return getApplicationsCollection()
        .find("{ visible: true }")
        .as(JongoApplication.class);
  }

  @Override
  public Application createApplication(Application application) {
    application = new JongoApplication(application);
    try {
      getApplicationsCollection().insert(application);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return application;
  }

  @Override
  public long getCountByProvider(String providerId) {
    return getApplicationsCollection()
        .count("{ provider_id: # }", providerId);
  }

  @Override
  public void bootstrap() {
    getApplicationsCollection().ensureIndex("{ id : 1 }", "{ unique: 1 }");
  }

  private MongoCollection getApplicationsCollection() {
    return jongo.getCollection(APPLICATIONS_COLLECTION);
  }
}
