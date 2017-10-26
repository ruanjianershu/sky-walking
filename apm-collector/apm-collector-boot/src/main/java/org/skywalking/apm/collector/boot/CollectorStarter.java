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

package org.skywalking.apm.collector.boot;

import java.util.Map;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.core.framework.Starter;
import org.skywalking.apm.collector.core.module.ModuleConfigLoader;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleDefineLoader;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleGroupDefineLoader;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class CollectorStarter implements Starter {

    private final Logger logger = LoggerFactory.getLogger(CollectorStarter.class);
    private Map<String, ModuleGroupDefine> moduleGroupDefineMap;

    @Override public void start() throws CollectorException {
        /**
         * 从配置文件application.yml初始化参数配置，如果application.yml不存在就从默认配置文件application-default.yml初始化
         * ModuleConfigLoader无需实例化，静态方法即可
         */
        ModuleConfigLoader configLoader = new ModuleConfigLoader();
        Map<String, Map> configuration = configLoader.load();
        /**
         * 从META-INF/defines/目录下拿到group.define文件
         * GROUP_NAME-CLASS_INSTANCE
         * 基于策略模式的
         * agent_jvm        org.skywalking.apm.collector.agentjvm.AgentJVMModuleGroupDefine
         * agent_register   org.skywalking.apm.collector.agentregister.AgentRegisterModuleGroupDefine
         * agent_server     org.skywalking.apm.collector.agentserver.AgentServerModuleGroupDefine
         * agent_stream     org.skywalking.apm.collector.agentstream.AgentStreamModuleGroupDefine
         * cluster          org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine
         * queue            org.skywalking.apm.collector.queue.QueueModuleGroupDefine
         * storage          org.skywalking.apm.collector.storage.StorageModuleGroupDefine
         * collector_inside org.skywalking.apm.collector.stream.StreamModuleGroupDefine
         * ui               org.skywalking.apm.collector.ui.UIModuleGroupDefine
         */
        ModuleGroupDefineLoader groupDefineLoader = new ModuleGroupDefineLoader();
        moduleGroupDefineMap = groupDefineLoader.load();

        /**
         * 从META-INF/defines/目录下拿到module.define文件
         *返回GROUP_NAME-(MODULE_NAME-CLASS_INSTANCE)
         */
        ModuleDefineLoader defineLoader = new ModuleDefineLoader();
        Map<String, Map<String, ModuleDefine>> moduleDefineMap = defineLoader.load();

        ServerHolder serverHolder = new ServerHolder();
        for (ModuleGroupDefine moduleGroupDefine : moduleGroupDefineMap.values()) {
            if (moduleGroupDefine.groupConfigParser() != null) {
                moduleGroupDefine.groupConfigParser().parse(configuration.get(moduleGroupDefine.name()));
            }
            //找到该group在appliction.yml对应的配置文件
            //moduleGroup.installer关联config、module
            moduleGroupDefine.moduleInstaller().injectConfiguration(configuration.get(moduleGroupDefine.name()), moduleDefineMap.get(moduleGroupDefine.name()));
            //moduleGroup.installer关联serverHolder
            moduleGroupDefine.moduleInstaller().injectServerHolder(serverHolder);
            /**
             * 分为single-module、mutil-module
             * 1.将配置文件appliction.yml参数映射到module实例
             * 2.将每个module的server实例与实例化的handler进行绑定并注入到serverHolder。也就是说Server是rpc服务器，handler是rpc提供的服务及路由
             */
            moduleGroupDefine.moduleInstaller().preInstall();
        }
        /**
         *集群模式仅zookeeper可用
         */
        moduleGroupDefineMap.get(ClusterModuleGroupDefine.GROUP_NAME).moduleInstaller().install();

        for (ModuleGroupDefine moduleGroupDefine : moduleGroupDefineMap.values()) {
            //排除集群实现模块，因为已经做了初始化
            if (!(moduleGroupDefine instanceof ClusterModuleGroupDefine)) {
                //构造上下文，做模块初始化工作
                moduleGroupDefine.moduleInstaller().install();
            }
        }
        //启动了三个server 代理上传样本 grpc server、 代理上传样本 http server、collector集群状态 http server
        serverHolder.getServers().forEach(server -> {
            try {
                server.start();
            } catch (ServerException e) {
                logger.error(e.getMessage(), e);
            }
        });
        /**
         * 初始化被引用的模块分组
         * cluster->collector_inside->queue
         */
        dependenceAfterInstall();
    }

    private void dependenceAfterInstall() throws CollectorException {
        for (ModuleGroupDefine moduleGroupDefine : moduleGroupDefineMap.values()) {
            moduleInstall(moduleGroupDefine);
        }
    }

    private void moduleInstall(ModuleGroupDefine moduleGroupDefine) throws CollectorException {
        if (CollectionUtils.isNotEmpty(moduleGroupDefine.moduleInstaller().dependenceModules())) {
            for (String groupName : moduleGroupDefine.moduleInstaller().dependenceModules()) {
                moduleInstall(moduleGroupDefineMap.get(groupName));
            }
            logger.info("after install module group: {}", moduleGroupDefine.name());
            moduleGroupDefine.moduleInstaller().afterInstall();
        } else {
            logger.info("after install module group: {}", moduleGroupDefine.name());
            moduleGroupDefine.moduleInstaller().afterInstall();
        }
    }
}
