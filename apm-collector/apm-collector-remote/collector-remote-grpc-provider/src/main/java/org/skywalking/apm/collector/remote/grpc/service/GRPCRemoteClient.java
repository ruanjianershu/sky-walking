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

package org.skywalking.apm.collector.remote.grpc.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.skywalking.apm.collector.client.ClientException;
import org.skywalking.apm.collector.client.grpc.GRPCClient;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.remote.grpc.proto.Empty;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteCommonServiceGrpc;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteMessage;
import org.skywalking.apm.collector.remote.service.RemoteClient;
import org.skywalking.apm.collector.remote.service.RemoteDataIDGetter;
import org.skywalking.apm.collector.remote.service.RemoteDataMappingIdNotFoundException;
import org.skywalking.apm.commons.datacarrier.DataCarrier;
import org.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author peng-yongsheng
 */
public class GRPCRemoteClient implements RemoteClient {

    private final Logger logger = LoggerFactory.getLogger(GRPCRemoteClient.class);

    private final GRPCRemoteSerializeService service;
    private final GRPCClient client;
    private final DataCarrier<RemoteMessage> carrier;
    private final String address;
    private final RemoteDataIDGetter remoteDataIDGetter;
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final ScheduledExecutorService connectionKeeper = Executors.newSingleThreadScheduledExecutor();
    GRPCRemoteClient(GRPCClient client, RemoteDataIDGetter remoteDataIDGetter, int channelSize, int bufferSize) {
        this.address = client.toString();
        this.client = client;
        this.service = new GRPCRemoteSerializeService();
        this.remoteDataIDGetter = remoteDataIDGetter;
        this.carrier = new DataCarrier<>(channelSize, bufferSize);
        this.carrier.setBufferStrategy(BufferStrategy.BLOCKING);
        this.carrier.consume(new RemoteMessageConsumer(), 1);

    }
    public void startConnectionKeeper(boolean isLocalClient) {
        //连接保持守护进程
        connectionKeeper.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (! isAvailable()) {
                    try {
                        client.initialize();
                        available.compareAndSet(false, true);
                    } catch (ClientException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, isLocalClient ? 3 : 10, 10, TimeUnit.SECONDS);
    }
    public void stopConnectionKeeper() {
        connectionKeeper.shutdownNow();
    }

    @Override public final String getAddress() {
        return this.address;
    }

    @Override public void push(int graphId, int nodeId, Data data) {
        try {
            Integer remoteDataId = remoteDataIDGetter.getRemoteDataId(data.getClass());
            RemoteMessage.Builder builder = RemoteMessage.newBuilder();
            builder.setGraphId(graphId);
            builder.setNodeId(nodeId);
            builder.setRemoteDataId(remoteDataId);
            builder.setRemoteData(service.serialize(data));

            this.carrier.produce(builder.build());
            logger.debug("put remote message into queue, id: {}", data.getId());
        } catch (RemoteDataMappingIdNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }

    class RemoteMessageConsumer implements IConsumer<RemoteMessage> {
        @Override public void init() {
        }

        @Override public void consume(List<RemoteMessage> remoteMessages) {
            StreamObserver<RemoteMessage> streamObserver = createStreamObserver();
            for (RemoteMessage remoteMessage : remoteMessages) {
                streamObserver.onNext(remoteMessage);
            }
            streamObserver.onCompleted();
        }

        @Override public void onError(List<RemoteMessage> remoteMessages, Throwable t) {
            logger.error("RemoteMessageConsumer consume error {},address:{}", t, address);
            disableClient(t);
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }

    }
    private StreamObserver<RemoteMessage> createStreamObserver() {
        RemoteCommonServiceGrpc.RemoteCommonServiceStub stub = RemoteCommonServiceGrpc.newStub(client.getChannel());

        StreamStatus status = new StreamStatus(false);
        return stub.call(new StreamObserver<Empty>() {
            @Override public void onNext(Empty empty) {
            }

            @Override public void onError(Throwable throwable) {
                //zookeeper监听到达快于socket端口建立，当集群中其他collector获得到事件创建channel时，很可能socketServer还未建立
                //其他集群节点退出后本地节点分配给他的样本信息无法消费
                logger.error("createStreamObserver失败,error:{},address:{}",throwable.getMessage(), address, throwable);
                disableClient(throwable);
            }

            @Override public void onCompleted() {
                status.finished();
            }
        });
    }

    /**
     * 不再接收新的请求
     * @param t
     */
    private void disableClient(Throwable t) {
        if (! isUnAvailable(t)) {
            available.compareAndSet(true, false);
        }
    }
    class StreamStatus {

        private final Logger logger = LoggerFactory.getLogger(StreamStatus.class);

        private volatile boolean status;

        StreamStatus(boolean status) {
            this.status = status;
        }

        public boolean isFinish() {
            return status;
        }

        void finished() {
            this.status = true;
        }

        /**
         * @param maxTimeout max wait time, milliseconds.
         */
        public void wait4Finish(long maxTimeout) {
            long time = 0;
            while (!status) {
                if (time > maxTimeout) {
                    break;
                }
                try2Sleep(5);
                time += 5;
            }
        }

        /**
         * Try to sleep, and ignore the {@link InterruptedException}
         *
         * @param millis the length of time to sleep in milliseconds
         */
        private void try2Sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override public boolean equals(String address) {
        return this.address.equals(address);
    }

    @Override
    public boolean isAvailable() {
        return available.get() && null != client.getChannel() && ! client.getChannel().isShutdown();
    }


    @Override public int compareTo(RemoteClient o) {
        return this.address.compareTo(o.getAddress());
    }

    private boolean isUnAvailable(Throwable t) {
        boolean clientError = false;
        if (t instanceof StatusRuntimeException) {
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException)t;
            clientError = statusEquals(statusRuntimeException.getStatus(),
                    Status.UNAVAILABLE,
                    Status.PERMISSION_DENIED,
                    Status.UNAUTHENTICATED,
                    Status.RESOURCE_EXHAUSTED,
                    Status.UNKNOWN,
                    //INTERNAL: HTTP/2 error code: INTERNAL_ERROR
                    Status.INTERNAL
            );
        }
        return clientError;
    }
    private boolean statusEquals(Status sourceStatus, Status... potentialStatus) {
        for (Status status : potentialStatus) {
            if (sourceStatus.getCode() == status.getCode()) {
                return true;
            }
        }
        return false;
    }

}
