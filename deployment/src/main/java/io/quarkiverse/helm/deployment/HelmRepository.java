package io.quarkiverse.helm.deployment;

import java.util.Optional;

import io.dekorate.utils.Strings;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class HelmRepository {
    /**
     * If true, it will perform the upload to a Helm repository.
     */
    @ConfigItem(defaultValue = "false")
    public boolean push;
    /**
     * The Helm repository type. Options are: `CHARTMUSEUM`, `ARTIFACTORY`, and `NEXUS`.
     */
    @ConfigItem
    public Optional<HelmRepositoryType> type;
    /**
     * The Helm repository URL.
     */
    @ConfigItem
    public Optional<String> url;
    /**
     * The Helm repository username.
     */
    @ConfigItem
    public Optional<String> username;
    /**
     * The Helm repository password.
     */
    @ConfigItem
    public Optional<String> password;

    public String getUsername() {
        return username.filter(Strings::isNotNullOrEmpty).orElse(null);
    }

    public String getPassword() {
        return password.filter(Strings::isNotNullOrEmpty).orElse(null);
    }
}
