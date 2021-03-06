/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.repository.dynamodb.management;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import io.gravitee.repository.dynamodb.management.model.DynamoDBMembership;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.MembershipRepository;
import io.gravitee.repository.management.model.Membership;
import io.gravitee.repository.management.model.MembershipReferenceType;
import io.gravitee.repository.management.model.RoleScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com) 
 * @author GraviteeSource Team
 */
@Repository
public class DynamoDBMembershipRepository implements MembershipRepository {

    private final Logger LOGGER = LoggerFactory.getLogger(DynamoDBMembershipRepository.class);

    @Autowired
    private DynamoDBMapper mapper;

    @Override
    public Membership create(Membership membership) throws TechnicalException {
        if (membership == null) {
            throw new IllegalArgumentException("Trying to create null");
        }
        mapper.save(
                convert(membership),
                new DynamoDBSaveExpression().withExpectedEntry(
                        "id",
                        new ExpectedAttributeValue().withExists(false)
                )
        );
        return membership;
    }

    @Override
    public Membership update(Membership membership) throws TechnicalException {
        if (membership == null) {
            throw new IllegalStateException("Trying to update null");
        }
        DynamoDBMembership dynamoDBMembership = convert(membership);
        DynamoDBMembership load = mapper.load(DynamoDBMembership.class, dynamoDBMembership.getId());
        if (load == null) {
            throw new IllegalStateException(String.format("No membership found with id [%s]", dynamoDBMembership.getId()));
        }
        mapper.save(
                dynamoDBMembership,
                new DynamoDBSaveExpression().withExpectedEntry(
                        "id",
                        new ExpectedAttributeValue().
                                withValue(new AttributeValue().withS(dynamoDBMembership.getId())).
                                withExists(true)
                )
        );
        return membership;
    }

    @Override
    public void delete(Membership membership) throws TechnicalException {
        if (membership == null) {
            throw new IllegalArgumentException("Trying to delete null");
        }
        DynamoDBMembership dynamoDBMembership = convert(membership);
        mapper.delete(dynamoDBMembership);
    }

    @Override
    public Optional<Membership> findById(String userId, MembershipReferenceType membershipReferenceType, String referenceId) throws TechnicalException {
        if (membershipReferenceType == null) {
            throw new IllegalArgumentException("membershipReferenceType is null");
        }
        DynamoDBMembership load = mapper.load(DynamoDBMembership.class, getMembershipKey(userId, membershipReferenceType.name(), referenceId));
        return Optional.ofNullable(convert(load));
    }

    @Override
    public Set<Membership> findByIds(String userId, MembershipReferenceType referenceType, Set<String> referenceIds) throws TechnicalException {
        Map<String, List<Object>> result = mapper.batchLoad(referenceIds.stream().map(referenceId -> {
            DynamoDBMembership dynamoDBMembership = new DynamoDBMembership();
            dynamoDBMembership.setId(getMembershipKey(userId, referenceType.name(), referenceId));
            return dynamoDBMembership;
        }).collect(Collectors.toSet()));

        if (result != null && !result.isEmpty()) {
            List<Object> memberships = result.entrySet().iterator().next().getValue();
            return memberships.stream().map(o -> convert((DynamoDBMembership)o)).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    @Override
    public Set<Membership> findByReferenceAndRole(MembershipReferenceType membershipReferenceType, String referenceId, RoleScope roleScope, String roleName) throws TechnicalException {
        DynamoDBMembership dynamoDBMembership = new DynamoDBMembership();
        dynamoDBMembership.setReferenceId(referenceId);
        String membershipType = convertRoleToType(roleScope, roleName);
        return mapper.query(DynamoDBMembership.class, new DynamoDBQueryExpression<DynamoDBMembership>().
                withConsistentRead(false).
                withHashKeyValues(dynamoDBMembership).
                withRangeKeyCondition("referenceType", new Condition().
                        withComparisonOperator(ComparisonOperator.EQ).
                        withAttributeValueList(new AttributeValue().
                                withS(membershipReferenceType.name())))).

                stream().
                filter(membership -> membershipType == null || membership.getRoles().contains(membershipType)).
                map(this::convert).
                collect(Collectors.toSet());
    }

    @Override
    public Set<Membership> findByReferencesAndRole(MembershipReferenceType membershipReferenceType, List<String> referenceIds, RoleScope roleScope, String roleName) throws TechnicalException {
        DynamoDBMembership dynamoDBMembership = new DynamoDBMembership();
        Set<Membership> result = new HashSet<>();
        String membershipType = convertRoleToType(roleScope, roleName);
        referenceIds.forEach(referenceId -> {
                    dynamoDBMembership.setReferenceId(referenceId);
                    result.addAll(
                            mapper.query(DynamoDBMembership.class, new DynamoDBQueryExpression<DynamoDBMembership>().
                                    withConsistentRead(false).
                                    withHashKeyValues(dynamoDBMembership).
                                    withRangeKeyCondition("referenceType", new Condition().
                                            withComparisonOperator(ComparisonOperator.EQ).
                                            withAttributeValueList(new AttributeValue().
                                                    withS(membershipReferenceType.name())))).
                                    stream().
                                    filter(membership -> membershipType == null || membership.getRoles().contains(membershipType)).
                                    map(this::convert).
                                    collect(Collectors.toSet()));
                }
        );
        return result;
    }

    @Override
    public Set<Membership> findByUserAndReferenceType(String userId, MembershipReferenceType membershipReferenceType) throws TechnicalException {
        DynamoDBMembership dynamoDBMembership = new DynamoDBMembership();
        dynamoDBMembership.setUserId(userId);
        return mapper.query(DynamoDBMembership.class, new DynamoDBQueryExpression<DynamoDBMembership>().
                withConsistentRead(false).
                withHashKeyValues(dynamoDBMembership).
                withRangeKeyCondition("referenceType", new Condition().
                        withComparisonOperator(ComparisonOperator.EQ).
                        withAttributeValueList(new AttributeValue().
                                withS(membershipReferenceType.name())))).

                stream().
                map(this::convert).
                collect(Collectors.toSet());
    }

    @Override
    public Set<Membership> findByUserAndReferenceTypeAndRole(String userId, MembershipReferenceType membershipReferenceType, RoleScope roleScope, String roleName) throws TechnicalException {
        String role = this.convertRoleToType(roleScope, roleName);
        DynamoDBMembership dynamoDBMembership = new DynamoDBMembership();
        dynamoDBMembership.setUserId(userId);
        return mapper.query(DynamoDBMembership.class, new DynamoDBQueryExpression<DynamoDBMembership>().
                withConsistentRead(false).
                withHashKeyValues(dynamoDBMembership).
                withRangeKeyCondition("referenceType", new Condition().
                        withComparisonOperator(ComparisonOperator.EQ).
                        withAttributeValueList(new AttributeValue().
                                withS(membershipReferenceType.name())))).
                stream().
                filter(membership -> membership.getRoles() != null && !membership.getRoles().isEmpty() && membership.getRoles().contains(role)).
                map(this::convert).
                collect(Collectors.toSet());
    }

    private String getMembershipKey(String userId, String referenceType, String referenceId) {
        return userId + ":" + referenceType + ":" + referenceId;
    }

    private DynamoDBMembership convert(Membership membership) {
        if (membership == null) {
            return null;
        }
        DynamoDBMembership dynamoDBMembership = new DynamoDBMembership();
        dynamoDBMembership.setUserId(membership.getUserId());
        dynamoDBMembership.setReferenceId(membership.getReferenceId());
        dynamoDBMembership.setReferenceType(membership.getReferenceType().name());
        dynamoDBMembership.setId(getMembershipKey(dynamoDBMembership.getUserId(), dynamoDBMembership.getReferenceType(), dynamoDBMembership.getReferenceId()));
        if (membership.getRoles() != null) {
            Set<String> roles = new HashSet<>(membership.getRoles().size());
            for (Map.Entry<Integer, String> roleEntry : membership.getRoles().entrySet()) {
                roles.add(convertRoleToType(roleEntry.getKey(), roleEntry.getValue()));
            }

            dynamoDBMembership.setRoles(roles);
        }
        dynamoDBMembership.setCreatedAt(membership.getCreatedAt() != null ? membership.getCreatedAt().getTime() : 0);
        dynamoDBMembership.setUpdatedAt(membership.getUpdatedAt() != null ? membership.getUpdatedAt().getTime() : 0);
        return dynamoDBMembership;
    }

    private Membership convert(DynamoDBMembership dynamoDBMembership) {
        if (dynamoDBMembership == null) {
            return null;
        }
        Membership membership = new Membership();
        membership.setUserId(dynamoDBMembership.getUserId());
        membership.setReferenceId(dynamoDBMembership.getReferenceId());
        membership.setReferenceType(MembershipReferenceType.valueOf(dynamoDBMembership.getReferenceType()));
        if (dynamoDBMembership.getRoles() != null) {
            Map<Integer, String> roles = new HashMap<>(dynamoDBMembership.getRoles().size());
            for (String roleAsString : dynamoDBMembership.getRoles()) {
                String[] role = convertTypeToRole(roleAsString);
                roles.put(Integer.valueOf(role[0]), role[1]);
            }
            membership.setRoles(roles);
        }
        membership.setCreatedAt(dynamoDBMembership.getCreatedAt() == 0 ? null : new Date(dynamoDBMembership.getCreatedAt()));
        membership.setUpdatedAt(dynamoDBMembership.getUpdatedAt() == 0 ? null : new Date(dynamoDBMembership.getUpdatedAt()));
        return membership;
    }

    private String convertRoleToType(RoleScope roleScope, String roleName) {
        if (roleName == null) {
            return null;
        }
        return convertRoleToType(roleScope.getId(), roleName);
    }

    private String convertRoleToType(int roleScope, String roleName) {
        return roleScope + ":" + roleName;
    }

    private String[] convertTypeToRole(String type) {
        if(type == null) {
            return null;
        }
        String[] role = type.split(":");
        if (role .length != 2) {
            return null;
        }
        return role;
    }
}
