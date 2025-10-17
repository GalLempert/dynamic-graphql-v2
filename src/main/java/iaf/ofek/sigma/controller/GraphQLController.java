package iaf.ofek.sigma.controller;

import com.netflix.graphql.dgs.DgsComponent;
import iaf.ofek.sigma.engine.GraphQLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GraphQL API controller
 * Handles GraphQL queries and mutations using Netflix DGS
 */
@DgsComponent
public class GraphQLController {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLController.class);

    private final GraphQLEngine engine;

    public GraphQLController(GraphQLEngine engine) {
        this.engine = engine;
    }

    // GraphQL data fetchers will be added here
    // Example:
    // @DgsQuery
    // public String hello(@InputArgument String name) {
    //     return engine.processHello(name);
    // }
}
