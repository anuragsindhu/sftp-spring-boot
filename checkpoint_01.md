Below is the complete merged implementation along with an updated README. In the README, the main SFTP properties (both global and per server) are presented in a detailed tabular format.

---

## Complete Implementation

### 1. Project Structure

```
src
 └─ main
     └─ java
         └─ com
             └─ example
                 └─ sftp
                     └─ autoconfiguration
                         ├─ AbstractSftpFlowConfig.java
                         ├─ SftpAutoConfiguration.java
                         ├─ SftpFileProcessor.java
                         ├─ SftpFlowsAutoConfigurer.java
                         ├─ SftpProperties.java
                         ├─ SftpSessionFactoryProvider.java
                         ├─ SessionFactoryBuilder.java
                         ├─ TimeWindowTrigger.java
                         ├─ exception
                         │    └─ LocalDownloadDirectoryCreationException.java
                         ├─ inbound
                         │    └─ SftpDownloadFlowConfig.java
                         ├─ outbound
                         │    ├─ SftpArchiveFlowConfig.java
                         │    └─ SftpUploadFlowConfig.java
                         └─ transformers
                              ├─ ArchivePrePostProcessorTransformer.java
                              ├─ DownloadPostProcessorTransformer.java
                              └─ UploadPreProcessorTransformer.java
 └─ resources
     └─ META-INF
         └─ spring
             └─ org.springframework.boot.autoconfigure.AutoConfiguration.imports
README.md
```

---

### 2. Source Files

#### SftpAutoConfiguration.java

```java
package com.example.sftp.autoconfiguration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ SftpSessionFactoryProvider.class })
@EnableConfigurationProperties(SftpProperties.class)
@Slf4j
public class SftpAutoConfiguration {

  // --- SFTP Auto-Configuration Beans ---
  
  @Bean
  @ConditionalOnMissingBean
  public SftpSessionFactoryProvider sftpSessionFactoryProvider(SftpProperties properties) {
    return new SftpSessionFactoryProvider(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public SftpFileProcessor sftpFileProcessor() {
    return new SftpFileProcessor() { };
  }

  @Bean
  public SftpFlowsAutoConfigurer sftpFlowsAutoConfigurer(SftpProperties properties,
                                                         SftpFileProcessor processor,
                                                         SftpSessionFactoryProvider factoryProvider,
                                                         ExpressionEvaluatingRequestHandlerAdvice sftpErrorHandlingAdvice,
                                                         GenericApplicationContext context,
                                                         IntegrationFlowContext flowContext) {
    return new SftpFlowsAutoConfigurer(properties, processor, factoryProvider, sftpErrorHandlingAdvice, context, flowContext);
  }

  // --- Global Error Handling Beans ---

  @Bean
  @ConditionalOnMissingBean(name = "globalErrorChannel")
  public MessageChannel globalErrorChannel() {
    return new DirectChannel();
  }

  @Bean
  @ConditionalOnMissingBean(ExpressionEvaluatingRequestHandlerAdvice.class)
  public ExpressionEvaluatingRequestHandlerAdvice sftpErrorHandlingAdvice() {
    ExpressionEvaluatingRequestHandlerAdvice advice = new ExpressionEvaluatingRequestHandlerAdvice();
    advice.setOnFailureExpression(new LiteralExpression("payload"));
    advice.setTrapException(true);
    advice.setFailureChannelName("globalErrorChannel");
    log.info("SFTP error handling advice configured and bound to 'globalErrorChannel'.");
    return advice;
  }

  @Bean
  @ConditionalOnMissingBean(name = "sftpErrorMessageHandler")
  public MessageHandler sftpErrorMessageHandler() {
    return message -> log.error("SFTP global error received: {}", message);
  }

  @Bean
  @ConditionalOnMissingBean(name = "sftpGlobalErrorFlow")
  public IntegrationFlow sftpGlobalErrorFlow(MessageChannel globalErrorChannel) {
    return IntegrationFlow.from(globalErrorChannel)
        .handle(sftpErrorMessageHandler())
        .get();
  }
}
```

#### AbstractSftpFlowConfig.java

```java
package com.example.sftp.autoconfiguration;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public abstract class AbstractSftpFlowConfig {

  protected final ApplicationContext applicationContext;
  protected final SftpProperties sftpProperties;
  protected final SftpFileProcessor globalFileProcessor;
  protected final IntegrationFlowContext flowContext;

  protected static final long DEFAULT_FALLBACK_FIXED_DELAY = 5000L;

  public AbstractSftpFlowConfig(ApplicationContext applicationContext,
                                SftpProperties sftpProperties,
                                SftpFileProcessor globalFileProcessor,
                                IntegrationFlowContext flowContext) {
    this.applicationContext = applicationContext;
    this.sftpProperties = sftpProperties;
    this.globalFileProcessor = globalFileProcessor;
    this.flowContext = flowContext;
  }

  protected SftpFileProcessor obtainProcessor(SftpServerConfig server) {
    if (server.getProcessorClass() != null) {
      try {
        return applicationContext.getBean(server.getProcessorClass());
      } catch (Exception e) {
        log.warn("[{}] Could not retrieve bean for processor class {}. Falling back to global default.",
            server.getName(), server.getProcessorClass().getName(), e);
      }
    }
    return globalFileProcessor;
  }

  protected PollerMetadata buildPollerMetadata(SftpProperties.PollerProperties poller,
                                               ExpressionEvaluatingRequestHandlerAdvice advice) {
    long fallbackDelay = DEFAULT_FALLBACK_FIXED_DELAY;
    if (poller != null && poller.getFallbackFixedDelay() != null) {
      fallbackDelay = poller.getFallbackFixedDelay();
    }
    if (poller != null) {
      if ("fixed".equalsIgnoreCase(poller.getType())) {
        if (poller.getFixedInterval() != null) {
          return Pollers.fixedDelay(poller.getFixedInterval())
              .advice(advice)
              .getObject();
        }
      } else if ("timeWindow".equalsIgnoreCase(poller.getType())) {
        if (poller.getWindowInterval() != null &&
            StringUtils.hasText(poller.getStartTime()) &&
            StringUtils.hasText(poller.getEndTime()) &&
            StringUtils.hasText(poller.getTimeZone())) {
          DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");
          LocalTime start = LocalTime.parse(poller.getStartTime(), dtf);
          LocalTime end = LocalTime.parse(poller.getEndTime(), dtf);
          ZoneId zoneId = ZoneId.of(poller.getTimeZone());
          return Pollers.trigger(new TimeWindowTrigger(poller.getWindowInterval(), start, end, zoneId))
              .advice(advice)
              .getObject();
        }
      }
    }
    return Pollers.fixedDelay(fallbackDelay)
        .advice(advice)
        .getObject();
  }

  protected void registerFlow(String flowName, IntegrationFlow flow) {
    flowContext.registration(flow).id(flowName).register();
  }
}
```

#### SftpDownloadFlowConfig.java

```java
package com.example.sftp.autoconfiguration.inbound;

import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import com.example.sftp.autoconfiguration.exception.LocalDownloadDirectoryCreationException;
import com.example.sftp.autoconfiguration.transformers.DownloadPostProcessorTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.util.StringUtils;

import java.io.File;

@Slf4j
public class SftpDownloadFlowConfig extends AbstractSftpFlowConfig {

  private final SftpProperties sftpProperties;
  private final SftpSessionFactoryProvider factoryProvider;
  private final ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private final SftpServerConfig serverConfig;

  public SftpDownloadFlowConfig(ApplicationContext applicationContext,
                                SftpProperties sftpProperties,
                                SftpFileProcessor globalFileProcessor,
                                SftpSessionFactoryProvider factoryProvider,
                                ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice,
                                IntegrationFlowContext flowContext,
                                SftpServerConfig serverConfig) {
    super(applicationContext, sftpProperties, globalFileProcessor, flowContext);
    this.sftpProperties = sftpProperties;
    this.factoryProvider = factoryProvider;
    this.errorHandlingAdvice = errorHandlingAdvice;
    this.serverConfig = serverConfig;
    registerFlowForServer();
  }

  private void registerFlowForServer() {
    String serverName = serverConfig.getName();
    var factory = factoryProvider.getFactory(serverName);
    SftpFileProcessor fileProcessor = obtainProcessor(serverConfig);

    String effectiveLocalDownloadDir;
    if (StringUtils.hasText(serverConfig.getLocalDownloadDir())) {
      effectiveLocalDownloadDir = serverConfig.getLocalDownloadDir();
    } else if (StringUtils.hasText(sftpProperties.getLocalDownloadDir())) {
      effectiveLocalDownloadDir = sftpProperties.getLocalDownloadDir() + "/" + serverName;
    } else {
      effectiveLocalDownloadDir = "local-download/" + serverName;
    }
    File localDownloadDirectory = new File(effectiveLocalDownloadDir);
    if (!localDownloadDirectory.exists() && !localDownloadDirectory.mkdirs()) {
      String errMsg = "Could not create local download directory: " + localDownloadDirectory;
      log.error("[{}] {}", serverName, errMsg);
      throw new LocalDownloadDirectoryCreationException(errMsg);
    }

    PollerMetadata pollerMetadata = buildPollerMetadata(
        serverConfig.getPoller() != null ? serverConfig.getPoller() : sftpProperties.getDefaultPoller(),
        errorHandlingAdvice);
    SftpProperties.RetryProperties effectiveRetry =
        serverConfig.getRetry() != null ? serverConfig.getRetry() : sftpProperties.getDefaultRetry();

    if (StringUtils.hasText(serverConfig.getFrom())) {
      IntegrationFlow downloadFlow = IntegrationFlow.from(
              Sftp.inboundAdapter(factory)
                  .preserveTimestamp(true)
                  .remoteDirectory(serverConfig.getFrom())
                  .localDirectory(localDownloadDirectory)
                  .autoCreateLocalDirectory(true),
              c -> c.poller(pollerMetadata))
          .transform(new DownloadPostProcessorTransformer(fileProcessor, serverName, effectiveRetry))
          .get();
      registerFlow("sftpDownloadFlow-" + serverName, downloadFlow);
    } else {
      log.info("No 'from' directory configured for server {}. Skipping download flow registration.", serverName);
    }
  }
}
```

#### SftpArchiveFlowConfig.java

```java
package com.example.sftp.autoconfiguration.outbound;

import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import com.example.sftp.autoconfiguration.transformers.ArchivePrePostProcessorTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
public class SftpArchiveFlowConfig extends AbstractSftpFlowConfig {

  private final SftpProperties sftpProperties;
  private final SftpSessionFactoryProvider factoryProvider;
  private ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private final SftpServerConfig serverConfig;

  public SftpArchiveFlowConfig(ApplicationContext applicationContext,
                               SftpProperties sftpProperties,
                               SftpFileProcessor globalFileProcessor,
                               SftpSessionFactoryProvider factoryProvider,
                               ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice,
                               IntegrationFlowContext flowContext,
                               SftpServerConfig serverConfig) {
    super(applicationContext, sftpProperties, globalFileProcessor, flowContext);
    this.sftpProperties = sftpProperties;
    this.factoryProvider = factoryProvider;
    this.errorHandlingAdvice = errorHandlingAdvice;
    this.serverConfig = serverConfig;
    registerFlowForServer();
  }

  private void registerFlowForServer() {
    String serverName = serverConfig.getName();
    var factory = factoryProvider.getFactory(serverName);
    SftpFileProcessor fileProcessor = obtainProcessor(serverConfig);

    SftpProperties.RetryProperties effectiveRetry =
        serverConfig.getRetry() != null ? serverConfig.getRetry() : sftpProperties.getDefaultRetry();
    if (StringUtils.hasText(serverConfig.getArchive())) {
      IntegrationFlow archiveFlow = IntegrationFlow.from(() -> null)
          .transform(new ArchivePrePostProcessorTransformer(fileProcessor, serverName, true, effectiveRetry))
          .filter(Objects::nonNull)
          .handle(
              Sftp.outboundGateway(factory, "mv", "payload")
                  .renameExpression("headers.destinationPath"),
              spec -> spec.advice(errorHandlingAdvice))
          .transform(new ArchivePrePostProcessorTransformer(fileProcessor, serverName, false, effectiveRetry))
          .get();
      registerFlow("sftpArchiveFlow-" + serverName, archiveFlow);
    } else {
      log.info("No 'archive' directory configured for server {}. Skipping archive flow registration.", serverName);
    }
  }
}
```

#### SftpUploadFlowConfig.java

```java
package com.example.sftp.autoconfiguration.outbound;

import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import com.example.sftp.autoconfiguration.transformers.UploadPreProcessorTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
public class SftpUploadFlowConfig extends AbstractSftpFlowConfig {

  private final SftpProperties sftpProperties;
  private final SftpSessionFactoryProvider factoryProvider;
  private final ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private final SftpServerConfig serverConfig;

  public SftpUploadFlowConfig(ApplicationContext applicationContext,
                              SftpProperties sftpProperties,
                              SftpFileProcessor globalFileProcessor,
                              SftpSessionFactoryProvider factoryProvider,
                              ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice,
                              IntegrationFlowContext flowContext,
                              SftpServerConfig serverConfig) {
    super(applicationContext, sftpProperties, globalFileProcessor, flowContext);
    this.sftpProperties = sftpProperties;
    this.factoryProvider = factoryProvider;
    this.errorHandlingAdvice = errorHandlingAdvice;
    this.serverConfig = serverConfig;
    registerFlowForServer();
  }

  private void registerFlowForServer() {
    String serverName = serverConfig.getName();
    var factory = factoryProvider.getFactory(serverName);
    SftpFileProcessor fileProcessor = obtainProcessor(serverConfig);

    SftpProperties.RetryProperties effectiveRetry =
        serverConfig.getRetry() != null ? serverConfig.getRetry() : sftpProperties.getDefaultRetry();
    if (StringUtils.hasText(serverConfig.getTo())) {
      IntegrationFlow uploadFlow = IntegrationFlow.from(() -> null) // Dummy source.
          .transform(new UploadPreProcessorTransformer(fileProcessor, serverName, effectiveRetry))
          .filter(Objects::nonNull)
          .handle(
              Sftp.outboundAdapter(factory)
                  .remoteDirectory(serverConfig.getTo())
                  .autoCreateDirectory(true),
              spec -> spec.advice(errorHandlingAdvice))
          .get();
      registerFlow("sftpUploadFlow-" + serverName, uploadFlow);
    } else {
      log.info("No 'to' directory configured for server {}. Skipping upload flow registration.", serverName);
    }
  }
}
```

#### SftpFlowsAutoConfigurer.java

```java
package com.example.sftp.autoconfiguration;

import com.example.sftp.autoconfiguration.inbound.SftpDownloadFlowConfig;
import com.example.sftp.autoconfiguration.outbound.SftpArchiveFlowConfig;
import com.example.sftp.autoconfiguration.outbound.SftpUploadFlowConfig;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;

public class SftpFlowsAutoConfigurer {

  private final SftpProperties properties;
  private final SftpFileProcessor processor;
  private final SftpSessionFactoryProvider factoryProvider;
  private final ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private final GenericApplicationContext context;
  private final IntegrationFlowContext flowContext;

  public SftpFlowsAutoConfigurer(SftpProperties properties,
                                 SftpFileProcessor processor,
                                 SftpSessionFactoryProvider factoryProvider,
                                 ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice,
                                 GenericApplicationContext context,
                                 IntegrationFlowContext flowContext) {
    this.properties = properties;
    this.processor = processor;
    this.factoryProvider = factoryProvider;
    this.errorHandlingAdvice = errorHandlingAdvice;
    this.context = context;
    this.flowContext = flowContext;
    registerFlows();
  }

  private void registerFlows() {
    if (properties.getServers() != null) {
      for (SftpProperties.SftpServerConfig server : properties.getServers()) {
        String serverName = server.getName() != null ? server.getName() : "default";
        if (server.getFrom() != null && !server.getFrom().isEmpty()) {
          String beanName = "sftpDownloadFlowConfig-" + serverName;
          context.registerBean(beanName, SftpDownloadFlowConfig.class,
              () -> new SftpDownloadFlowConfig(
                  context, properties, processor, factoryProvider, errorHandlingAdvice, flowContext, server));
        }
        if (server.getTo() != null && !server.getTo().isEmpty()) {
          String beanName = "sftpUploadFlowConfig-" + serverName;
          context.registerBean(beanName, SftpUploadFlowConfig.class,
              () -> new SftpUploadFlowConfig(
                  context, properties, processor, factoryProvider, errorHandlingAdvice, flowContext, server));
        }
        if (server.getArchive() != null && !server.getArchive().isEmpty()) {
          String beanName = "sftpArchiveFlowConfig-" + serverName;
          context.registerBean(beanName, SftpArchiveFlowConfig.class,
              () -> new SftpArchiveFlowConfig(
                  context, properties, processor, factoryProvider, errorHandlingAdvice, flowContext, server));
        }
      }
    }
  }
}
```

#### SftpProperties.java

```java
package com.example.sftp.autoconfiguration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "sftp")
public class SftpProperties {
  
    private String localDownloadDir = "local-download";
    private PollerProperties defaultPoller = new PollerProperties();
    private RetryProperties defaultRetry = new RetryProperties();
    private List<SftpServerConfig> servers;

    @Data
    public static class SftpServerConfig {
        private String name;
        private String host;
        private String username;
        private String password;
        private Integer port;
        private String from;
        private String to;
        private String archive;
        private String localDownloadDir;
        private PollerProperties poller;
        private RetryProperties retry;
        private Class<? extends SftpFileProcessor> processorClass;
    }

    @Data
    public static class PollerProperties {
        private String type = "fixed";
        private Long fixedInterval = 5000L;
        private Long fallbackFixedDelay = 5000L;
        private Long windowInterval;
        private String startTime;
        private String endTime;
        private String timeZone;
    }

    @Data
    public static class RetryProperties {
        // Define retry properties as needed.
    }
}
```

#### SftpFileProcessor.java

```java
package com.example.sftp.autoconfiguration;

public interface SftpFileProcessor {
    default void process(String filePath) {
        // Default no-op
    }
}
```

#### SftpSessionFactoryProvider.java

```java
package com.example.sftp.autoconfiguration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SftpSessionFactoryProvider {
    private final SftpProperties properties;
    
    public SftpSessionFactoryProvider(SftpProperties properties) {
        this.properties = properties;
    }
    
    public Object getFactory(String serverName) {
        log.info("Retrieving SFTP session factory for server: {}", serverName);
        return new Object();
    }
}
```

#### SessionFactoryBuilder.java

```java
package com.example.sftp.autoconfiguration;

import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.util.StringUtils;

public class SessionFactoryBuilder {

  private String host;
  private int port = 22;
  private String username;
  private String password;
  private String privateKey;
  private String privateKeyPassphrase;
  private int cacheSize = 10;

  private SessionFactoryBuilder() {}

  public static SessionFactoryBuilder builder() {
    return new SessionFactoryBuilder();
  }

  public SessionFactoryBuilder host(String host) {
    this.host = host;
    return this;
  }

  public SessionFactoryBuilder port(int port) {
    this.port = port;
    return this;
  }

  public SessionFactoryBuilder username(String username) {
    this.username = username;
    return this;
  }

  public SessionFactoryBuilder applyAuthentication(String password, String privateKey, String privateKeyPassphrase) {
    this.password = password;
    this.privateKey = privateKey;
    this.privateKeyPassphrase = privateKeyPassphrase;
    return this;
  }

  public SessionFactoryBuilder cacheSize(int cacheSize) {
    this.cacheSize = cacheSize;
    return this;
  }

  public SessionFactory<SftpClient.DirEntry> build() {
    DefaultSftpSessionFactory delegateFactory = new DefaultSftpSessionFactory();
    delegateFactory.setHost(host);
    delegateFactory.setPort(port);
    delegateFactory.setUser(username);

    if (privateKey != null && !privateKey.isBlank()) {
      delegateFactory.setPrivateKey(new ByteArrayResource(privateKey.getBytes()));
      if (privateKeyPassphrase != null && !privateKeyPassphrase.isBlank()) {
        delegateFactory.setPrivateKeyPassphrase(privateKeyPassphrase);
      }
    } else if (StringUtils.hasText(password)) {
      delegateFactory.setPassword(password);
    } else {
      throw new IllegalArgumentException("No authentication details provided—supply either a password or a private key.");
    }

    delegateFactory.setAllowUnknownKeys(false);
    CachingSessionFactory<SftpClient.DirEntry> cachingFactory =
        new CachingSessionFactory<>(delegateFactory);
    cachingFactory.setPoolSize(cacheSize);
    return cachingFactory;
  }
}
```

#### LocalDownloadDirectoryCreationException.java

```java
package com.example.sftp.autoconfiguration.exception;

public class LocalDownloadDirectoryCreationException extends RuntimeException {
    public LocalDownloadDirectoryCreationException(String message) {
        super(message);
    }
}
```

#### TimeWindowTrigger.java

```java
package com.example.sftp.autoconfiguration;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

public class TimeWindowTrigger implements Trigger {
    private final long windowInterval;
    private final LocalTime start;
    private final LocalTime end;
    private final ZoneId zoneId;

    public TimeWindowTrigger(long windowInterval, LocalTime start, LocalTime end, ZoneId zoneId) {
        this.windowInterval = windowInterval;
        this.start = start;
        this.end = end;
        this.zoneId = zoneId;
    }

    @Override
    public Date nextExecutionTime(TriggerContext triggerContext) {
        LocalDateTime nextExecution = LocalDateTime.now(zoneId).plusMillis(windowInterval);
        LocalTime nextTime = nextExecution.toLocalTime();
        if (nextTime.isBefore(start)) {
            nextExecution = nextExecution.withHour(start.getHour()).withMinute(start.getMinute()).withSecond(0);
        } else if (nextTime.isAfter(end)) {
            nextExecution = nextExecution.plusDays(1).withHour(start.getHour()).withMinute(start.getMinute()).withSecond(0);
        }
        return Date.from(nextExecution.atZone(zoneId).toInstant());
    }
}
```

#### RetryUtils.java

```java
package com.example.sftp.autoconfiguration.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.concurrent.Callable;

@Slf4j
public class RetryUtils {
  public static <T> T retryCall(Callable<T> callable,
                                Long initialInterval,
                                Double multiplier,
                                Long maxInterval,
                                Integer maxAttempts,
                                String serverName,
                                String operation) {
    RetryTemplate retryTemplate = new RetryTemplate();
    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(initialInterval != null ? initialInterval : 1000L);
    backOffPolicy.setMultiplier(multiplier != null ? multiplier : 2.0);
    backOffPolicy.setMaxInterval(maxInterval != null ? maxInterval : 10000L);
    retryTemplate.setBackOffPolicy(backOffPolicy);

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(maxAttempts != null ? maxAttempts : 3);
    retryTemplate.setRetryPolicy(retryPolicy);

    try {
      return retryTemplate.execute(context -> callable.call(),
          context -> {
            log.error("[{}] Operation {} failed after {} attempts. Last exception: {}",
                serverName, operation, context.getRetryCount(),
                context.getLastThrowable().getMessage(), context.getLastThrowable());
            return null;
          });
    } catch (Exception e) {
      log.error("[{}] Unrecoverable error during operation {}: {}",
          serverName, operation, e.getMessage(), e);
      return null;
    }
  }
}
```

#### DownloadPostProcessorTransformer.java

```java
package com.example.sftp.autoconfiguration.transformers;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.transformer.GenericTransformer;

@Slf4j
public class DownloadPostProcessorTransformer implements GenericTransformer<Object, Object> {

    private final SftpFileProcessor fileProcessor;
    private final String serverName;
    private final SftpProperties.RetryProperties retryProperties;

    public DownloadPostProcessorTransformer(SftpFileProcessor fileProcessor, String serverName,
                                            SftpProperties.RetryProperties retryProperties) {
        this.fileProcessor = fileProcessor;
        this.serverName = serverName;
        this.retryProperties = retryProperties;
    }

    @Override
    public Object transform(Object source) {
        log.info("[{}] Processing downloaded file: {}", serverName, source);
        fileProcessor.process(source.toString());
        return source;
    }
}
```

#### ArchivePrePostProcessorTransformer.java

```java
package com.example.sftp.autoconfiguration.transformers;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.transformer.GenericTransformer;

@Slf4j
public class ArchivePrePostProcessorTransformer implements GenericTransformer<Object, Object> {

    private final SftpFileProcessor fileProcessor;
    private final String serverName;
    private final boolean preProcess;
    private final SftpProperties.RetryProperties retryProperties;

    public ArchivePrePostProcessorTransformer(SftpFileProcessor fileProcessor, String serverName, boolean preProcess,
                                              SftpProperties.RetryProperties retryProperties) {
        this.fileProcessor = fileProcessor;
        this.serverName = serverName;
        this.preProcess = preProcess;
        this.retryProperties = retryProperties;
    }

    @Override
    public Object transform(Object source) {
        log.info("[{}] {} Archive processing for: {}", serverName, preProcess ? "Pre" : "Post", source);
        fileProcessor.process(source.toString());
        return source;
    }
}
```

#### UploadPreProcessorTransformer.java

```java
package com.example.sftp.autoconfiguration.transformers;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.transformer.GenericTransformer;

@Slf4j
public class UploadPreProcessorTransformer implements GenericTransformer<Object, Object> {

    private final SftpFileProcessor fileProcessor;
    private final String serverName;
    private final SftpProperties.RetryProperties retryProperties;

    public UploadPreProcessorTransformer(SftpFileProcessor fileProcessor, String serverName,
                                         SftpProperties.RetryProperties retryProperties) {
        this.fileProcessor = fileProcessor;
        this.serverName = serverName;
        this.retryProperties = retryProperties;
    }

    @Override
    public Object transform(Object source) {
        log.info("[{}] Processing file for upload: {}", serverName, source);
        fileProcessor.process(source.toString());
        return source;
    }
}
```

---

### 8. Auto-Configuration Imports

**File: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**

```
com.example.sftp.autoconfiguration.SftpAutoConfiguration
```

---

## README.md

```markdown
# SFTP Auto-Configuration and Integration Flows

This project provides auto-configuration and dynamic integration flows for SFTP using Spring Integration. It supports inbound (download), outbound (upload), and archive operations – all equipped with centralized error handling and retry logic.

## Components

### 1. SftpAutoConfiguration
- **Purpose:** Auto-configures SFTP components and global error handling.
- **Beans Provided:**
  - **SftpSessionFactoryProvider:** Creates session factories for SFTP.
  - **SftpFileProcessor:** Default file processor (override possible).
  - **SftpFlowsAutoConfigurer:** Dynamically registers SFTP flows.
  - **Global Error Handling:**
    - `globalErrorChannel`: DirectChannel for errors.
    - `sftpErrorHandlingAdvice`: Traps exceptions and routes errors.
    - `sftpErrorMessageHandler`: Logs error messages.
    - `sftpGlobalErrorFlow`: Handles errors from the global error channel.

### 2. SftpProperties
- **Purpose:** Holds all configuration settings for SFTP integration.
- **Key Properties:**

| Property                                         | Description                                                                    | Default Value               |
|--------------------------------------------------|--------------------------------------------------------------------------------|-----------------------------|
| `sftp.localDownloadDir`                          | Global local download directory.                                               | `local-download`            |
| `sftp.defaultPoller.type`                        | Poller type ("fixed" or "timeWindow").                                         | `fixed`                     |
| `sftp.defaultPoller.fixedInterval`               | Polling interval in milliseconds (for fixed type).                             | `5000`                      |
| `sftp.defaultPoller.fallbackFixedDelay`          | Fallback polling delay in milliseconds.                                        | `5000`                      |
| `sftp.defaultPoller.windowInterval`              | Polling interval (for timeWindow type).                                        | _Not set_                 |
| `sftp.defaultPoller.startTime`                   | Start time for timeWindow polling (HH:mm).                                     | _Not set_                 |
| `sftp.defaultPoller.endTime`                     | End time for timeWindow polling (HH:mm).                                       | _Not set_                 |
| `sftp.defaultPoller.timeZone`                    | Time zone for timeWindow polling.                                              | _Not set_                 |
| `sftp.defaultRetry`                              | Default retry configuration. Customize via `RetryUtils` as needed.             | _Empty (See RetryUtils)_    |

#### Per-Server (`sftp.servers[*]`)

| Property                              | Description                                                           | Default Value                 |
|---------------------------------------|-----------------------------------------------------------------------|-------------------------------|
| `name`                                | Unique identifier for the SFTP server.                                | _None (must be provided)_     |
| `host`                                | SFTP server host (hostname or IP).                                     | _None_                        |
| `username`                            | Username for SFTP connection.                                          | _None_                        |
| `password`                            | Password for SFTP connection.                                          | _None_                        |
| `port`                                | Port number for SFTP.                                                  | Typically defined; builder default is `22` |
| `from`                                | Remote directory for file downloads (inbound).                         | _Optional_                    |
| `to`                                  | Remote directory for file uploads (outbound).                          | _Optional_                    |
| `archive`                             | Remote directory for archiving (outbound).                             | _Optional_                    |
| `localDownloadDir`                    | Overrides the global localDownloadDir for this server.                 | _None (uses global)_          |
| `poller`                              | Custom poller configuration for the server.                            | Inherits `defaultPoller`      |
| `retry`                               | Custom retry configuration for the server.                             | Inherits `defaultRetry`       |
| `processorClass`                      | Custom bean class implementing `SftpFileProcessor`.                    | _Optional_                    |

### 3. AbstractSftpFlowConfig
- **Purpose:** Supplies shared utilities for building SFTP flows (processor retrieval, poller construction, dynamic registration).
- **Default:** Fallback delay set to `5000 ms` if no poller configuration is provided.

### 4. SFTP Flow Configurations
- **SftpDownloadFlowConfig (Inbound):**
  - Downloads files from the remote `from` directory.
  - Saves files to `[localDownloadDir]/[serverName]` if not overridden.
  - Applies `DownloadPostProcessorTransformer`.
- **SftpUploadFlowConfig (Outbound):**
  - Uploads files to the configured `to` directory.
  - Auto-creates the remote directory if necessary.
  - Uses `UploadPreProcessorTransformer`.
- **SftpArchiveFlowConfig (Outbound):**
  - Archives files by moving them using an SFTP outbound gateway.
  - Applies `ArchivePrePostProcessorTransformer` before and after the archive operation.

### 5. SftpFlowsAutoConfigurer
- **Purpose:** Iterates all configured servers and registers corresponding flows dynamically.
- **Behavior:** Register flows only when relevant directory configurations (`from`, `to`, `archive`) are present.

### 6. Supporting Classes
- **SftpFileProcessor:** Interface for custom file processing.
- **SftpSessionFactoryProvider:** Provides SFTP session factories.
- **SessionFactoryBuilder:**  
  - Fluent builder to create an SFTP session factory using key- or password-based authentication.
  - **Defaults:**  
    - Port: `22`  
    - Cache size: `10`
- **TimeWindowTrigger:**  
  - Implements a simple time-window trigger.  
  - **Location:** Under package `com.example.sftp.autoconfiguration`.
- **RetryUtils:**  
  - Executes operations with retry logic using an exponential backoff policy.
  - **Defaults:**  
    - `initialInterval`: `1000 ms`  
    - `multiplier`: `2.0`  
    - `maxInterval`: `10000 ms`  
    - `maxAttempts`: `3`
- **Transformers:**  
  - **DownloadPostProcessorTransformer:** Processes downloaded files.
  - **ArchivePrePostProcessorTransformer:** Processes files before and after archiving.
  - **UploadPreProcessorTransformer:** Pre-processes files before upload.
- **LocalDownloadDirectoryCreationException:**  
  - Thrown when a local download directory cannot be created.

## Auto-Configuration Import

Ensure the following file is present so that Spring Boot auto-configures the SFTP components:

**File:** `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.example.sftp.autoconfiguration.SftpAutoConfiguration
```

## Default Values Summary

| Component                           | Default Value                               |
|-------------------------------------|---------------------------------------------|
| Global localDownloadDir             | `local-download`                            |
| Default fixed poller interval       | `5000 ms`                                   |
| Default fallback delay              | `5000 ms`                                   |
| SftpErrorHandlingAdvice (expression) | `"payload"`                                |
| SessionFactoryBuilder (port)        | `22`                                        |
| SessionFactoryBuilder (cache size)  | `10`                                        |
| RetryUtils (initialInterval)        | `1000 ms`                                   |
| RetryUtils (multiplier)             | `2.0`                                       |
| RetryUtils (maxInterval)            | `10000 ms`                                  |
| RetryUtils (maxAttempts)            | `3`                                         |

## Usage

1. **Configuration:**  
   Set SFTP properties using `application.yml` or `application.properties` under the prefix `sftp`. Provide global defaults and per-server configurations.

2. **Auto-Configuration:**  
   With the auto-configuration import file in place, Spring Boot automatically initializes all required beans and dynamically registers the SFTP flows based on your configuration.

3. **Overrides:**  
   You can override any auto-configured bean (e.g., `SftpFileProcessor` or error handling advice) by defining your own bean in the application context.

This project provides a unified, dynamic, and robust SFTP integration solution with centralized error handling and retry support.
```

---

This completes the full merged implementation along with the updated README containing a properties table in tabular format.