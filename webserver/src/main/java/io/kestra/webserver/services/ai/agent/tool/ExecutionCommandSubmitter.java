package io.kestra.webserver.services.ai.agent.tool;

import java.util.concurrent.TimeoutException;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationsConfiguration;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.services.AsyncOperationWaiter;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sends execution commands on behalf of the agent tools and blocks until the executor has applied them.
 */
@Singleton
public class ExecutionCommandSubmitter {
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final AsyncOperationWaiter asyncOperationWaiter;
    private final AsyncOperationsConfiguration asyncOperationsConfiguration;

    @Inject
    public ExecutionCommandSubmitter(
        final DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        final AsyncOperationWaiter asyncOperationWaiter,
        final AsyncOperationsConfiguration asyncOperationsConfiguration) {
        this.executionCommandQueue = executionCommandQueue;
        this.asyncOperationWaiter = asyncOperationWaiter;
        this.asyncOperationsConfiguration = asyncOperationsConfiguration;
    }

    /**
     * Emits the commands built by {@code emitter} and waits until the one tagged with the operation id is applied.
     *
     * @param executionId the execution the operation completes on, which for a replay is the new execution
     * @param action the action, in lower case, used in error messages (e.g. "restarted")
     * @throws IllegalStateException if the executor rejects the command or does not apply it in time
     */
    public void submitAndAwait(final String executionId, final String action, final Emitter emitter) {
        AsyncOperationProcessedEvent processed;
        try {
            processed = asyncOperationWaiter.submitAndWait(
                executionId,
                operationId ->
                {
                    try {
                        emitter.emit(executionCommandQueue, operationId);
                    } catch (QueueException e) {
                        throw new RuntimeException(e);
                    }
                },
                asyncOperationsConfiguration.waitTimeout()
            );
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for execution '%s' to be %s.".formatted(executionId, action), e);
        }

        if (processed.outcome() == AsyncOperationProcessedEvent.Outcome.FAILED) {
            throw new IllegalStateException("Execution '%s' could not be %s: %s".formatted(executionId, action, processed.error()));
        }
    }

    @FunctionalInterface
    public interface Emitter {
        void emit(DispatchQueueInterface<ExecutionCommand> queue, String operationId) throws QueueException;
    }
}
