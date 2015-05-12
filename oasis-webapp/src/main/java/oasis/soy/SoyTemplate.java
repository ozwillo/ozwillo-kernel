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
package oasis.soy;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.ibm.icu.util.ULocale;

public class SoyTemplate {
  private final SoyTemplateInfo templateInfo;
  private final ULocale locale;
  private final @Nullable SanitizedContent.ContentKind contentKind;
  private final @Nullable SoyMapData data;

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale) {
    this(templateInfo, locale, null, null);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale, @Nullable SoyMapData data) {
    this(templateInfo, locale, null, data);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale, @Nullable SanitizedContent.ContentKind contentKind) {
    this(templateInfo, locale, contentKind, null);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, ULocale locale, @Nullable SanitizedContent.ContentKind contentKind,
      @Nullable SoyMapData data) {
    this.templateInfo = Preconditions.checkNotNull(templateInfo);
    this.locale = locale;
    this.contentKind = contentKind;
    this.data = data;
  }

  public SoyTemplateInfo getTemplateInfo() {
    return templateInfo;
  }

  public ULocale getLocale() {
    return locale;
  }

  @Nullable
  public SanitizedContent.ContentKind getContentKind() {
    return contentKind;
  }

  @Nullable
  public SoyMapData getData() {
    return data;
  }
}
