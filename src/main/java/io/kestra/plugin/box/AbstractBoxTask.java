package io.kestra.plugin.box;

import com.box.sdkgen.client.BoxClient;
import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractBoxTask extends Task implements BoxConnectionInterface {

    @ToString.Exclude
    @Schema(title = "Box client ID")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> clientId;

    @ToString.Exclude
    @Schema(title = "Box client secret")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> clientSecret;

    @Schema(title = "Enterprise ID", description = """
        Use with CCG to act as the app's service account.""")
    @PluginProperty(group = "connection")
    private Property<String> enterpriseId;

    @Schema(title = "User ID", description = """
        Use with CCG to act as a specific user instead of the enterprise.""")
    @PluginProperty(group = "connection")
    private Property<String> userId;

    @ToString.Exclude
    @Schema(title = "Developer token", description = """
        Short-lived token for quick tests. Takes priority over other auth.""")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> developerToken;

    @ToString.Exclude
    @Schema(title = "JWT config JSON", description = """
        Contents of the JSON config file downloaded from the Box developer console. Used when no developer token is set and no client credentials are set.""")
    @PluginProperty(group = "connection", secret = true)
    private Property<String> jwtConfig;

    @VisibleForTesting
    protected BoxClient client(RunContext runContext) throws Exception {
        return BoxConnectionInterface.client(runContext, this);
    }
}
