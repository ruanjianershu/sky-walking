#javaAgent

---

- ###代码入口    
 		org.skywalking.apm.agent.SkyWalkingAgent.premain()
		
  		初始化配置文件配置优先级-D参数>-D配置文件>jar目录配置文件>默认配置
  		applicationCode、services必须配置，不能为空
  		javaAgent中间件插件需要配置要拦截的类、方法等
  		PluginFinder根据插件的参数匹配需要拦截哪些类
		通过ServiceLoader查找META-INF/services/目录下SPI服务并启动。
		apm-agent-core/src/main/resources/META-INF/services/
		定义系统钩子，当jvm进程关闭时停止ServiceLoader定义的服务
		构造AgentBuilder，对入参instrumentation进行字节码编织
		JavaAgent类     apm-sniffer/apm-agent
		中间件插件实现    apm-sniffer/apm-sdk-plugin
		apm-application-toolkit拦截     apm-sniffer/apm-toolkit-activation

- ###随代理启动服务接口:BootService
	- org.skywalking.apm.agent.core.remote.TraceSegmentServiceClient
	
			提交TraceSegment到收集器,如果无可用收集器就放弃提交.
			采用生产者、消费模式，构造了一个管道，多个消费者线程按照一定的规则分配数据发送到收集器,以TraceSegment为单位.
			在afterBoot()方法中注册监听TracingContext.ListenerManager.add(this)，当TraceSegment.finsh()触发监听时，
			TraceSegmentServiceClient.afterFinished()将traceSegment提交到DataCarrier
			消费者为ConsumerThread,实际发送数据代码为TraceSegmentServiceClient.consume
			消费者模式代码臃肿

	- org.skywalking.apm.agent.core.context.ContextManager

			根据SamplingService的样本采集规则创建IgnoredTracerContext或是TracerContext
			TracerContext.inject()导出当前上下文信息到ContentCarrier
			通过TracerContext.extract()将ContentCarrier导入当前上下文，形成对调用方的引用
			在boot方法里注册TraceSegment完成监听，从ThreadLocal清除TraceContext。


	- org.skywalking.apm.agent.core.remote.CollectorDiscoveryService
	
			每60秒根据selectedServer(即用户配置的收集器数组下标)从Config.Collector.SERVERS获取GRPC服务列表，更新RemoteDownstreamConfig.Collector.GRPC_SERVERS
			如果失败,selectedServer递增1，首次随机

   - org.skywalking.apm.agent.core.sampling.SamplingService

			如果通过Config.Agent.SAMPLE_N_PER_3_SECS配置启用采样(>0)，定时任务就会每三秒重置一次当前样本数。ContextManager根据SamplingService.trySampling()判断是否采集本次样本

   - org.skywalking.apm.agent.core.remote.GRPCChannelManager
   
		    每30秒检查一次当前对象channel，如果不可用就随机选择收集器构造channel对象，并通知监听该服务的对象

	- org.skywalking.apm.agent.core.jvm.JVMService
	
			每一秒收集一次硬件信息，插入队列，如果插入失败就丢弃最早未发送到收集器的信息并再次重新尝试
			每一秒发送一次硬件信息到收集器,如果RemoteDownstreamConfig.Collector.GRPC_SERVERS无可用收集器列表则放弃发送。
			同时通过监听器模式注册监听，监听GRPCChannelManager的channel状态。

	- org.skywalking.apm.agent.core.remote.AppAndServiceRegisterClient
	
			每十秒执行一次：
			1.提交applicationCode获得applicationId
			2.提交applicationId获得applicationInstanceId
			3.提交applicationId、applicationInstanceId发送心跳
			4.同步字典(应该是集群信息)
			
#collector

---

- ### 代码入口
		org.skywalking.apm.collector.boot.CollectorBootStartUp
        CollectorBootStartUp->CollectorStarter->ClusterModuleInstaller.onAfterInstall()->ClusterZKDataMonitor
		CollectorStarter->AgentStreamModuleGroupDefine.moduleInstaller().install()->PersistenceTimer.start()

- ### 模块
	- apm-collector/apm-collector-boot  收集器启动类
	- app-network
		             
		  .proto 通过protocol buffer编译插件生成客户端和服务端代码
	  	
	  	
	  	
	- apm-collector/apm-collector-agentregister
		
          agent的注册、发现、心跳，有grpc、http两种实现
          请求接入XXXXXRemoteWorker后根据匹配的RemoteWorkerRef属性acrossJVM判断是本地处理还是远程collector处理，
          如果是远程collector处理，会请求到RemoteCommonServiceHandler，最终再跳转到SerialWorker实现。
          grpc调用是集群形式,jetty http是单点同步请求
          
    - apm-collector/apm-collector-agentserver

          默认端口为10800，提供三个http接口:
          /ui/jetty   apm-web可用接口集群列表
          /agentstream/jetty   apm-agent通过jetty上传样本可用接口集群列表
          /agentstream/grpc    apm-agent通过grpc上传样本可用接口集群列表
          同步请求，直接查询每个路由在zookeeper、redis中存放的collector集群信息

    - apm-collector/apm-collector-agentstream

          分别提供grpc和jetty http两种形式的接口:
          jetty:12800
          /application/register
          /segments
          /instance/register
          /servicename/discovery

          grpc:11800
          ApplicationRegisterServiceHandler
          TraceSegmentServiceHandler
          InstanceDiscoveryServiceHandler
          ServiceNameDiscoveryServiceHandler
          JVMMetricsServiceHandler

	- apm-collector/apm-collector-agentjvm

     		收集客户端jvm参数信息放入DataCache批量插入到storage,如果DataCache已满就直接插入

	- apm-collector/apm-collector-cluster

			zookeeper节点
			/skywalking/ui/jetty
			/skywalking/agent_server/jetty
			/skywalking/agent_stream/grpc
			/skywalking/agent_stream/jetty
			/skywalking/collector_inside/grpc


     		ClusterModuleGroupDefine主要做的工作就是:
     		根据策略规则启动约定的集群组件，将实现DataMonitor的类注册监听，代码调用顺序			是:ClusterModuleGroupDefine.ClusterModuleInstaller.preInstall、install、afterInstall。
     		在cluster组启动后会调用ClusterModuleInstaller.afterInstall方法，继而调用DataMonitor.start方法，上报服务。
     		从代码逻辑上看，仅实现了zookeeper上报通知，下线时并没有删除zookeeper节点，但在agent代码里有对collector是否可用做了判断.
     		当前问题:
               1.下线未通知
               2.本机上线时有时拿不到zookeeper通知

	- apm-collector/apm-collector-stream

          启动了11800端口，并从zookeeper监听其他collector服务的上下线，并组成一个由本地worker和远程worker共同提供服务来消费样本
          当collector上下线时，生成一个grpc客户端，通知到本机所有的worker,组成一个worker集群


