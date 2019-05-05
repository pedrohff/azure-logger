# Azure Logger Spring Boot Starter

This project allows you to connect to Azure Monitor logger by simply adding the properties below:

```bash
azure-logger.enabled=true
azure-logger.sharedKey=mySharedKey
azure-logger.workspaceId=myWorkspaceId

spring.application.name=myAppName
spring.application.version=0.0.1-RELEASE
```

Then, you can send your logs to Azure by doing:

```java
    @Autowired
    private AzureLogger azureLogger;
    
    public void sendJsonToAzure(String json) {
        azureLogger.pushLogsToAzure(json);
    }
```