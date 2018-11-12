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
package oasis.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.ibm.icu.util.ULocale;

public class MailMessage {
  private ULocale locale;
  private InternetAddress recipient;
  private SoyTemplateInfo subject;
  private SoyTemplateInfo body;
  private boolean html = true;
  private ImmutableMap<String, ?> data;
  private InternetAddress from;

  public ULocale getLocale() {
    return locale;
  }

  public MailMessage setLocale(ULocale locale) {
    this.locale = locale;
    return this;
  }

  public InternetAddress getRecipient() {
    return recipient;
  }

  public MailMessage setRecipient(InternetAddress recipient) {
    this.recipient = recipient;
    return this;
  }

  public MailMessage setRecipient(String recipient) throws AddressException {
    this.recipient = new InternetAddress(recipient, true);
    return this;
  }

  public MailMessage setRecipient(String address, @Nullable String personal) {
    try {
      this.recipient = new InternetAddress(address, personal, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      // We use a Unicode charset, so that shouldn't happen.
      throw new AssertionError(e);
    }
    return this;
  }

  public SoyTemplateInfo getSubject() {
    return subject;
  }

  public MailMessage setSubject(SoyTemplateInfo subject) {
    this.subject = subject;
    return this;
  }

  public SoyTemplateInfo getBody() {
    return body;
  }

  public MailMessage setBody(SoyTemplateInfo body) {
    this.body = body;
    return this;
  }

  public boolean isHtml() {
    return html;
  }

  public MailMessage setHtml() {
    this.html = true;
    return this;
  }

  public MailMessage setPlainText() {
    this.html = false;
    return this;
  }

  public ImmutableMap<String, ?> getData() {
    return data;
  }

  public MailMessage setData(ImmutableMap<String, ?> data) {
    this.data = data;
    return this;
  }

  public InternetAddress getFrom() {
    return from;
  }

  public MailMessage setFrom(@Nullable String from) throws AddressException {
    this.from = (from != null) ? new InternetAddress(from) : null;
    return this;
  }
}
