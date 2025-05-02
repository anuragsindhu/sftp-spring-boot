Below is an example of a comprehensive prompt you can use to generate the exact, final implementation that we worked through. This prompt instructs the AI to produce a full, modular Spring Boot starter library for SFTP integration. It includes auto-configuration classes, separate inbound/outbound flows, dedicated transformer and retry utility classes, all using Lombok, Spring Integration 6.4.4, and Spring Boot’s auto-configuration mechanism. It also instructs to create the necessary auto-configuration metadata file.

---

**Prompt:**

```
You are to generate the complete implementation for a Spring Boot starter library for SFTP integration that conforms exactly to the following requirements and uses the exact provided code structures.

The project must use Spring Boot, Spring Integration 6.4.4, Lombok, and Spring Retry. It must be modularized and follow best practices by:
  • Extracting common flow configuration methods into an abstract base class.
  • Splitting inbound and outbound flows into separate configuration classes.
  • Separating record transformers into their own classes under a separate package.
  • Extracting retry logic into a dedicated utility class.

The project must have the following package structure and files:

1. **Auto-Configuration Metadata File:**
   - File: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   - Contents:
     ```
     com.example.sftp.autoconfiguration.GlobalErrorHandlingConfig
     com.example.sftp.autoconfiguration.inbound.SftpDownloadFlowConfig
     com.example.sftp.autoconfiguration.outbound.SftpUploadFlowConfig
     com.example.sftp.autoconfiguration.outbound.SftpArchiveFlowConfig
     ```

2. **GlobalErrorHandlingConfig.java** in package `com.example.sftp.autoconfiguration`
   - Use the following exact implementation:
     ```java
     package com.example.sftp.autoconfiguration;

     import lombok.extern.slf4j.Slf4j;
     import org.springframework.context.annotation.Bean;
     import org.springframework.context.annotation.Configuration;
     import org.springframework.integration.channel.PublishSubscribeChannel;
     import org.springframework.integration.dsl.IntegrationFlow;
     import org.springframework.messaging.MessageChannel;
     import org.springframework.messaging.MessageHandler;

     /**
      * Configures the global error channel and error handling flow.
      * All exceptions from SFTP flows are routed to the global error channel.
      */
     @Slf4j
     @Configuration
     public class GlobalErrorHandlingConfig {

       /**
        * Defines the global error channel as a PublishSubscribeChannel.
        *
        * @return the global error MessageChannel.
        */
       @Bean
       public MessageChannel errorChannel() {
         return new PublishSubscribeChannel();
       }

       /**
        * A simple global error message handler that logs error details.
        *
        * @return the error MessageHandler.
        */
       @Bean
       public MessageHandler errorMessageHandler() {
         return message -> {
           // Assume the payload is a Throwable.
           Throwable error = (Throwable) message.getPayload();
           log.error("Global Error: {}. Cause: {}", error.getMessage(), error.getCause(), error);
         };
       }

       /**
        * Defines a global error-handling flow that listens on the error channel and handles error messages.
        *
        * @return the global error handling IntegrationFlow.
        */
       @Bean
       public IntegrationFlow globalErrorFlow() {
         return IntegrationFlow.from("errorChannel")
             .handle(errorMessageHandler())
             .get();
       }
     }
     ```

3. **AbstractSftpFlowConfig.java** in package `com.example.sftp.autoconfiguration`
   - This abstract class provides common methods such as obtaining a file processor, building poller metadata with error-handling advice, and creating error-handling advice.
   - Example implementation:
     ```java
     package com.example.sftp.autoconfiguration;

     import com.example.sftp.autoconfiguration.SftpProperties;
     import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
     import lombok.extern.slf4j.Slf4j;
     import org.springframework.context.ApplicationContext;
     import org.springframework.integration.dsl.Pollers;
     import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
     import org.springframework.integration.scheduling.PollerMetadata;
     import org.springframework.util.StringUtils;

     import java.time.LocalTime;
     import java.time.ZoneId;
     import java.time.format.DateTimeFormatter;

     /**
      * An abstract base class that provides common utility methods for SFTP flow configurations.
      */
     @Slf4j
     public abstract class AbstractSftpFlowConfig {

         protected final ApplicationContext applicationContext;
         protected final SftpProperties sftpProperties;
         protected final SftpFileProcessor globalFileProcessor;
         protected static final long DEFAULT_FALLBACK_FIXED_DELAY = 5000L;

         public AbstractSftpFlowConfig(ApplicationContext applicationContext, SftpProperties sftpProperties, SftpFileProcessor globalFileProcessor) {
             this.applicationContext = applicationContext;
             this.sftpProperties = sftpProperties;
             this.globalFileProcessor = globalFileProcessor;
         }

         /**
          * Retrieves the SftpFileProcessor for the given server configuration.
          * If a custom processor is defined (via processorClass), returns that; otherwise returns the global one.
          *
          * @param server the server configuration.
          * @return the SftpFileProcessor.
          */
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

         /**
          * Builds PollerMetadata based on the provided poller configuration and attaches the supplied error advice.
          *
          * @param poller the poller configuration.
          * @param advice the error-handling advice.
          * @return the PollerMetadata.
          */
         protected PollerMetadata buildPollerMetadata(SftpProperties.PollerProperties poller, ExpressionEvaluatingRequestHandlerAdvice advice) {
             long fallbackDelay = DEFAULT_FALLBACK_FIXED_DELAY;
             if (poller != null && poller.getFallbackFixedDelay() != null) {
                 fallbackDelay = poller.getFallbackFixedDelay();
             }
             if (poller != null) {
                 if ("fixed".equalsIgnoreCase(poller.getType())) {
                     if (poller.getFixedInterval() != null) {
                         return Pollers.fixedDelay(poller.getFixedInterval()).advice(advice).getObject();
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
             return Pollers.fixedDelay(fallbackDelay).advice(advice).getObject();
         }

         /**
          * Creates error-handling advice that routes failures to the global error channel.
          *
          * @return the error-handling advice.
          */
         protected ExpressionEvaluatingRequestHandlerAdvice createErrorHandlingAdvice() {
             ExpressionEvaluatingRequestHandlerAdvice advice = new ExpressionEvaluatingRequestHandlerAdvice();
             advice.setFailureChannelName("errorChannel");
             advice.setTrapException(true);
             advice.setOnFailureExpressionString("payload.toString()");
             return advice;
         }
     }
     ```

4. **SftpDownloadFlowConfig.java** in package `com.example.sftp.autoconfiguration.inbound`
   - Use the following exact implementation:
     ```java
     package com.example.sftp.autoconfiguration.inbound;

     import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
     import com.example.sftp.autoconfiguration.SftpFileProcessor;
     import com.example.sftp.autoconfiguration.SftpProperties;
     import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
     import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
     import com.example.sftp.autoconfiguration.exception.LocalDownloadDirectoryCreationException;
     import com.example.sftp.autoconfiguration.transformers.DownloadPostProcessorTransformer;
     import jakarta.annotation.PostConstruct;
     import lombok.extern.slf4j.Slf4j;
     import org.springframework.context.ApplicationContext;
     import org.springframework.context.annotation.Configuration;
     import org.springframework.integration.dsl.IntegrationFlow;
     import org.springframework.integration.dsl.context.IntegrationFlowContext;
     import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
     import org.springframework.integration.scheduling.PollerMetadata;
     import org.springframework.integration.sftp.dsl.Sftp;
     import org.springframework.util.StringUtils;

     import java.io.File;
     import java.util.Objects;

     /**
      * Configures and registers inbound (download) SFTP flows.
      */
     @Slf4j
     @Configuration
     public class SftpDownloadFlowConfig extends AbstractSftpFlowConfig {

       private final SftpProperties sftpProperties;
       private final SftpSessionFactoryProvider factoryProvider;
       private final IntegrationFlowContext flowContext;

       public SftpDownloadFlowConfig(ApplicationContext applicationContext,
                                     SftpProperties sftpProperties,
                                     SftpFileProcessor globalFileProcessor,
                                     SftpSessionFactoryProvider factoryProvider,
                                     IntegrationFlowContext flowContext) {
         super(applicationContext, sftpProperties, globalFileProcessor);
         this.sftpProperties = sftpProperties;
         this.factoryProvider = factoryProvider;
         this.flowContext = flowContext;
       }

       @PostConstruct
       public void registerDownloadFlows() {
         if (sftpProperties.getServers() != null) {
           ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice = createErrorHandlingAdvice();

           for (SftpServerConfig server : sftpProperties.getServers()) {
             String serverName = server.getName();
             var factory = factoryProvider.getFactory(serverName);
             SftpFileProcessor fileProcessor = obtainProcessor(server);

             // Determine effective local download directory.
             String effectiveLocalDownloadDir;
             if (StringUtils.hasText(server.getLocalDownloadDir())) {
               effectiveLocalDownloadDir = server.getLocalDownloadDir();
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
                 server.getPoller() != null ? server.getPoller() : sftpProperties.getDefaultPoller(),
                 errorHandlingAdvice);
             SftpProperties.RetryProperties effectiveRetry =
                 server.getRetry() != null ? server.getRetry() : sftpProperties.getDefaultRetry();

             if (StringUtils.hasText(server.getFrom())) {
               IntegrationFlow downloadFlow = org.springframework.integration.dsl.IntegrationFlow.from(
                       Sftp.inboundAdapter(factory)
                           .preserveTimestamp(true)
                           .remoteDirectory(server.getFrom())
                           .localDirectory(localDownloadDirectory)
                           .autoCreateLocalDirectory(true),
                       c -> c.poller(pollerMetadata))
                   .transform(new DownloadPostProcessorTransformer(fileProcessor, serverName, effectiveRetry))
                   .filter(Objects::nonNull)
                   .channel("nullChannel")
                   .get();
               flowContext.registration(downloadFlow)
                   .id("sftpDownloadFlow-" + serverName)
                   .register();
             }
           }
         }
       }
     }
     ```

5. **SftpUploadFlowConfig.java** in package `com.example.sftp.autoconfiguration.outbound`
   - Use the following exact implementation:
     ```java
     package com.example.sftp.autoconfiguration.outbound;

     import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
     import com.example.sftp.autoconfiguration.SftpFileProcessor;
     import com.example.sftp.autoconfiguration.SftpProperties;
     import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
     import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
     import com.example.sftp.autoconfiguration.transformers.UploadPreProcessorTransformer;
     import jakarta.annotation.PostConstruct;
     import lombok.extern.slf4j.Slf4j;
     import org.springframework.context.ApplicationContext;
     import org.springframework.context.annotation.Configuration;
     import org.springframework.integration.dsl.IntegrationFlow;
     import org.springframework.integration.dsl.context.IntegrationFlowContext;
     import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
     import org.springframework.integration.sftp.dsl.Sftp;
     import org.springframework.util.StringUtils;

     import java.util.Objects;

     /**
      * Configures and registers outbound SFTP upload flows.
      */
     @Slf4j
     @Configuration
     public class SftpUploadFlowConfig extends AbstractSftpFlowConfig {

       private final SftpProperties sftpProperties;
       private final SftpSessionFactoryProvider factoryProvider;
       private final IntegrationFlowContext flowContext;

       public SftpUploadFlowConfig(ApplicationContext applicationContext,
                                   SftpProperties sftpProperties,
                                   SftpFileProcessor globalFileProcessor,
                                   SftpSessionFactoryProvider factoryProvider,
                                   IntegrationFlowContext flowContext) {
         super(applicationContext, sftpProperties, globalFileProcessor);
         this.sftpProperties = sftpProperties;
         this.factoryProvider = factoryProvider;
         this.flowContext = flowContext;
       }

       @PostConstruct
       public void registerUploadFlows() {
         if (sftpProperties.getServers() != null) {
           ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice = createErrorHandlingAdvice();

           for (SftpServerConfig server : sftpProperties.getServers()) {
             if (StringUtils.hasText(server.getTo())) {
               String serverName = server.getName();
               var factory = factoryProvider.getFactory(serverName);
               SftpFileProcessor fileProcessor = obtainProcessor(server);
               SftpProperties.RetryProperties effectiveRetry =
                   server.getRetry() != null ? server.getRetry() : sftpProperties.getDefaultRetry();

               IntegrationFlow uploadFlow = org.springframework.integration.dsl.IntegrationFlow.from(() -> null) // Dummy source.
                   .transform(new UploadPreProcessorTransformer(fileProcessor, serverName, effectiveRetry))
                   .filter(Objects::nonNull)
                   .handle(
                       Sftp.outboundAdapter(factory)
                           .remoteDirectory(server.getTo())
                           .autoCreateDirectory(true),
                       spec -> spec.advice(errorHandlingAdvice))
                   .channel("nullChannel")
                   .get();
               flowContext.registration(uploadFlow)
                   .id("sftpUploadFlow-" + serverName)
                   .register();
             }
           }
         }
       }
     }
     ```

6. **SftpArchiveFlowConfig.java** in package `com.example.sftp.autoconfiguration.outbound`
   - Use the following exact implementation:
     ```java
     package com.example.sftp.autoconfiguration.outbound;

     import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
     import com.example.sftp.autoconfiguration.SftpFileProcessor;
     import com.example.sftp.autoconfiguration.SftpProperties;
     import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
     import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
     import com.example.sftp.autoconfiguration.transformers.ArchivePrePostProcessorTransformer;
     import jakarta.annotation.PostConstruct;
     import lombok.extern.slf4j.Slf4j;
     import org.springframework.context.ApplicationContext;
     import org.springframework.context.annotation.Configuration;
     import org.springframework.integration.dsl.IntegrationFlow;
     import org.springframework.integration.dsl.context.IntegrationFlowContext;
     import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
     import org.springframework.integration.sftp.dsl.Sftp;
     import org.springframework.util.StringUtils;

     import java.util.Objects;

     /**
      * Configures and registers outbound SFTP archive flows.
      */
     @Slf4j
     @Configuration
     public class SftpArchiveFlowConfig extends AbstractSftpFlowConfig {

       private final SftpProperties sftpProperties;
       private final SftpSessionFactoryProvider factoryProvider;
       private final IntegrationFlowContext flowContext;

       public SftpArchiveFlowConfig(ApplicationContext applicationContext,
                                    SftpProperties sftpProperties,
                                    SftpFileProcessor globalFileProcessor,
                                    SftpSessionFactoryProvider factoryProvider,
                                    IntegrationFlowContext flowContext) {
         super(applicationContext, sftpProperties, globalFileProcessor);
         this.sftpProperties = sftpProperties;
         this.factoryProvider = factoryProvider;
         this.flowContext = flowContext;
       }

       @PostConstruct
       public void registerArchiveFlows() {
         if (sftpProperties.getServers() != null) {
           ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice = createErrorHandlingAdvice();

           for (SftpServerConfig server : sftpProperties.getServers()) {
             if (StringUtils.hasText(server.getArchive())) {
               String serverName = server.getName();
               var factory = factoryProvider.getFactory(serverName);
               SftpFileProcessor fileProcessor = obtainProcessor(server);
               SftpProperties.RetryProperties effectiveRetry =
                   server.getRetry() != null ? server.getRetry() : sftpProperties.getDefaultRetry();

               IntegrationFlow archiveFlow = org.springframework.integration.dsl.IntegrationFlow.from(() -> null) // Dummy source.
                   .transform(new ArchivePrePostProcessorTransformer(fileProcessor, serverName, true, effectiveRetry))
                   .filter(Objects::nonNull)
                   .handle(
                       Sftp.outboundGateway(factory, "mv", "payload")
                           .renameExpression("headers.destinationPath"),
                       spec -> spec.advice(errorHandlingAdvice))
                   .transform(new ArchivePrePostProcessorTransformer(fileProcessor, serverName, false, effectiveRetry))
                   .filter(Objects::nonNull)
                   .channel("nullChannel")
                   .get();
               flowContext.registration(archiveFlow)
                   .id("sftpArchiveFlow-" + serverName)
                   .register();
             }
           }
         }
       }
     }
     ```

7. **Additional Classes:**
   - **RetryUtils.java** in package `com.example.sftp.autoconfiguration.util`
   - **DownloadPostProcessorTransformer.java, UploadPreProcessorTransformer.java,** and **ArchivePrePostProcessorTransformer.java** in package `com.example.sftp.autoconfiguration.transformers`
   - **SftpFileProcessor.java**, **SftpProperties.java**, **SftpSessionFactoryBuilder.java**, **SftpSessionFactoryProvider.java**, **TimeWindowTrigger.java**, and
   - **LocalDownloadDirectoryCreationException.java** in their respective packages.
   (Use the exact definitions as already provided in our previous implementations.)

Ensure that the auto-configuration metadata file is placed under the resources folder as:
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
with the contents:
```
com.example.sftp.autoconfiguration.GlobalErrorHandlingConfig
com.example.sftp.autoconfiguration.inbound.SftpDownloadFlowConfig
com.example.sftp.autoconfiguration.outbound.SftpUploadFlowConfig
com.example.sftp.autoconfiguration.outbound.SftpArchiveFlowConfig
```

This prompt should generate the full, modularized SFTP starter library implementation exactly as we have built so far, following best practices and using industry-standard design patterns.
```

---

Using this prompt will instruct the AI to produce the entire complete solution with the modularized inbound, upload, and archive configuration classes extending the common base class, along with all supporting classes and the Spring Boot auto-configuration metadata file.
