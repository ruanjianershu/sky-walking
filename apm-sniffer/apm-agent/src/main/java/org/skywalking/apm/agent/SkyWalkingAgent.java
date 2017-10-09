package org.skywalking.apm.agent;

import java.lang.instrument.Instrumentation;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.SnifferConfigInitializer;
import org.skywalking.apm.agent.core.logging.EasyLogResolver;
import org.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.EnhanceContext;
import org.skywalking.apm.agent.core.plugin.PluginBootstrap;
import org.skywalking.apm.agent.core.plugin.PluginException;
import org.skywalking.apm.agent.core.plugin.PluginFinder;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

/**
 * The main entrance of sky-waking agent,
 * based on javaagent mechanism.
 *
 * @author wusheng
 */
public class SkyWalkingAgent {
    private static final ILog logger;

    static {
        LogManager.setLogResolver(new EasyLogResolver());
        logger = LogManager.getLogger(SkyWalkingAgent.class);
    }

    /**
     * Main entrance.
     * Use byte-buddy transform to enhance all classes, which define in plugins.
     *
     * @param agentArgs
     * @param instrumentation
     * @throws PluginException
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        /**
         * 初始化配置文件配置优先级-D参数>-D配置文件>jar目录配置文件>默认配置
         * applicationCode、services必须配置，不能为空
         * zkevin
         */
        SnifferConfigInitializer.initialize();
        /**
         * javaAgent中间件插件需要配置要拦截的类、方法等
         * PluginFinder根据插件的参数匹配需要拦截哪些类
         */
        final PluginFinder pluginFinder = new PluginFinder(new PluginBootstrap().loadPlugins());
        /**
         * 通过ServiceLoader查找META-INF/services/目录下SPI服务并启动。
         * apm-agent-core/src/main/resources/META-INF/services/org.skywalking.apm.agent.core.boot.BootService
         */
        ServiceManager.INSTANCE.boot();
        /**
         * 定义系统钩子，当jvm进程关闭时停止ServiceLoader定义的服务
         */
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override public void run() {
                ServiceManager.INSTANCE.shutdown();
            }
        }, "skywalking service shutdown thread"));
        /**
         *构造AgentBuilder，对入参instrumentation进行字节码编织
         */
        new AgentBuilder.Default().type(pluginFinder.buildMatch()).transform(new AgentBuilder.Transformer() {
            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                ClassLoader classLoader, JavaModule module) {
                List<AbstractClassEnhancePluginDefine> pluginDefines = pluginFinder.find(typeDescription, classLoader);
                if (pluginDefines.size() > 0) {
                    DynamicType.Builder<?> newBuilder = builder;
                    EnhanceContext context = new EnhanceContext();
                    for (AbstractClassEnhancePluginDefine define : pluginDefines) {
                        DynamicType.Builder<?> possibleNewBuilder = define.define(typeDescription.getTypeName(), newBuilder, classLoader, context);
                        if (possibleNewBuilder != null) {
                            newBuilder = possibleNewBuilder;
                        }
                    }
                    if (context.isEnhanced()) {
                        logger.debug("Finish the prepare stage for {}.", typeDescription.getName());
                    }

                    return newBuilder;
                }

                logger.debug("Matched class {}, but ignore by finding mechanism.", typeDescription.getTypeName());
                return builder;
            }
        }).with(new AgentBuilder.Listener() {
            @Override
            public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {

            }

            @Override
            public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module,
                boolean loaded, DynamicType dynamicType) {
                if (logger.isDebugEnable()) {
                    logger.debug("On Transformation class {}.", typeDescription.getName());
                }
            }

            @Override
            public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module,
                boolean loaded) {

            }

            @Override public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded,
                Throwable throwable) {
                logger.error("Failed to enhance class " + typeName, throwable);
            }

            @Override
            public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
            }
        }).installOn(instrumentation);
    }
}
