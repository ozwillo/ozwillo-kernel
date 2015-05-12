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
package oasis.model.notification;

import java.util.List;

public interface NotificationRepository {

  /**
   * @return the generated notification id
   */
  Notification createNotification(Notification notification);

  List<Notification> createNotifications(List<Notification> notifications);

  boolean deleteNotification(String notificationId);

  Notification getNotification(String notificationId);

  Iterable<Notification> getNotifications(String userId);

  Iterable<Notification> getNotifications(String userId, String instanceId);

  Iterable<Notification> getNotifications(String userId, Notification.Status status);

  Iterable<Notification> getNotifications(String userId, String instanceId, Notification.Status status);

  void markNotifications(String userId, List<String> notificationIds, Notification.Status status);

}
