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
package oasis.model.branding;

import com.google.common.collect.ImmutableMap;

import oasis.model.annotations.Id;

public class BrandInfo {

  public static final String DEFAULT_BRAND = "ozwillo";

  @Id
  private String brand_id = DEFAULT_BRAND;

  /**
   * Use for button color and background color
   */
  private String main_color = "#6f438e";

  /**
   * Darker main color use for border button and input focus
    */
  private String main_color_dark = "#4c2d62";

  /**
   * Main text color
   */
  private String text_color = "#636884";

  /**
   * Footer and locale selector button
   */
  private String footer_text_color = "#4f5a65";

  private String button_text_color = "#fff";
  private String main_background_color = "white";

  private String footer_background_color = "#dfcae2";

  /**
   * Use in locales menu
   */
  private String text_color_hover = "#555";
  private String background_color_hover = "#eee";

  private String claim_required_text_color = "#f01d11";
  private String claim_need_auth_text_color = "black";

  private String error_background_color = "#d9534f";
  private String error_text_color = "white";

  private String warning_background_color = "#f0ad4e";
  private String warning_text_color = "white";

  private String success_text_color = "#5cb85c";

  public String getMain_color() {
    return main_color;
  }

  public String getMain_color_dark() {
    return main_color_dark;
  }

  public String getText_color() {
    return text_color;
  }

  public String getFooter_text_color() {
    return footer_text_color;
  }

  public String getButton_text_color() {
    return button_text_color;
  }

  public String getMain_background_color() {
    return main_background_color;
  }

  public String getFooter_background_color() {
    return footer_background_color;
  }

  public String getText_color_hover() {
    return text_color_hover;
  }

  public String getBackground_color_hover() {
    return background_color_hover;
  }

  public String getClaim_required_text_color() {
    return claim_required_text_color;
  }

  public String getClaim_need_auth_text_color() {
    return claim_need_auth_text_color;
  }

  public String getError_background_color() {
    return error_background_color;
  }

  public String getError_text_color() {
    return error_text_color;
  }

  public String getWarning_background_color() {
    return warning_background_color;
  }

  public String getWarning_text_color() {
    return warning_text_color;
  }

  public String getSuccess_text_color() {
    return success_text_color;
  }

  public String getBrand_id() {
    return brand_id;
  }

}
