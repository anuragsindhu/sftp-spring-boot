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

