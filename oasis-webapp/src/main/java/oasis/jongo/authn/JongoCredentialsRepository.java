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
package oasis.jongo.authn;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import oasis.jongo.JongoBootstrapper;
import oasis.model.authn.ClientType;
import oasis.model.authn.Credentials;
import oasis.model.authn.CredentialsRepository;

public class JongoCredentialsRepository implements CredentialsRepository, JongoBootstrapper {
  private final Jongo jongo;

  @Inject
  JongoCredentialsRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getCredentialsCollection() {
    return jongo.getCollection("credentials");
  }

  @Override
  public Credentials saveCredentials(ClientType type, String id, byte[] hash, byte[] salt) {
    return getCredentialsCollection().findAndModify("{ clientType:#, id:# }", type, id)
        .upsert()
        .returnNew()
        .with("{ $set: { hash:#, salt:# } }", hash, salt)
        .as(Credentials.class);
  }

  @Override
  public Credentials getCredentials(ClientType type, String id) {
    return getCredentialsCollection().findOne("{ clientType:#, id:# }", type, id).as(Credentials.class);
  }

  @Override
  public boolean deleteCredentials(ClientType type, String id) {
    return getCredentialsCollection().remove("{ clientType:#, id:# }", type, id).getN() > 0;
  }

  @Override
  public void bootstrap() {
    getCredentialsCollection().ensureIndex("{ clientType: 1, id: 1 }", "{ unique: 1 }");
  }
}
