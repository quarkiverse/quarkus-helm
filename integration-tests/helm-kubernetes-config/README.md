# Example

The purpose of this example is to demonstrate how to use the [Kubernetes Config](https://quarkus.io/guides/kubernetes-config) extension. 

Basically, after adding the Kubernetes Config, Kubernetes, and Helm extensions to your Maven/Gradle configuration, you need first to enable it by adding the following properties to your application properties:

```
quarkus.kubernetes-config.enabled=true
quarkus.kubernetes-config.config-maps=app-config
```

With these two properties, Quarkus will try to load the config map named `app-config` at startup as config source.

Where is the ConfigMap named `app-config`? You need to write it on your own and write the application properties there, for example:

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  application.properties: |
    hello.message=Hello %s from configmap
```

and then add the content at the file `src/main/kubernetes/kubernetes.yml`. (Note that the name of the file must be `kubernetes.yml` and the folder `src/main/kubernetes`). More information is in [this link](https://quarkus.io/guides/deploying-to-kubernetes#using-existing-resources).

The Kubernetes extension will aggregate the resources within the file `src/main/kubernetes/kubernetes.yml` into the generated `target/kubernetes/kubernetes.yml` (you will notice your configmap is there).

And finally, the Helm extension will inspect the `target/kubernetes` folder and create the Helm chart templates accordingly. 