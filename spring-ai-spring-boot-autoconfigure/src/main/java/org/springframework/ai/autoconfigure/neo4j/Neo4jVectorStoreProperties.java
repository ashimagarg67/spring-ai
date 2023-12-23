/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.autoconfigure.neo4j;

import org.springframework.ai.vectorstore.Neo4jVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Jingzhou Ou
 */
@ConfigurationProperties(Neo4jVectorStoreProperties.CONFIG_PREFIX)
public class Neo4jVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.neo4j";

	private String databaseName;

	private int embeddingDimension = Neo4jVectorStore.DEFAULT_EMBEDDING_DIMENSION;

	private Neo4jVectorStore.Neo4jDistanceType distanceType = Neo4jVectorStore.Neo4jDistanceType.COSINE;

	private String label = Neo4jVectorStore.DEFAULT_LABEL;

	private String embeddingProperty = Neo4jVectorStore.DEFAULT_EMBEDDING_PROPERTY;

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public int getEmbeddingDimension() {
		return embeddingDimension;
	}

	public void setEmbeddingDimension(int embeddingDimension) {
		this.embeddingDimension = embeddingDimension;
	}

	public Neo4jVectorStore.Neo4jDistanceType getDistanceType() {
		return distanceType;
	}

	public void setDistanceType(Neo4jVectorStore.Neo4jDistanceType distanceType) {
		this.distanceType = distanceType;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getEmbeddingProperty() {
		return embeddingProperty;
	}

	public void setEmbeddingProperty(String embeddingProperty) {
		this.embeddingProperty = embeddingProperty;
	}

}
