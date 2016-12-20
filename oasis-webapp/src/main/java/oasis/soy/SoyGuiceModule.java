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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyModule;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule;

import oasis.urls.Urls;

public class SoyGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new SoyModule());
    install(new XliffMsgPluginModule());

    requireBinding(Urls.class);
  }

  @Provides @Singleton SoyTofu provideSoyTofu(SoyFileSet.Builder builder) {
    return builder
        .add(getClass().getResource("/templates/Common.soy"))
        .add(getClass().getResource("/templates/Login.soy"))
        .add(getClass().getResource("/templates/Recover.soy"))
        .add(getClass().getResource("/templates/Logout.soy"))
        .add(getClass().getResource("/templates/Authorize.soy"))
        .add(getClass().getResource("/templates/ChangePassword.soy"))
        .add(getClass().getResource("/templates/OrgMembershipInvitation.soy"))
        .add(getClass().getResource("/templates/AppInstanceInvitation.soy"))
        .add(getClass().getResource("/templates/mail/SignUp.soy"))
        .add(getClass().getResource("/templates/mail/RecoverMail.soy"))
        .add(getClass().getResource("/templates/mail/OrgMembershipInvitationMail.soy"))
        .add(getClass().getResource("/templates/mail/AppInstanceInvitationMail.soy"))
        .add(getClass().getResource("/templates/notification/AppProvisioning.soy"))
        .add(getClass().getResource("/templates/notification/DeletedOrganizationMembership.soy"))
        .add(getClass().getResource("/templates/notification/ChangedAppInstanceStatus.soy"))
        .add(getClass().getResource("/templates/notification/ChangedOrganizationStatus.soy"))
        .add(getClass().getResource("/templates/notification/DeletedAppInstance.soy"))
        .add(getClass().getResource("/templates/notification/DeletedOrganization.soy"))
        .add(getClass().getResource("/templates/notification/OrgMembershipInvitationNotification.soy"))
        .add(getClass().getResource("/templates/notification/AppInstanceInvitationNotification.soy"))
        .build()
        .compileToTofu();
  }
}
