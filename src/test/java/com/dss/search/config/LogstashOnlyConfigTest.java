package com.dss.search.config;

import com.dss.DssApplication;
import com.dss.common.config.RabbitCommonConfig;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.mq.DocType;
import com.dss.common.mq.SearchSyncMessage;
import com.dss.common.mq.SearchSyncPublisher;
import com.dss.search.job.SearchIndexRebuildRunner;
import com.dss.search.mq.SearchSyncConsumer;
import com.dss.search.service.impl.SearchServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

class LogstashOnlyConfigTest {
    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void componentScanStartsWithoutRabbitTemplateAndPublisherDoesNothing() {
        try (var context = scannedContext(false)) {
            context.refresh();
            assertThat(context.getBeansOfType(RabbitTemplate.class)).isEmpty();
            var publisher = context.getBean(SearchSyncPublisher.class);
            publisher.publishShopChanged(1L);
            publisher.publishProductChanged(2L);
            TransactionSynchronizationManager.initSynchronization();
            publisher.publishShopChanged(1L);
            publisher.publishProductChanged(2L);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }
    }

    @Test
    void evenAnExistingRabbitTemplateDoesNotEnablePublishingOrTopology() {
        try (var context = scannedContext(true)) {
            context.refresh();
            RabbitTemplate rabbit = context.getBean(RabbitTemplate.class);
            // Spring 初始化会调用 Aware/afterPropertiesSet；只检查业务通知之后的交互。
            clearInvocations(rabbit);
            var publisher = context.getBean(SearchSyncPublisher.class);
            publisher.publishShopChanged(1L);
            publisher.publishProductChanged(2L);
            assertThat(context.getBeansOfType(SearchSyncConsumer.class)).isEmpty();
            assertThat(context.getBeansOfType(SearchMqConfig.class)).isEmpty();
            assertThat(context.getBeansOfType(RabbitCommonConfig.class)).hasSize(1);
            verifyNoInteractions(rabbit);
        }
    }

    @Test
    void applicationKeepsRabbitAutoConfigurationForOtherFeatures() {
        assertThat(DssApplication.class.getAnnotation(SpringBootApplication.class).exclude())
                .doesNotContain(RabbitAutoConfiguration.class);
    }

    @Test
    void javaSyncAndRebuildAreExplicitlyRejectedWithoutAnyEsOperations() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        SearchServiceImpl service = new SearchServiceImpl(operations, mock(FileUrlResolver.class));
        assertThatThrownBy(() -> service.sync(new SearchSyncMessage(DocType.PRODUCT, "1")))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Logstash");
        assertThatThrownBy(service::rebuildAll)
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Logstash");
        verifyNoInteractions(operations);
    }

    @Test
    void accidentalStartupRebuildFailsInsteadOfDeletingIndices() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        SearchServiceImpl service = new SearchServiceImpl(operations, mock(FileUrlResolver.class));
        var runner = new SearchIndexRebuildRunner(service);
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Logstash");
        verifyNoInteractions(operations);
    }

    private AnnotationConfigApplicationContext scannedContext(boolean withRabbit) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        if (withRabbit) {
            context.registerBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class));
        }
        Set<String> candidates = Set.of(SearchSyncPublisher.class.getName(), SearchSyncConsumer.class.getName(),
                SearchMqConfig.class.getName(), RabbitCommonConfig.class.getName());
        var scanner = new ClassPathBeanDefinitionScanner(context);
        scanner.addExcludeFilter((reader, factory) -> !candidates.contains(reader.getClassMetadata().getClassName()));
        scanner.scan("com.dss");
        return context;
    }
}
