package oasis.soy;

import java.util.Locale;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.parseinfo.SoyTemplateInfo;

public class SoyTemplate {
  private final SoyTemplateInfo templateInfo;
  private final Locale locale;
  private final @Nullable SanitizedContent.ContentKind contentKind;
  private final @Nullable SoyMapData data;

  public SoyTemplate(SoyTemplateInfo templateInfo, Locale locale) {
    this(templateInfo, locale, null);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, Locale locale, @Nullable SoyMapData data) {
    this(templateInfo, locale, null, data);
  }

  public SoyTemplate(SoyTemplateInfo templateInfo, Locale locale, @Nullable SanitizedContent.ContentKind contentKind,
      @Nullable SoyMapData data) {
    this.templateInfo = Preconditions.checkNotNull(templateInfo);
    this.locale = locale;
    this.contentKind = contentKind;
    this.data = data;
  }

  public SoyTemplateInfo getTemplateInfo() {
    return templateInfo;
  }

  public Locale getLocale() {
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
