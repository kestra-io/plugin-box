package io.kestra.plugin.box;

import com.box.sdkgen.box.ccgauth.BoxCCGAuth;
import com.box.sdkgen.box.ccgauth.CCGConfig;
import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.box.jwtauth.BoxJWTAuth;
import com.box.sdkgen.box.jwtauth.JWTConfig;
import com.box.sdkgen.client.BoxClient;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;

/**
 * A task and a trigger have different base classes, so both declare the connection fields
 * and share only the client-building logic through this interface.
 */
public interface BoxConnectionInterface {
    Property<String> getClientId();

    Property<String> getClientSecret();

    Property<String> getEnterpriseId();

    Property<String> getUserId();

    Property<String> getDeveloperToken();

    Property<String> getJwtConfig();

    static BoxClient client(RunContext runContext, BoxConnectionInterface connection) throws Exception {
        var rDeveloperToken = runContext.render(connection.getDeveloperToken()).as(String.class);
        if (rDeveloperToken.isPresent()) {
            return new BoxClient(new BoxDeveloperTokenAuth(rDeveloperToken.get()));
        }

        var rEnterpriseId = runContext.render(connection.getEnterpriseId()).as(String.class);
        var rUserId = runContext.render(connection.getUserId()).as(String.class);

        var rJwtConfig = runContext.render(connection.getJwtConfig()).as(String.class);
        if (rJwtConfig.isPresent()) {
            var jwt = new BoxJWTAuth(JWTConfig.fromConfigJsonString(rJwtConfig.get()));
            // without a subject the JWT config's own enterprise ID is used
            if (rUserId.isPresent()) {
                jwt = jwt.withUserSubject(rUserId.get());
            } else if (rEnterpriseId.isPresent()) {
                jwt = jwt.withEnterpriseSubject(rEnterpriseId.get());
            }
            return new BoxClient(jwt);
        }

        var rClientId = runContext.render(connection.getClientId()).as(String.class);
        var rClientSecret = runContext.render(connection.getClientSecret()).as(String.class);
        if (rClientId.isPresent() && rClientSecret.isPresent()) {
            var ccg = new BoxCCGAuth(new CCGConfig(rClientId.get(), rClientSecret.get()));
            if (rUserId.isPresent()) {
                ccg = ccg.withUserSubject(rUserId.get());
            } else if (rEnterpriseId.isPresent()) {
                ccg = ccg.withEnterpriseSubject(rEnterpriseId.get());
            } else {
                throw new IllegalArgumentException("Client credentials need either 'enterpriseId' or 'userId'");
            }
            return new BoxClient(ccg);
        }

        throw new IllegalArgumentException(
            "No Box credentials: set 'developerToken', or 'jwtConfig', or 'clientId' + 'clientSecret' with 'enterpriseId' or 'userId'"
        );
    }
}
