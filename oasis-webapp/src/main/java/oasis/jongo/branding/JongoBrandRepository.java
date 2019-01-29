/**
 * Ozwillo Kernel
 * Copyright (C) 2018  The Ozwillo Kernel Authors
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
package oasis.jongo.branding;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import oasis.jongo.JongoBootstrapper;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;

public class JongoBrandRepository implements BrandRepository, JongoBootstrapper {
  private final Jongo jongo;

  @Inject
  JongoBrandRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getBrandCollection() {
    return jongo.getCollection("brand_info");
  }

  @Override
  public BrandInfo getBrandInfo(String brandId) {
    if (BrandInfo.DEFAULT_BRAND.equals(brandId)) {
      return new BrandInfo();
    }
    BrandInfo res = this.getBrandCollection()
        .findOne("{ brand_id : #}", brandId)
        .projection("{small_logo: 0, large_logo: 0}")
        .as(BrandInfo.class);

    return res != null ? res : new BrandInfo();
  }

  @Override
  public byte[] getSmallLogo(String brandId) {
    return getBrandCollection()
        .findOne("{ brand_id: #}", brandId)
        .projection("{small_logo: 1}")
        .map(result -> (byte[]) result.get("small_logo"));
  }

  @Override
  public String getLargeLogo(String brandId) {
    return getBrandCollection()
        .findOne("{ brand_id: #}", brandId)
        .projection("{large_logo: 1}")
        .map(result -> (String) result.get("large_logo"));
  }

  @Override
  public void bootstrap() {
    getBrandCollection().ensureIndex("{ brand_id: 1 }", "{ unique: 1 }");
  }
}
