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

package org.skywalking.apm.plugin.jdbc.postgresql;

import java.lang.reflect.Method;
import org.postgresql.jdbc.PgConnection;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.plugin.jdbc.define.StatementEnhanceInfos;
import org.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link CreateStatementInterceptor} intercepts the {@link PgConnection#createStatement} method in
 * {@link org.postgresql.jdbc.PgConnection}, {@link org.postgresql.jdbc.PgConnection} or {@link org.postgresql.jdbc3.Jdbc3Connection} class.
 *
 * @author zhangxin
 */
public class CreateStatementInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {

        if (ret instanceof EnhancedInstance) {
            ((EnhancedInstance)ret).setSkyWalkingDynamicField(new StatementEnhanceInfos((ConnectionInfo)objInst.getSkyWalkingDynamicField(), "", "CallableStatement"));
        }

        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
