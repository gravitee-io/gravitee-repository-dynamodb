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

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import io.gravitee.repository.dynamodb.management.model.DynamoDBApiKey;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ApiKeyRepository;
import io.gravitee.repository.management.api.search.ApiKeyCriteria;
import io.gravitee.repository.management.model.ApiKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com) 
 * @author GraviteeSource Team
 */
@Repository
public class DynamoDBApiKeyRepository implements ApiKeyRepository{

    @Autowired
    private DynamoDBMapper mapper;

    @Override
    public Optional<ApiKey> findById(String id) throws TechnicalException {
        DynamoDBApiKey load = mapper.load(DynamoDBApiKey.class, id);
        return Optional.ofNullable(convert(load));
    }

    @Override
    public ApiKey create(ApiKey apiKey) throws TechnicalException {
        if (apiKey == null) {
            throw new IllegalArgumentException("Trying to create null");
        }

        mapper.save(
                convert(apiKey),
                new DynamoDBSaveExpression().withExpectedEntry(
                        "key",
                        new ExpectedAttributeValue().withExists(false)
                )
        );
        return apiKey;
    }

    @Override
    public ApiKey update(ApiKey apiKey) throws TechnicalException {
        if (apiKey == null || apiKey.getKey() == null) {
            throw new IllegalStateException("ApiKey to update must have an key");
        }

        if (!findById(apiKey.getKey()).isPresent()) {
            throw new IllegalStateException(String.format("No apiKey found with key [%s]", apiKey.getKey()));
        }
        mapper.save(
                convert(apiKey),
                new DynamoDBSaveExpression().withExpectedEntry(
                        "key",
                        new ExpectedAttributeValue().
                                withValue(new AttributeValue().withS(apiKey.getKey())).
                                withExists(true)
                )
        );
        return apiKey;
    }

    @Override
    public Set<ApiKey> findBySubscription(String subscription) throws TechnicalException {
        DynamoDBApiKey dynamoDBApiKey = new DynamoDBApiKey();
        dynamoDBApiKey.setSubscription(subscription);
        return mapper.query(DynamoDBApiKey.class, new DynamoDBQueryExpression<DynamoDBApiKey>().
                withConsistentRead(false).
                withHashKeyValues(dynamoDBApiKey)).
                stream().
                map(this::convert).
                collect(Collectors.toSet());
    }

    @Override
    public Set<ApiKey> findByPlan(String plan) throws TechnicalException {
        DynamoDBApiKey dynamoDBApiKey = new DynamoDBApiKey();
        dynamoDBApiKey.setPlan(plan);
        return mapper.query(DynamoDBApiKey.class, new DynamoDBQueryExpression<DynamoDBApiKey>().
                withConsistentRead(false).
                withHashKeyValues(dynamoDBApiKey)).
                stream().
                map(this::convert).
                collect(Collectors.toSet());
    }

    @Override
    public List<ApiKey> findByCriteria(ApiKeyCriteria filter) throws TechnicalException {
        if (filter.getPlans() == null || filter.getPlans().isEmpty()) {
            return Collections.emptyList();
        }

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        Map<String, AttributeValue> eav = new HashMap<>();
        StringJoiner filterExpression = new StringJoiner(" and ");

        //plan Ids
        int i = 0;
        StringJoiner planIds = new StringJoiner(",");
        for (String plan : filter.getPlans()) {
            String planAttr = ":plan" + (i++);
            eav.put(planAttr, new AttributeValue().withS(plan));
            planIds.add(planAttr);
        }
        filterExpression.add("#p in (" + planIds.toString() + ")");

        // time range
        if (filter.getFrom() != 0 && filter.getTo() != 0) {

            eav.put(":from", new AttributeValue().withN(Long.toString(filter.getFrom())));
            eav.put(":to", new AttributeValue().withN(Long.toString(filter.getTo())));
            filterExpression.add("updatedAt between :from and :to");
        }

        // revoked
        if (!filter.isIncludeRevoked()) {
            eav.put(":revoked", new AttributeValue().withBOOL(filter.isIncludeRevoked()));
            filterExpression.add("revoked = :revoked");
        }
        return mapper.scan(DynamoDBApiKey.class,
                scanExpression.
                        withFilterExpression(filterExpression.toString()).
                        withExpressionAttributeValues(eav).
                        withExpressionAttributeNames(Collections.singletonMap("#p", "plan")).
                        withConsistentRead(false)).
                stream().
                map(this::convert).
                collect(Collectors.toList());
    }

    private ApiKey convert(DynamoDBApiKey dynamoDBApiKey) {
        if (dynamoDBApiKey == null) {
            return null;
        }

        ApiKey apiKey = new ApiKey();

        apiKey.setKey(dynamoDBApiKey.getKey());
        apiKey.setApplication(dynamoDBApiKey.getApplication());
        apiKey.setSubscription(dynamoDBApiKey.getSubscription());
        apiKey.setPlan(dynamoDBApiKey.getPlan());
        if (dynamoDBApiKey.getCreatedAt() != 0) {
            apiKey.setCreatedAt(new Date(dynamoDBApiKey.getCreatedAt()));
        }
        if (dynamoDBApiKey.getUpdatedAt() != 0) {
            apiKey.setUpdatedAt(new Date(dynamoDBApiKey.getUpdatedAt()));
        }
        if (dynamoDBApiKey.getExpireAt() != 0) {
            apiKey.setExpireAt(new Date(dynamoDBApiKey.getExpireAt()));
        }
        if (dynamoDBApiKey.getRevokeAt() != 0) {
            apiKey.setRevokedAt(new Date(dynamoDBApiKey.getRevokeAt()));
        }
        apiKey.setRevoked(dynamoDBApiKey.isRevoked());

        return apiKey;
    }

    private DynamoDBApiKey convert(ApiKey apiKey) {
        DynamoDBApiKey dynamoDBApiKey = new DynamoDBApiKey();

        dynamoDBApiKey.setKey(apiKey.getKey());
        dynamoDBApiKey.setApplication(apiKey.getApplication());
        dynamoDBApiKey.setSubscription(apiKey.getSubscription());
        dynamoDBApiKey.setPlan(apiKey.getPlan());

        if (apiKey.getCreatedAt() != null) {
            dynamoDBApiKey.setCreatedAt(apiKey.getCreatedAt().getTime());
        }
        if (apiKey.getUpdatedAt() != null) {
            dynamoDBApiKey.setUpdatedAt(apiKey.getUpdatedAt().getTime());
        }
        if (apiKey.getExpireAt() != null) {
            dynamoDBApiKey.setExpireAt(apiKey.getExpireAt().getTime());
        }
        if (apiKey.getRevokedAt() != null) {
            dynamoDBApiKey.setRevokeAt(apiKey.getRevokedAt().getTime());
        }
        dynamoDBApiKey.setRevoked(apiKey.isRevoked());

        return dynamoDBApiKey;
    }
}
