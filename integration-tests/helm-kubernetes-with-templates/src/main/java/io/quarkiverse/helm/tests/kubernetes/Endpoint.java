package io.quarkiverse.helm.tests.kubernetes;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("")
public class Endpoint {

    @GET
    public String hello() {
        return "Hello, World!";
    }
}