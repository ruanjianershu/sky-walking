package org.skywalking.apm.toolkit.activation.log.logback.v1.x.mdc.automatic;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

/**
 * @author zhangkewei
 */
public class PrintMDCTraceIdInterceptor implements InstanceMethodsAroundInterceptor {
    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Boolean convert4TID = (Boolean) objInst.getSkyWalkingDynamicField();
        if (convert4TID) {
            return ContextManager.getGlobalTraceId();
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}