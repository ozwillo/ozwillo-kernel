package oasis.services.authz;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import oasis.model.accounts.Account;
import oasis.model.accounts.AgentAccount;
import oasis.model.accounts.UserAccount;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Group;
import oasis.model.social.IdentityRepository;

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
  private final IdentityRepository identityRepository;

  private final DirectoryRepository directoryRepository;

  @Inject
  GroupService(IdentityRepository identityRepository, DirectoryRepository directoryRepository) {
    this.identityRepository = identityRepository;
    this.directoryRepository = directoryRepository;
  }

  public List<String> getGroups(Account account) {
    if (!(account instanceof UserAccount)) {
      return null;
    }

    List<String> res = new ArrayList<>();

    // groups from organization if account is a AgentAccount
    if (account instanceof AgentAccount) {
      Collection<Group> groupsForAgent = directoryRepository.getGroupsForAgent(account.getId());
      if (groupsForAgent != null) {
        res.addAll(Collections2.transform(groupsForAgent, GROUP_ID_TRANSFORMER));
      }
    }

    // groups from identity relation
    res.addAll(identityRepository.getRelationIdsForIdentity(((UserAccount) account).getIdentityId()));

    return res;
  }

}
