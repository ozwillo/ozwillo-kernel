package oasis.jongo.accounts;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.WriteResult;

import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.AgentAccount;
import oasis.model.accounts.UserAccount;

public class JongoAccountRepository implements AccountRepository {

  private final static Logger logger = LoggerFactory.getLogger(AccountRepository.class);

  @Inject
  protected Jongo jongo;

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  @Override
  public Account getAccount(String id) {
    return this.getAccountCollection().findOne("{id:#}", id).as(Account.class);
  }

  @Override
  public Account getAccountByTokenId(String tokenid) {
    return this.getAccountCollection().findOne("{tokens.id:#}", tokenid).projection("{id:1,type:1,tokens.$:1}").as(Account.class);
  }

  @Override
  public UserAccount getUserAccountByEmail(String email) {
    return this.getAccountCollection().findOne("{emailAddress:#}", email).as(UserAccount.class);
  }

  @Override
  public UserAccount getUserAccountById(String id) {
    return this.getAccountCollection().findOne("{id:#}", id).as(UserAccount.class);
  }

  @Override
  public AgentAccount getAgentAccountById(String id) {
    return getAccountCollection().findOne("{id:#}", id).as(AgentAccount.class);
  }

  @Override
  public String createAgentAccount(String organizationId, AgentAccount agent) {
    agent.setModified(System.currentTimeMillis());
    agent.setOrganizationId(organizationId);
    getAccountCollection().insert(agent);
    return agent.getId();
  }

  @Override
  public boolean deleteAgentAccount(String agentId) {
    // TODO: check modified
    WriteResult wr = getAccountCollection().remove("{ id: # }", agentId);
    if (wr.getN() != 1) {
      logger.warn("The agent {} does not exist", agentId);
    }

    return wr.getN() != 1;
  }

  @Override
  public boolean deleteAgentAccountsFromOrganization(String organizationId) {
    WriteResult wr = getAccountCollection().remove("{ organizationId: # }", organizationId);
    if (wr.getN() != 1) {
      logger.warn("The organization {} has no agents", organizationId);
    }

    return wr.getN() != 1;
  }

  @Override
  public Iterable<AgentAccount> getAgentsForOrganization(String organizationId, int start, int limit) {
    return getAccountCollection()
        .find("{ organizationId: # }", organizationId)
        .skip(start)
        .limit(limit)
        .as(AgentAccount.class);
  }

}

