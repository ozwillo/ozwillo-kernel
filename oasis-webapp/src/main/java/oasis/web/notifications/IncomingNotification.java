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
package oasis.web.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.i18n.LocalizableString;

class IncomingNotification {

  @JsonProperty String[] user_ids;

  @JsonProperty String service_id;

  @JsonProperty LocalizableString message = new LocalizableString();

  @JsonProperty LocalizableString action_uri = new LocalizableString();

  @JsonProperty LocalizableString action_label = new LocalizableString();
}

