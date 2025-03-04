package io.quarkiverse.helm.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the <a href="https://github.com/helm/helm">Helm</a>
 * <a href="https://github.com/helm/helm/blob/v3.10.1/pkg/chart/metadata.go">Chart.yaml file</a>
 */
@JsonInclude(NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Chart {
    @JsonProperty
    private String name;
    @JsonProperty
    private String home;
    @JsonProperty
    private List<String> sources;
    @JsonProperty
    private String version;
    @JsonProperty
    private String description;
    @JsonProperty
    private List<String> keywords;
    @JsonProperty
    private List<Maintainer> maintainers;
    @JsonProperty
    private String icon;
    @JsonProperty
    private String apiVersion;
    @JsonProperty
    private String condition;
    @JsonProperty
    private String tags;
    @JsonProperty
    private String appVersion;
    @JsonProperty
    private Boolean deprecated;
    @JsonProperty
    private Map<String, String> annotations;
    @JsonProperty
    private String kubeVersion;
    @JsonProperty
    private List<HelmDependency> dependencies;
    @JsonProperty
    private String type;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<HelmDependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<HelmDependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<Maintainer> getMaintainers() {
        return maintainers;
    }

    public void setMaintainers(List<Maintainer> maintainers) {
        this.maintainers = maintainers;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public Boolean getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public String getKubeVersion() {
        return kubeVersion;
    }

    public void setKubeVersion(String kubeVersion) {
        this.kubeVersion = kubeVersion;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "Chart{" +
                "name='" + name + '\'' +
                ", home='" + home + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

}
