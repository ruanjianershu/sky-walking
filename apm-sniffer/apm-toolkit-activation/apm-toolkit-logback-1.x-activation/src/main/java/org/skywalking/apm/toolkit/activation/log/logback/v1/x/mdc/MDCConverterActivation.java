package org.skywalking.apm.toolkit.activation.log.logback.v1.x.mdc;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * Support MDC https://logback.qos.ch/manual/mdc.html
 * @author: zhangkewei
 */
public class MDCConverterActivation extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("convert");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "org.skywalking.apm.toolkit.activation.log.logback.v1.x.mdc.PrintMDCTraceIdInterceptor";
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return byName("ch.qos.logback.classic.pattern.MDCConverter");
    }
}
