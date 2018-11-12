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
package oasis.services.branding;

import com.google.common.collect.ImmutableMap;

import oasis.model.branding.BrandInfo;

public class BrandHelper {
  public static final String BRAND_PARAM = "brand";

  public static ImmutableMap<String, String> toMap(BrandInfo brandInfo) {
    return new ImmutableMap.Builder<String, String>()
        .put("main_color", brandInfo.getMain_color())
        .put("main_color_dark", brandInfo.getMain_color_dark())
        .put("footer_text_color", brandInfo.getFooter_text_color())
        .put("text_color", brandInfo.getText_color())
        .put("button_text_color", brandInfo.getButton_text_color())
        .put("main_background_color", brandInfo.getMain_background_color())
        .put("footer_background_color", brandInfo.getFooter_background_color())
        .put("text_color_hover", brandInfo.getText_color_hover())
        .put("background_color_hover", brandInfo.getBackground_color_hover())
        .put("claim_required_text_color", brandInfo.getClaim_required_text_color())
        .put("claim_need_auth_text_color", brandInfo.getClaim_need_auth_text_color())
        .put("error_background_color", brandInfo.getError_background_color())
        .put("error_text_color", brandInfo.getError_text_color())
        .put("warning_background_color", brandInfo.getWarning_background_color())
        .put("warning_text_color", brandInfo.getWarning_text_color())
        .put("success_text_color", brandInfo.getSuccess_text_color())
        .put("brand_id", brandInfo.getBrand_id())
        .build();
  }

}
