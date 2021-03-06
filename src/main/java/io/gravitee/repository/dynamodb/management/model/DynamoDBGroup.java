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
package io.gravitee.repository.dynamodb.management.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com) 
 * @author GraviteeSource Team
 */
@DynamoDBTable(tableName = DynamoDBGraviteeSchema.GROUP_TABLENAME)
public class DynamoDBGroup {
    @DynamoDBHashKey
    private String id;
    @DynamoDBAttribute
    private String name;
    @DynamoDBAttribute
    private Date createdAt;
    @DynamoDBAttribute
    private Date updatedAt;
    @DynamoDBAttribute
    private List<String> eventRules;
    @DynamoDBAttribute
    private List<String> administrators;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getEventRules() {
        return eventRules;
    }
    public void setEventRules(List<String> eventRules) {
        this.eventRules = eventRules;
    }

    public List<String> getAdministrators() {
        return administrators;
    }
    public void setAdministrators(List<String> administrators) {
        this.administrators = administrators;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamoDBGroup dynamoDBGroup = (DynamoDBGroup) o;
        return Objects.equals(id, dynamoDBGroup.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DynamoDBGroup{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
