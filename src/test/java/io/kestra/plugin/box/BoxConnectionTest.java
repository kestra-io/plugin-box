package io.kestra.plugin.box;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.box.files.Get;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class BoxConnectionTest {
    @Inject
    private RunContextFactory runContextFactory;

    private Get.GetBuilder<?, ?> task() {
        return Get.builder().fileId(Property.ofValue("1"));
    }

    @Test
    void developerToken() throws Exception {
        var task = task().developerToken(Property.ofValue("token")).build();

        assertThat(BoxConnectionInterface.client(runContextFactory.of(), task), notNullValue());
    }

    @Test
    void clientCredentialsWithEnterprise() throws Exception {
        var task = task()
            .clientId(Property.ofValue("id"))
            .clientSecret(Property.ofValue("secret"))
            .enterpriseId(Property.ofValue("1234"))
            .build();

        assertThat(BoxConnectionInterface.client(runContextFactory.of(), task), notNullValue());
    }

    @Test
    void clientCredentialsNeedASubject() {
        var task = task().clientId(Property.ofValue("id")).clientSecret(Property.ofValue("secret")).build();

        var e = assertThrows(IllegalArgumentException.class, () -> BoxConnectionInterface.client(runContextFactory.of(), task));
        assertThat(e.getMessage(), containsString("enterpriseId"));
    }

    @Test
    void noCredentials() {
        var e = assertThrows(IllegalArgumentException.class, () -> BoxConnectionInterface.client(runContextFactory.of(), task().build()));
        assertThat(e.getMessage(), containsString("No Box credentials"));
    }
}
