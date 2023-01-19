package io.quarkiverse.helm.tests.kubernetes;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("")
public class Endpoint {

    @GET
    public String hello() {
        return "Hello, World!";
    }
}