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
