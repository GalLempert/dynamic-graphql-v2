package iaf.ofek.sigma.observability;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.OperationDefinition;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Wraps GraphQL execution in a Micrometer {@link Observation} so request metrics and traces are
 * exported automatically via Actuator.
 */
@Component
public class GraphQLRequestObservationInstrumentation extends SimpleInstrumentation {

    static final String OBSERVATION_NAME = "graphql.request.duration";
    static final String CONTEXTUAL_NAME = "graphql.request";

    private final ObservationRegistry observationRegistry;

    public GraphQLRequestObservationInstrumentation(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(
            InstrumentationExecutionParameters parameters) {
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                .contextualName(CONTEXTUAL_NAME)
                .lowCardinalityKeyValue("operation.name", determineOperationName(parameters))
                .lowCardinalityKeyValue("operation.type", determineOperationType(parameters))
                .start();

        return new SimpleInstrumentationContext<>() {
            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
                try {
                    if (t != null) {
                        observation.error(t);
                    } else if (result != null && !result.getErrors().isEmpty()) {
                        observation.error(new GraphQLRequestFailure(result.getErrors().size()));
                    }
                } finally {
                    observation.stop();
                }
            }
        };
    }

    private String determineOperationName(InstrumentationExecutionParameters parameters) {
        return Optional.ofNullable(parameters.getExecutionInput().getOperationName())
                .filter(StringUtils::hasText)
                .orElse("anonymous");
    }

    private String determineOperationType(InstrumentationExecutionParameters parameters) {
        return Optional.ofNullable(parameters.getOperation())
                .map(OperationDefinition::getOperation)
                .map(operation -> operation.name().toLowerCase(Locale.ROOT))
                .orElse("unknown");
    }

    private static final class GraphQLRequestFailure extends RuntimeException {

        GraphQLRequestFailure(int errorCount) {
            super("GraphQL request completed with " + errorCount + " error(s)");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
