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

package org.skywalking.apm.collector.agent.stream.worker.trace.noderef;

import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.storage.table.noderef.NodeReference;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.impl.AggregationWorker;

/**
 * @author peng-yongsheng
 */
public class NodeReferenceAggregationWorker extends AggregationWorker<NodeReference, NodeReference> {

    public NodeReferenceAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return 106;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NodeReference, NodeReference, NodeReferenceAggregationWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<NodeReference> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public NodeReferenceAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new NodeReferenceAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
