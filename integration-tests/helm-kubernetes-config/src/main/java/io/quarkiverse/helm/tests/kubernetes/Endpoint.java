package io.quarkiverse.helm.tests.kubernetes;

import java.util.Optional;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("")
public class Endpoint {

    @ConfigProperty(name = "hello.message")
    Optional<String> message;

    @GET
    public Response get(@QueryParam("name") @DefaultValue("World") String name) {
        if (message.isPresent()) {
            return Response.ok().entity(String.format(message.get(), name)).build();
        }

        return Response.serverError().entity("ConfigMap not present").build();
    }
}