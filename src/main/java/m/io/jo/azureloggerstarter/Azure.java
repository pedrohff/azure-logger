package m.io.jo.azureloggerstarter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "azure-logger")
public class Azure {
    private boolean enabled;
    private String sharedKey;
    private String workspaceId;
}
