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
package oasis.mail;

import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;

import com.google.common.base.CharMatcher;
import com.google.template.soy.data.SanitizedContent;

import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;

public class MailSender {
  private final MailModule.Settings settings;
  private final Session session;
  private final SoyTemplateRenderer templateRenderer;

  @Inject MailSender(MailModule.Settings settings, Session session, SoyTemplateRenderer templateRenderer) {
    this.settings = settings;
    this.session = session;
    this.templateRenderer = templateRenderer;
  }

  public void send(MailMessage message) throws MessagingException {
    String subject =  templateRenderer.renderAsString(
        new SoyTemplate(message.getSubject(), message.getLocale(), SanitizedContent.ContentKind.TEXT, message.getData()));

    String body = templateRenderer.renderAsString(
        new SoyTemplate(message.getBody(), message.getLocale(), message.isHtml() ? SanitizedContent.ContentKind.HTML : SanitizedContent.ContentKind.TEXT, message.getData()));

    MimeMessage msg = new MimeMessage(session);
    msg.setFrom(settings.from);
    msg.setRecipient(Message.RecipientType.TO, message.getRecipient());
    msg.setSubject(CharMatcher.WHITESPACE.trimAndCollapseFrom(subject.toString(), ' '));
    msg.setText(body.toString(), StandardCharsets.UTF_8.name(), message.isHtml() ? "html" : "plain");

    Transport.send(msg);
  }
}
