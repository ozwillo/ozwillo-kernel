package oasis.jongo.social;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.mongodb.WriteResult;

import oasis.model.directory.DirectoryRepository;
import oasis.model.social.Identity;
import oasis.model.social.IdentityRepository;

public class JongoIdentityRepository implements IdentityRepository {
  private static final Logger logger = LoggerFactory.getLogger(DirectoryRepository.class);
  private static final Function<JongoRelation, String> RELATION_TYPE_TRANSFORMER = new Function<JongoRelation, String>() {
    @Nullable
    @Override
    public String apply(@Nullable JongoRelation input) {
      return input == null ? null : input.getType();
    }
  };
  private static final Predicate<JongoRelation> NON_EMPTY_RELATION_FILTER = new Predicate<JongoRelation>() {
    @Override
    public boolean apply(@Nullable JongoRelation input) {
      return input != null && input.getIdentityIds() != null && !input.getIdentityIds().isEmpty();
    }
  };

  private final Jongo jongo;

  @Inject
  JongoIdentityRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Identity getIdentity(String identityId) {
    return getIdentityCollection().findOne("{id:#}", identityId).as(JongoIdentity.class);
  }

  @Override
  public Identity createIdentity(Identity identity) {
    JongoIdentity jongoIdentity = new JongoIdentity(identity);
    jongoIdentity.setUpdatedAt(System.currentTimeMillis());
    getIdentityCollection().insert(jongoIdentity);
    return jongoIdentity;
  }

  @Override
  public boolean deleteIdentity(String identityId) {
    WriteResult wr = getIdentityCollection().remove("{ id: # }", identityId);

    return wr.getN() != 0;
  }

  @Override
  public boolean addRelation(String sourceIdentityId, String relationType, String destIdentityId) {
    WriteResult wr = getIdentityCollection()
        .update("{ id: #, relations: { $elemMatch: { identityIds: { $ne: # }, type: # } } }", sourceIdentityId, destIdentityId, relationType)
        .with("{ $addToSet: { relations.$.identityIds: # } }", destIdentityId);

    if (wr.getN() > 0) {
      return true;
    }

    // Try with relation creation
    JongoRelation jongoRelation = new JongoRelation();
    jongoRelation.setIdentityIds(Lists.newArrayList(destIdentityId));
    jongoRelation.setType(relationType);

    wr = getIdentityCollection()
        .update("{ id: #, relations.type : { $ne: # } }", sourceIdentityId, relationType)
        .with("{ $push: { relations: # } }", jongoRelation);

    if (wr.getN() == 0) {
      // TODO: more precise message
      logger.warn("The identity {} does not exist or is already related as {} to {}", sourceIdentityId, relationType, destIdentityId);
      return false;
    }

    return true;
  }

  @Override
  public boolean removeRelation(String sourceIdentityId, String relationType, String destIdentityId) {
    WriteResult wr = getIdentityCollection()
        .update("{ id: #, relations: { $elemMatch: { identityIds: #, type: # } } }", sourceIdentityId, destIdentityId, relationType)
        .with("{ $pull: { relations.$.identityIds: # } }", destIdentityId);

    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The identity {} does not exist or is not related as {} to {}", sourceIdentityId, relationType, destIdentityId);
    }

    return wr.getN() > 0;
  }

  @Override
  public Collection<String> getRelationMembers(String identityId, String relationType) {

    JongoIdentity identity = getIdentityCollection()
        .findOne("{ id: #, relations.type: # }", identityId, relationType)
        .projection("{ relations.$: 1 }")
        .as(JongoIdentity.class);

    // try with only identityId
    if (identity == null) {
      if (getIdentityCollection().count("{ id: # }", identityId) != 0) {
        return Collections.emptyList();
      } else {
        return null;
      }
    }

    if (identity.getRelations() == null || identity.getRelations().isEmpty()) {
      return Collections.emptyList();
    }

    JongoRelation jongoRelation = identity.getRelations().get(0);
    if (jongoRelation == null || jongoRelation.getIdentityIds() == null) {
      return Collections.emptyList();
    }

    return jongoRelation.getIdentityIds();
  }

  @Override
  public Collection<String> getRelationTypes(String identityId) {
    JongoIdentity identity = getIdentityCollection()
        .findOne("{ id: # }", identityId)
        .projection("{ relations: 1 }")
        .as(JongoIdentity.class);

    if (identity == null) {
      return null;
    }

    if (identity.getRelations() == null) {
      return Collections.emptyList();
    }

    return Collections2.transform(Collections2.filter(identity.getRelations(), NON_EMPTY_RELATION_FILTER), RELATION_TYPE_TRANSFORMER);
  }

  @Override
  public boolean relationExists(String sourceIdentityId, String relationType, String destIdentityId) {
    return getIdentityCollection()
        .count("{ id: #, relations: { $elemMatch: { type: #, identityIds : # } } }", sourceIdentityId, relationType, destIdentityId) > 0;
  }

  @Override
  public String getRelationId(String identityId, String relationType) {
    JongoIdentity identity = getIdentityCollection()
        .findOne("{ id: #, relations.type: # }", identityId, relationType)
        .projection("{ relations.$: 1 }")
        .as(JongoIdentity.class);

    if (identity == null || identity.getRelations() == null || identity.getRelations().isEmpty()) {
      return null;
    }

    return identity.getRelations().get(0).getId();
  }

  @Override
  public Collection<String> getRelationIdsForIdentity(String identityId) {
    Iterable<JongoIdentity> identities = getIdentityCollection()
        .find("{ relations.identityIds: # }", identityId)
        .projection("{ relations: 1 }")
        .as(JongoIdentity.class);

    if (identities == null) {
      return Collections.emptyList();
    }

    Collection<String> res = new ArrayList<>();
    for (JongoIdentity identity : identities) {
      for (JongoRelation relation : identity.getRelations()) {
        if (relation.getIdentityIds() != null && relation.getIdentityIds().contains(identityId)) {
          res.add(relation.getId());
        }
      }
    }

    return res;
  }

  private MongoCollection getIdentityCollection() {
    return jongo.getCollection("identity");
  }

}
