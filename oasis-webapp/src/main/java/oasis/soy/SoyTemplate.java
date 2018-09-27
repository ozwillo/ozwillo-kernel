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
package oasis.soy;

import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.ibm.icu.util.ULocale;

import oasis.model.branding.BrandInfo;

@Immutable
public class SoyTemplate {
  private final SoyTemplateInfo templateInfo;
  @SuppressWarnings("Immutable")
  private final ULocale locale;
  private final SanitizedContent.ContentKind contentKind;
  @SuppressWarnings("Immutable")
  private final @Nullable ImmutableMap<String, ?> data;
  @SuppressWarnings("Immutable")
  private final @Nullable BrandInfo brandInfo;

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale) {
    this(templateInfo, locale, null, null, null);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale, @Nullable ImmutableMap<String, ?> data) {
    this(templateInfo, locale, null, data, null);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale, @Nullable ImmutableMap<String, ?> data, BrandInfo brandInfo) {
    this(templateInfo, locale, null, data, brandInfo);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale, @Nullable SanitizedContent.ContentKind contentKind) {
    this(templateInfo, locale, contentKind, null, null);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale, @Nullable SanitizedContent.ContentKind contentKind,
                     @Nullable ImmutableMap<String, ?> data) {
    this(templateInfo, locale, contentKind, data, null);
  }

  private SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale, @Nullable SanitizedContent.ContentKind contentKind,
      @Nullable ImmutableMap<String, ?> data, @Nullable BrandInfo brandInfo) {
    this.templateInfo = Preconditions.checkNotNull(templateInfo);
    this.locale = locale;
    this.contentKind = MoreObjects.firstNonNull(contentKind, SanitizedContent.ContentKind.HTML);
    this.data = data;
    this.brandInfo = brandInfo;
  }

  public SoyTemplateInfo getTemplateInfo() {
    return templateInfo;
  }

  public ULocale getLocale() {
    return locale;
  }

  public SanitizedContent.ContentKind getContentKind() {
    return contentKind;
  }

  @Nullable
  public ImmutableMap<String, ?> getData() {
    return data;
  }

  public BrandInfo getBrandInfo() {
    return brandInfo;
  }
}
