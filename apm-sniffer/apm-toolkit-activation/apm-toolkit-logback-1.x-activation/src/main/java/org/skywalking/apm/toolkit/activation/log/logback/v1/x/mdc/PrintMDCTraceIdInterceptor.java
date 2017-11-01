package org.skywalking.apm.toolkit.activation.log.logback.v1.x.mdc;

import ch.qos.logback.classic.pattern.MDCConverter;
import ch.qos.logback.core.util.OptionHelper;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

/**
 * @author zhangkewei
 */
public class PrintMDCTraceIdInterceptor implements InstanceMethodsAroundInterceptor {
    private static final String TRACEID_KEY = "apmTraceId";
    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        MDCConverter converter = (MDCConverter)objInst;
        String[] key = OptionHelper.extractDefaultReplacement(converter.getFirstOption());
        if (null != key && TRACEID_KEY.equals(key[0])) {
            return ContextManager.getGlobalTraceId();
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}