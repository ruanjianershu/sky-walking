package org.skywalking.apm.collector.storage.elasticsearch;

import java.util.List;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.storage.StorageInstaller;
import org.skywalking.apm.collector.storage.StorageModuleDefine;
import org.skywalking.apm.collector.storage.StorageModuleGroupDefine;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAODefineLoader;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchStorageInstaller;

/**
 * @author pengys5
 */
public class StorageElasticSearchModuleDefine extends StorageModuleDefine {

    public static final String MODULE_NAME = "elasticsearch";

    @Override protected String group() {
        return StorageModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new StorageElasticSearchConfigParser();
    }

    @Override protected Client createClient() {
        return new ElasticSearchClient(StorageElasticSearchConfig.CLUSTER_NAME, StorageElasticSearchConfig.CLUSTER_TRANSPORT_SNIFFER, StorageElasticSearchConfig.CLUSTER_NODES);
    }

    @Override public StorageInstaller storageInstaller() {
        return new ElasticSearchStorageInstaller();
    }

    @Override public void injectClientIntoDAO(Client client) throws DefineException {
        /**
         * 各个模块涉及到对ElasticSearch的读写对象的实例化
         * apm-collector/apm-collector-agentjvm/src/main/resources/META-INF/defines/es_dao.define
         * apm-collector/apm-collector-agentregister/src/main/resources/META-INF/defines/es_dao.define
         * apm-collector/apm-collector-agentstream/src/main/resources/META-INF/defines/es_dao.define
         * apm-collector/apm-collector-storage/src/main/resources/META-INF/defines/es_dao.define
         * apm-collector/apm-collector-ui/src/main/resources/META-INF/defines/es_dao.define
         */
        EsDAODefineLoader loader = new EsDAODefineLoader();
        List<EsDAO> esDAOs = loader.load();
        esDAOs.forEach(esDAO -> {
            esDAO.setClient((ElasticSearchClient)client);
            //获得该DAO的接口名
            String interFaceName = esDAO.getClass().getInterfaces()[0].getName();
            //将DAO实例和接口名形成映射关系，在需要的时候通过接口名获得实例对象。DAOContainer相当于控制反转容器
            DAOContainer.INSTANCE.put(interFaceName, esDAO);
        });
    }
}
