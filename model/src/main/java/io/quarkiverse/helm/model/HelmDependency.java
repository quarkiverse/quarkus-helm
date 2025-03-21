package io.quarkiverse.helm.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the <a href="https://github.com/helm/helm">Helm</a>
 * <a href="https://github.com/helm/helm/blob/v3.10.1/pkg/chart/dependency.go">Dependency object</a>
 */
@JsonInclude(NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelmDependency {

    @JsonProperty
    private String name;

    @JsonProperty
    private String version;

    @JsonProperty
    private String repository;

    @JsonProperty
    private String condition;

    @JsonProperty
    private String[] tags;

    @JsonProperty
    private Boolean enabled;

    @JsonProperty
    private String alias;

    public HelmDependency() {

    }

    public HelmDependency(String name, String alias, String version, String repository, String condition, String[] tags,
            Boolean enabled) {
        this.name = name;
        this.alias = alias;
        this.version = version;
        this.repository = repository;
        this.condition = condition;
        this.tags = tags;
        if (!enabled) {
            this.enabled = false;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
