package oasis.services.userdirectory;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.LocalDate;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import oasis.jongo.social.JongoIdentity;
import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.AgentAccount;
import oasis.model.social.Address;
import oasis.model.social.Identity;
import oasis.model.social.IdentityRepository;

public class UserDirectoryService {

  private final IdentityRepository identityRepository;
  private final AccountRepository accountRepository;

  private final Function<AgentAccount, AgentInfo> agentInfoTransformer = new Function<AgentAccount, AgentInfo>() {
    @Nullable
    @Override
    public AgentInfo apply(@Nullable AgentAccount input) {
      if (input == null) {
        return null;
      }
      Identity identity = null;
      if (input.getIdentityId() != null) {
        identity = identityRepository.getIdentity(input.getIdentityId());
      }
      return new AgentInfo(input, identity);
    }
  };

  @Inject
  UserDirectoryService(IdentityRepository identityRepository, AccountRepository accountRepository) {
    this.identityRepository = identityRepository;
    this.accountRepository = accountRepository;
  }

  public AgentInfo getAgentInfo(String agentId) {
    AgentAccount agent = accountRepository.getAgentAccountById(agentId);
    return agentInfoTransformer.apply(agent);
  }

  public Iterable<AgentInfo> getAgentsForOrganization(String organizationId, int start, int limit) {
    Iterable<AgentAccount> agents = accountRepository.getAgentsForOrganization(organizationId, start, limit);
    return Iterables.transform(agents, agentInfoTransformer);
  }

  public AgentInfo createAgentAccount(String organizationId, AgentInfo agentInfo) {

    Identity identity = createIdentity(agentInfo);
    JongoIdentity createdIdentity = null;
    if (identity != null) {
      createdIdentity = (JongoIdentity) identityRepository.createIdentity(identity);
      if (createdIdentity == null) {
        return null;
      }
    }

    AgentAccount agent = createAgentAccount(agentInfo);
    agent.setIdentityId(createdIdentity.getId());
    AgentAccount createdAgentAccount = accountRepository.createAgentAccount(organizationId, agent);

    if (createdAgentAccount == null) {
      // delete identity
      boolean deleted = identityRepository.deleteIdentity(createdIdentity.getId());
      if (!deleted) {
        // XXX: Identity does not exist, someone removed it for me. No matter, the job is done.
      }
    }

    return new AgentInfo(createdAgentAccount, createdIdentity);
  }

  public boolean deleteAgentAccount(String agentId, long[] versions) throws InvalidVersionException {
    AgentAccount agentAccount = accountRepository.findAndRemove(agentId, versions);

    if (agentAccount == null) {
      return false;
    }

    boolean deleted = identityRepository.deleteIdentity(agentAccount.getIdentityId());
    if (!deleted) {
      // XXX: Identity does not exist, someone removed it for me. No matter, the job is done.
    }
    return true;
  }

  private AgentAccount createAgentAccount(AgentInfo agentInfo) {
    AgentAccount res = new AgentAccount();

    res.setId(agentInfo.getId());
    res.setAdmin(agentInfo.isAdmin());
    res.setOrganizationId(agentInfo.getOrganizationId());
    res.setModified(agentInfo.getModified());

    res.setPicture(agentInfo.getPicture());
    res.setZoneInfo(agentInfo.getZoneinfo());
    res.setLocale(agentInfo.getLocale());
    res.setEmailAddress(agentInfo.getEmail());

    return res;
  }

  private Identity createIdentity(AgentInfo agentInfo) {
    Identity res = new Identity();

    // Address
    if (agentInfo.getAddress() != null) {
      Address address = new Address();
      address.setCountry(agentInfo.getAddress().getCountry());
      address.setLocality(agentInfo.getAddress().getLocality());
      address.setPostalCode(agentInfo.getAddress().getPostalCode());
      address.setRegion(agentInfo.getAddress().getRegion());
      address.setStreetAddress(agentInfo.getAddress().getStreetAddress());
      res.setAddress(address);
    }

    res.setName(agentInfo.getName());
    res.setFamilyName(agentInfo.getFamily_name());
    res.setGivenName(agentInfo.getGiven_name());
    res.setNickname(agentInfo.getNickname());
    res.setMiddleName(agentInfo.getMiddle_name());
    res.setGender(agentInfo.getGender());
    res.setBirthdate(LocalDate.parse(agentInfo.getBirthdate()));
    res.setPhoneNumber(agentInfo.getPhone());
    res.setPhoneNumberVerified(agentInfo.isPhone_verified());
    res.setUpdatedAt(agentInfo.getUpdated_at() == null ? 0 : agentInfo.getUpdated_at());

    return res;
  }
}
