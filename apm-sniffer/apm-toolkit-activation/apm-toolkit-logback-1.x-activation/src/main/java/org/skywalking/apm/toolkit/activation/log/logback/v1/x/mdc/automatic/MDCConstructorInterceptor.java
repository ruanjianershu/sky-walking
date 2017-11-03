package org.skywalking.apm.toolkit.activation.log.logback.v1.x.mdc.automatic;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * TODO
 * @author: zhangkewei
 * @version: V1.0
 */
public class MDCConstructorInterceptor  implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(Boolean.valueOf(false));
    }
}
