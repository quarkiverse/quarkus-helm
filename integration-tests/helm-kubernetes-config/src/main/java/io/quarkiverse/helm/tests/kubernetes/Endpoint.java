package io.quarkiverse.helm.tests.kubernetes;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("")
public class Endpoint {

    @ConfigProperty(name = "hello.message")
    String message;

    @ConfigProperty(name = "hello.number")
    int number;

    @ConfigProperty(name = "hello.flag")
    boolean flag;

    @GET
    public Response get(@QueryParam("name") @DefaultValue("World") String name) {
        return Response.ok().entity(String.format(message, name) + ", number=" + number + ", flag=" + flag).build();
    }
}
