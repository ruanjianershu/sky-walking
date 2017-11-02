package org.skywalking.apm.toolkit.activation.log.logback.v1.x.mdc.automatic;

import ch.qos.logback.classic.pattern.MDCConverter;
import ch.qos.logback.core.util.OptionHelper;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

/**
 * @author zhangkewei
 */
public class MDCKeyInterceptor implements InstanceMethodsAroundInterceptor {
    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        MDCConverter converter = (MDCConverter)objInst;
        Boolean convert4TID = false;
        String[] key = OptionHelper.extractDefaultReplacement(converter.getFirstOption());
        if (null != key && key.length > 0 && Config.Toolkit.LOGBACK_MDC_TRACEID.equals(key[0])) {
            convert4TID = true;
        }
        objInst.setSkyWalkingDynamicField(convert4TID);
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}