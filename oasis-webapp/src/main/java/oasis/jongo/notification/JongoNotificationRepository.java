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
package oasis.jongo.notification;

import static com.google.common.base.Preconditions.*;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;

public class JongoNotificationRepository implements NotificationRepository, JongoBootstrapper {

  private static final Logger logger = LoggerFactory.getLogger(NotificationRepository.class);

  private final Jongo jongo;

  @Inject
  JongoNotificationRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getNotificationCollection() {
    return jongo.getCollection("notification");
  }

  @Override
  public Notification createNotification(Notification notification) {
    checkNotNull(notification.getMessage());
    checkNotNull(notification.getStatus());
    checkNotNull(notification.getTime());
    JongoNotification jongoNotification = new JongoNotification(notification);
    try {
      getNotificationCollection().insert(jongoNotification);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoNotification;
  }

  @Override
  public List<Notification> createNotifications(List<Notification> notifications) {
    List<Notification> jongoNotifications = new ArrayList<>(notifications.size());
    for (Notification notification : notifications) {
      checkNotNull(notification.getMessage());
      checkNotNull(notification.getStatus());
      checkNotNull(notification.getTime());
      jongoNotifications.add(new JongoNotification(notification));
    }
    try {
      getNotificationCollection().insert(jongoNotifications.toArray());
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoNotifications;
  }

  @Override
  public boolean deleteNotification(String notificationId) {
    WriteResult wr = getNotificationCollection().remove("{ id: # }", notificationId);

    if (wr.getN() != 1 && logger.isWarnEnabled()) {
      logger.warn("The notification {} does not exist.", notificationId);
    }
    return wr.getN() == 1;
  }

  @Override
  public Notification getNotification(String notificationId) {
    return getNotificationCollection()
        .findOne("{ id: # }", notificationId)
        .as(JongoNotification.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Notification> getNotifications(String userId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ user_id: # }", userId)
        .as(JongoNotification.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Notification> getNotifications(String userId, String instanceId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ user_id: #, instance_id: # }", userId, instanceId)
        .as(JongoNotification.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Notification> getNotifications(String userId, Notification.Status status) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ user_id: #, status: # }", userId, status)
        .as(JongoNotification.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Notification> getNotifications(String userId, String instanceId, Notification.Status status) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ user_id: #, instance_id: #, status: # }", userId, instanceId, status)
        .as(JongoNotification.class);
  }

  @Override
  public void markNotifications(String userId, List<String> notificationIds, Notification.Status status) {
    getNotificationCollection()
        .update("{ id: {$in: # }, user_id: # }", notificationIds, userId)
        .multi()
        .with("{ $set: { status: # } }", status);
  }

  @Override
  public void bootstrap() {
    getNotificationCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    // TODO: add indexes for all other accesses
  }
}
