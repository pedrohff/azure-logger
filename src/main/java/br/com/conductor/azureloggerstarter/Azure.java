package br.com.conductor.azureloggerstarter;

import lombok.Data;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class Azure {
    private boolean enabled;
    private String sharedKey;
    private String workspaceId;
}
