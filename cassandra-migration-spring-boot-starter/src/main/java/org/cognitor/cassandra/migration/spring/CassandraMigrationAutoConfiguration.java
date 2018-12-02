package org.cognitor.cassandra.migration.spring;

import com.datastax.driver.core.Cluster;
import org.cognitor.cassandra.migration.MigrationRepository;
import org.cognitor.cassandra.migration.collector.FailOnDuplicatesCollector;
import org.cognitor.cassandra.migration.collector.IgnoreDuplicatesCollector;
import org.cognitor.cassandra.migration.keyspace.KeyspaceDefinition;
import org.cognitor.cassandra.migration.scanner.ScannerRegistry;
import org.cognitor.cassandra.migration.spring.scanner.SpringBootLocationScanner;
import org.cognitor.cassandra.migration.tasks.TaskChain;
import org.cognitor.cassandra.migration.tasks.TaskChainBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Patrick Kranz
 */
@Configuration
@EnableConfigurationProperties(CassandraMigrationConfigurationProperties.class)
@AutoConfigureAfter(CassandraAutoConfiguration.class)
@ConditionalOnClass(Cluster.class)
public class CassandraMigrationAutoConfiguration {
    private final CassandraMigrationConfigurationProperties properties;

    @Autowired
    public CassandraMigrationAutoConfiguration(CassandraMigrationConfigurationProperties properties) {
        this.properties = properties;
    }


    @Bean
    @ConditionalOnBean(Cluster.class)
    @ConditionalOnMissingBean(TaskChain.class)
    public TaskChain migrationProcess(Cluster cluster) {
        if (!properties.hasKeyspaceName()) {
            throw new IllegalStateException("Please specify ['cassandra.migration.keyspace-name'] in" +
                    " order to migrate your database");
        }
        KeyspaceDefinition keyspaceDefinition = new KeyspaceDefinition(properties.getKeyspaceName())
                .with(properties.getReplicationStrategy());
        return new TaskChainBuilder(cluster,
                    new org.cognitor.cassandra.migration.Configuration(keyspaceDefinition)
                .setChecksumValidation(properties.isChecksumValidation())
                .setValidateOnly(properties.isChecksumValidationOnly())
                .setRecalculateChecksum(properties.isRecalculateChecksum())
                .setRecalculateChecksumOnly(properties.isRecalculateChecksumOnly())
                .setConsistencyLevel(properties.getConsistencyLevel())
                .setCreateKeyspace(properties.isCreateKeyspace()),
                createRepository())
                .buildTaskChain();
    }

    private MigrationRepository createRepository() {
        ScannerRegistry registry = new ScannerRegistry();
        registry.register(ScannerRegistry.JAR_SCHEME, new SpringBootLocationScanner());
        if (properties.getStrategy() == ScriptCollectorStrategy.FAIL_ON_DUPLICATES) {
            return new MigrationRepository(properties.getScriptLocation(), new FailOnDuplicatesCollector(), registry);
        }
        return new MigrationRepository(properties.getScriptLocation(), new IgnoreDuplicatesCollector(), registry);
    }
}
