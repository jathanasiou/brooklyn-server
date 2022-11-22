/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.util.core.predicates.DslPredicates;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskTags;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

import static org.apache.brooklyn.core.workflow.WorkflowExecutionContext.*;

public class WorkflowErrorHandling implements Callable<WorkflowErrorHandling.WorkflowErrorHandlingResult> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowErrorHandling.class);

    /*
     * TODO ui for error handling
     * step task's workflow tag will have ERROR_HANDLED_BY single-key map tag pointing at handler parent task, created in this method.
     * error handler parent task will have 'errorHandlerForTask' field in the workflow tag pointing at step task, but no errorHandlerIndex.
     * if any of the handler steps match, the parent will have ERROR_HANDLED_BY pointing at it, and will have a task with the workflow tag with
     * 'errorHandlerForTask' field pointing at the parent and also 'errorHandlerIndex' set to the index in the step's error list, but not ERROR_HANDLED_BY.
     *
     * the workflow execution context's errorHandlerContext will point at the error handler context,
     * but this is cleared when the step runs, and sub-workflows there are not stored or replayable.
     */

    /** returns a result, or null if nothing applied (and caller should rethrow the error) */
    public static Task<WorkflowErrorHandlingResult> createStepErrorHandlerTask(WorkflowStepDefinition step, WorkflowStepInstanceExecutionContext context, Task<?> stepTask, Throwable error, Integer errorStepIfNested) {
        log.debug("Encountered error in step "+context.getWorkflowStepReference()+" '" + stepTask.getDisplayName() + "' (handler present): " + Exceptions.collapseText(error));
        String taskName = context.getWorkflowStepReference();

        if (errorStepIfNested!=null) {
            // already has error-handler reference, either from here or from computed name
            taskName = context.getWorkflowStepReference()+"-"+errorStepIfNested;
        } else {
            taskName += "-"+LABEL_FOR_ERROR_HANDLER;
        }

        Task<WorkflowErrorHandlingResult> task = Tasks.<WorkflowErrorHandlingResult>builder().dynamic(true).displayName(taskName)
                .tag(BrooklynTaskTags.tagForWorkflowStepErrorHandler(context, null, context.getTaskId()))
                .tag(BrooklynTaskTags.WORKFLOW_TAG)
                .body(new WorkflowErrorHandling(step.getOnError(), context.getWorkflowExectionContext(), context.getWorkflowExectionContext().currentStepIndex, stepTask, error))
                .build();
        TaskTags.addTagDynamically(stepTask, BrooklynTaskTags.tagForErrorHandledBy(task));
        log.debug("Creating step "+context.getWorkflowStepReference()+" error handler "+task.getDisplayName()+" in task " + task.getId());
        return task;
    }

    /** returns a result, or null if nothing applied (and caller should rethrow the error) */
    public static Task<WorkflowErrorHandlingResult> createWorkflowErrorHandlerTask(WorkflowExecutionContext context, Task<?> workflowTask, Throwable error) {
        log.debug("Encountered error in workflow "+context.getWorkflowId()+"/"+context.getTaskId()+" '" + workflowTask.getDisplayName() + "' (handler present): " + Exceptions.collapseText(error));
        Task<WorkflowErrorHandlingResult> task = Tasks.<WorkflowErrorHandlingResult>builder().dynamic(true).displayName(context.getWorkflowId()+"-"+LABEL_FOR_ERROR_HANDLER)
                .tag(BrooklynTaskTags.tagForWorkflowStepErrorHandler(context))
                .tag(BrooklynTaskTags.WORKFLOW_TAG)
                .body(new WorkflowErrorHandling(context.onError, context, null, workflowTask, error))
                .build();
        TaskTags.addTagDynamically(workflowTask, BrooklynTaskTags.tagForErrorHandledBy(task));
        log.debug("Creating workflow "+context.getWorkflowId()+"/"+context.getTaskId()+" error handler "+task.getDisplayName()+" in task " + task.getId());
        return task;
    }

    final List<WorkflowStepDefinition> errorOptions;
    final WorkflowExecutionContext context;
    final Integer stepIndexIfStepErrorHandler;
    /** The step or the workflow task */
    final Task<?> failedTask;
    final Throwable error;

    public WorkflowErrorHandling(List<Object> errorOptions, WorkflowExecutionContext context, Integer stepIndexIfStepErrorHandler, Task<?> failedTask, Throwable error) {
        this.errorOptions = WorkflowStepResolution.resolveSubSteps(context.getManagementContext(), "error handling", errorOptions);
        this.context = context;
        this.stepIndexIfStepErrorHandler = stepIndexIfStepErrorHandler;
        this.failedTask = failedTask;
        this.error = error;
    }

    @Override
    public WorkflowErrorHandlingResult call() throws Exception {
        WorkflowErrorHandlingResult result = new WorkflowErrorHandlingResult();
        Task handlerParent = Tasks.current();
        log.debug("Starting "+handlerParent.getDisplayName()+" with "+ errorOptions.size()+" handler"+(errorOptions.size()!=1 ? " options" : "")+" in task "+handlerParent.getId());

        int errorStepsMatching = 0;
        WorkflowStepDefinition errorStep = null;
        boolean lastStepConditionMatched = false;

        for (int i = 0; i< errorOptions.size(); i++) {
            // go through steps, find first that matches

            errorStep = errorOptions.get(i);

            WorkflowStepInstanceExecutionContext handlerContext = new WorkflowStepInstanceExecutionContext(stepIndexIfStepErrorHandler!=null ? stepIndexIfStepErrorHandler : WorkflowExecutionContext.STEP_INDEX_FOR_ERROR_HANDLER, errorStep, context);
            context.errorHandlerContext = handlerContext;
            handlerContext.error = error;
            lastStepConditionMatched = false;

            String potentialTaskName = Tasks.current().getDisplayName()+"-"+(i+1);
            DslPredicates.DslPredicate condition = errorStep.getConditionResolved(handlerContext);
            if (condition!=null) {
                if (log.isTraceEnabled()) log.trace("Considering condition " + condition + " for " + potentialTaskName);
                boolean conditionMet = DslPredicates.evaluateDslPredicateWithBrooklynObjectContext(condition, error, handlerContext.getEntity());
                if (log.isTraceEnabled()) log.trace("Considered condition " + condition + " for " + potentialTaskName + ": " + conditionMet);
                if (!conditionMet) continue;
            }

            errorStepsMatching++;
            lastStepConditionMatched = true;

            Task<?> handlerI = errorStep.newTaskAsSubTask(handlerContext,
                    potentialTaskName, BrooklynTaskTags.tagForWorkflowStepErrorHandler(handlerContext, i, handlerParent.getId()));
            TaskTags.addTagDynamically(handlerParent, BrooklynTaskTags.tagForErrorHandledBy(handlerI));

            log.debug("Creating handler " + potentialTaskName + " '" + errorStep.computeName(handlerContext, false)+"' in task "+handlerI.getId());
            if (!potentialTaskName.equals(context.getWorkflowStepReference(handlerI))) {
                // shouldn't happen, but double check
                log.warn("Mismatch in step name: "+potentialTaskName+" / "+context.getWorkflowStepReference(handlerI));
            }

            try {
                result.output = DynamicTasks.queue(handlerI).getUnchecked();

                if (errorStep.output!=null) {
                    result.output = handlerContext.resolve(WorkflowExpressionResolution.WorkflowExpressionStage.STEP_FINISHING_POST_OUTPUT, errorStep.output, Object.class);
                }
                result.next = WorkflowReplayUtils.getNext(handlerContext, errorStep);

            } catch (Exception errorInErrorHandlerStep) {
                BiConsumer onFinish = (output, next) -> {
                    result.output = output;
                    result.next = next;
                };
                WorkflowErrorHandling.handleErrorAtStep(context.getEntity(), errorStep, handlerContext, handlerI, onFinish, errorInErrorHandlerStep, i);
            }

            if (result.next!=null) {
                log.debug("Completed handler " + potentialTaskName +
                        (errorStepsMatching > 1 ? " with "+errorStepsMatching+" steps matching" : "") +
                        "; proceeding to explicit next step '" + result.next + "'");
                break;
            }
        }

        if (result.next==null) {
            // no 'next' defined; check we properly handled it
            if (errorStepsMatching==0 || errorStep==null) {
                log.debug("Error handler options were present but did not apply at "+handlerParent.getId()+"; will rethrow error: "+Exceptions.collapseText(error));
                throw Exceptions.propagate(error);
            }
            if (!lastStepConditionMatched) {
                log.warn("Error handler ran but did not branch and final step had a condition which did not match; " +
                        "for clarity, error handlers should either indicate a next step or ensure the final step is not conditional. " +
                        "Continuing with next step of workflow assuming that the error was handled." +
                        "The target `next: exit` can be used to exit a handler.");
            }
            log.debug("Completed handler " + handlerParent.getDisplayName() +
                            (errorStepsMatching > 1 ? " with "+errorStepsMatching+" steps matching" : "") +
                    "; no next step indicated so proceeding to default next step");
        }

        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WorkflowErrorHandlingResult {
        Object next;
        Object output;
    }

    public static void handleErrorAtStep(Entity entity, WorkflowStepDefinition step, WorkflowStepInstanceExecutionContext currentStepInstance, Task<?> stepTaskThrowingError, BiConsumer<Object, Object> onFinish, Exception error, Integer errorStepIfNested) {
        if (!step.onError.isEmpty()) {
            if (errorStepIfNested!=null) {
                log.debug("Nested error handler running on "+stepTaskThrowingError+" to handle "+error);
            }
            try {
                Task<WorkflowErrorHandling.WorkflowErrorHandlingResult> stepErrorHandlerTask = WorkflowErrorHandling.createStepErrorHandlerTask(step, currentStepInstance, stepTaskThrowingError, error, errorStepIfNested);
                currentStepInstance.errorHandlerTaskId = stepErrorHandlerTask.getId();

                WorkflowErrorHandling.WorkflowErrorHandlingResult result = DynamicTasks.queue(stepErrorHandlerTask).getUnchecked();
                if (result!=null) {
                    if (errorStepIfNested==null && STEP_TARGET_NAME_FOR_EXIT.equals(result.next)) {
                        result.next = null;
                    }
                    onFinish.accept(result.output, result.next);
                } else {
                    // should never be null
                    logWarnOnExceptionOrDebugIfKnown(entity, error, "Error in step '" + stepTaskThrowingError.getDisplayName() + "', error handler did not apply so rethrowing: " + Exceptions.collapseText(error));
                    throw Exceptions.propagate(error);
                }

            } catch (Exception e2) {
                if (Exceptions.getCausalChain(e2).stream().anyMatch(e3 -> e3== error)) {
                    // is, or wraps, original error, don't need to log
                } else {
                    logWarnOnExceptionOrDebugIfKnown(entity, e2, "Error in step '" + stepTaskThrowingError.getDisplayName() + "' error handler for -- " + Exceptions.collapseText(error) + " -- threw another error (rethrowing): " + Exceptions.collapseText(e2));
                    log.debug("Full trace of original error was: " + error, error);
                }
                throw Exceptions.propagate(e2);
            } finally {
                if (errorStepIfNested==null) currentStepInstance.getWorkflowExectionContext().lastErrorHandlerOutput = null;
            }

        } else {
            logWarnOnExceptionOrDebugIfKnown(entity, error, "Error in step '" + stepTaskThrowingError.getDisplayName() + "', no error handler so rethrowing: " + Exceptions.collapseText(error));
            throw Exceptions.propagate(error);
        }
    }

    public static void logWarnOnExceptionOrDebugIfKnown(Entity entity, Exception e, String msg) {
        if (Exceptions.getFirstThrowableOfType(e, DanglingWorkflowException.class)!=null) { log.debug(msg); return; }
        if (Entities.isUnmanagingOrNoLongerManaged(entity)) { log.debug(msg); return; }
        log.warn(msg);
    }
}