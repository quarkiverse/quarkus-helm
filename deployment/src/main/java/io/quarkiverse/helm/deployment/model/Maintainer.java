package io.quarkiverse.helm.deployment.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the <a href="https://github.com/helm/helm">Helm</a>
 * <a href="https://github.com/helm/helm/blob/v3.7.2/pkg/chart/metadata.go#L26">Maintainer object</a>
 */
@JsonInclude(NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Maintainer implements Serializable {

    private static final long serialVersionUID = -968020668786188166L;

    @JsonProperty
    private String name;

    @JsonProperty
    private String email;

    @JsonProperty
    private String url;

    public Maintainer() {

    }

    public Maintainer(String name, String email, String url) {
        this.name = name;
        this.email = email;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
