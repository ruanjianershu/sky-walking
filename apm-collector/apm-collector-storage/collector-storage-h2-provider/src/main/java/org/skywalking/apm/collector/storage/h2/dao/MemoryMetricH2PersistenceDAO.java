/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.h2.dao;

import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.skywalking.apm.collector.storage.dao.IMemoryMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.jvm.MemoryMetric;
import org.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;

/**
 * @author peng-yongsheng, clevertension
 */
public class MemoryMetricH2PersistenceDAO extends H2DAO implements IMemoryMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, MemoryMetric> {

    public MemoryMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public MemoryMetric get(String id) {
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(MemoryMetric data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryMetricTable.COLUMN_ID, data.getId());
        source.put(MemoryMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(MemoryMetricTable.COLUMN_IS_HEAP, data.getIsHeap());
        source.put(MemoryMetricTable.COLUMN_INIT, data.getInit());
        source.put(MemoryMetricTable.COLUMN_MAX, data.getMax());
        source.put(MemoryMetricTable.COLUMN_USED, data.getUsed());
        source.put(MemoryMetricTable.COLUMN_COMMITTED, data.getCommitted());
        source.put(MemoryMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchInsertSql(MemoryMetricTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(MemoryMetric data) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
