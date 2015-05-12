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
package oasis.services.authz;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import oasis.model.accounts.UserAccount;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Group;

public class GroupService {

  public static final Function<Group, String> GROUP_ID_TRANSFORMER = new Function<Group, String>() {
    @Nullable
    @Override
    public String apply(@Nullable Group input) {
      if (input == null) {
        return null;
      }
      return input.getId();
    }
  };

  private final DirectoryRepository directoryRepository;

  @Inject GroupService(DirectoryRepository directoryRepository) {
    this.directoryRepository = directoryRepository;
  }

  public List<String> getGroups(UserAccount account) {
    List<String> res = new ArrayList<>();

    // groups from organizations
    Collection<Group> groupsForAgent = directoryRepository.getGroupsForAgent(account.getId());
    if (groupsForAgent != null) {
      res.addAll(Collections2.transform(groupsForAgent, GROUP_ID_TRANSFORMER));
    }

    return res;
  }

}
