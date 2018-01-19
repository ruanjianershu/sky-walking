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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.skywalking.apm.collector.cluster.ClusterModuleListener;
import org.skywalking.apm.collector.core.UnexpectedException;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.remote.RemoteModule;
import org.skywalking.apm.collector.remote.grpc.RemoteModuleGRPCProvider;
import org.skywalking.apm.collector.remote.grpc.service.selector.ForeverFirstSelector;
import org.skywalking.apm.collector.remote.grpc.service.selector.HashCodeSelector;
import org.skywalking.apm.collector.remote.grpc.service.selector.RollingSelector;
import org.skywalking.apm.collector.remote.service.RemoteClient;
import org.skywalking.apm.collector.remote.service.RemoteDataIDGetter;
import org.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.skywalking.apm.collector.remote.service.Selector;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteSenderService extends ClusterModuleListener implements RemoteSenderService {

    private static final String PATH = "/" + RemoteModule.NAME + "/" + RemoteModuleGRPCProvider.NAME;
    private static  final String CLUSTER_MODE_NATIVE = "native";
    private static  final String CLUSTER_MODE_PROXY = "proxy";
    private final GRPCRemoteClientService service;
    private List<RemoteClient> remoteClients;
    private final String selfAddress;
    private final HashCodeSelector hashCodeSelector;
    private final ForeverFirstSelector foreverFirstSelector;
    private final RollingSelector rollingSelector;
    private final int channelSize;
    private final int bufferSize;
    private final String clusterMode;

    @Override public Mode send(int graphId, int nodeId, Data data, Selector selector) {
        //当前可用的Client
        List<RemoteClient> availableList = remoteClients.stream().filter(c -> c.isAvailable()).collect(Collectors.toList());
        RemoteClient remoteClient;
        switch (selector) {
            case HashCode:
                remoteClient = hashCodeSelector.select(availableList, data);
                return sendToRemoteWhenNotSelf(remoteClient, graphId, nodeId, data);
            case Rolling:
                remoteClient = rollingSelector.select(availableList, data);
                return sendToRemoteWhenNotSelf(remoteClient, graphId, nodeId, data);
            case ForeverFirst:
                remoteClient = foreverFirstSelector.select(availableList, data);
                return sendToRemoteWhenNotSelf(remoteClient, graphId, nodeId, data);
        }
        throw new UnexpectedException("Selector not match, Just support hash, rolling, forever first selector.");
    }

    private Mode sendToRemoteWhenNotSelf(RemoteClient remoteClient, int graphId, int nodeId, Data data) {
        if (remoteClient.equals(selfAddress)) {
            return Mode.Local;
        } else {
            remoteClient.push(graphId, nodeId, data);
            return Mode.Remote;
        }
    }

    public GRPCRemoteSenderService(String host, int port, int channelSize, int bufferSize,
        RemoteDataIDGetter remoteDataIDGetter, String clusterMode) {
        this.service = new GRPCRemoteClientService(remoteDataIDGetter);
        this.remoteClients = new ArrayList<>();
        this.selfAddress = host + ":" + String.valueOf(port);
        this.hashCodeSelector = new HashCodeSelector();
        this.foreverFirstSelector = new ForeverFirstSelector();
        this.rollingSelector = new RollingSelector();
        this.channelSize = channelSize;
        this.bufferSize = bufferSize;
        this.clusterMode = clusterMode;
    }

    @Override public String path() {
        return PATH;
    }

    @Override public synchronized void serverJoinNotify(String serverAddress) {
        List<RemoteClient> newRemoteClients = new ArrayList<>();
        newRemoteClients.addAll(remoteClients);

        //代理实现集群模式，仅仅添加本地Client
        if ((clusterMode.equals(CLUSTER_MODE_PROXY) && serverAddress.equals(selfAddress))
                || clusterMode.equals(CLUSTER_MODE_NATIVE)) {
            String host = serverAddress.split(":")[0];
            int port = Integer.parseInt(serverAddress.split(":")[1]);
            RemoteClient remoteClient = service.create(host, port, channelSize, bufferSize);
            remoteClient.startConnectionKeeper(remoteClient.equals(selfAddress));
            newRemoteClients.add(remoteClient);
        }

        Collections.sort(newRemoteClients);

        this.remoteClients = newRemoteClients;
    }

    @Override public synchronized void serverQuitNotify(String serverAddress) {
        List<RemoteClient> newRemoteClients = new ArrayList<>();
        newRemoteClients.addAll(remoteClients);

        for (int i = newRemoteClients.size() - 1; i >= 0; i--) {
            RemoteClient remoteClient = newRemoteClients.get(i);
            if (remoteClient.equals(serverAddress)) {
                remoteClient.stopConnectionKeeper();
                newRemoteClients.remove(i);
                //因为对象含有线程池字段，稳妥起见,标识可以gc。
                remoteClient = null;
            }
        }

        this.remoteClients = newRemoteClients;
    }
}
