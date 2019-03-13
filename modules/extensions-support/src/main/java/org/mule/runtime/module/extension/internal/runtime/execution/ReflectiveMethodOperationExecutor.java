/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.execution;

import static java.util.Collections.emptyMap;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.disposeIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.startIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.stopIfNeeded;
import static org.mule.runtime.core.api.rx.Exceptions.wrapFatal;
import static org.mule.runtime.module.extension.api.runtime.privileged.ExecutionContextProperties.COMPLETION_CALLBACK_CONTEXT_PARAM;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Lifecycle;
import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.extension.api.runtime.operation.CompletableComponentExecutor;
import org.mule.runtime.extension.api.runtime.operation.ExecutionContext;
import org.mule.runtime.extension.api.runtime.process.CompletionCallback;
import org.mule.runtime.module.extension.api.runtime.privileged.ExecutionContextAdapter;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;

/**
 * Implementation of {@link ReflectiveMethodOperationExecutor} which works by using reflection to invoke a method from a class.
 *
 * @since 4.2.0
 */
public class ReflectiveMethodOperationExecutor<M extends ComponentModel>
    implements CompletableComponentExecutor<M>, OperationArgumentResolverFactory<M>, MuleContextAware, Lifecycle {

  private static final Logger LOGGER = getLogger(
                                                 ReflectiveMethodOperationExecutor.class);

  private final ReflectiveMethodComponentExecutor<M> executor;
  private MuleContext muleContext;

  public ReflectiveMethodOperationExecutor(M operationModel, Method operationMethod, Object operationInstance) {
    executor =
        new ReflectiveMethodComponentExecutor<>(operationModel.getParameterGroupModels(), operationMethod, operationInstance);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletableFuture<Object> execute(ExecutionContext<M> executionContext) {
    CompletableFuture<Object> result = new CompletableFuture<>();
    try {
      result.complete(executor.execute(executionContext));
    } catch (Exception e) {
      result.completeExceptionally(handleError(e, (ExecutionContextAdapter<M>) executionContext));
    } catch (Throwable t) {
      result.completeExceptionally(handleError(wrapFatal(t), (ExecutionContextAdapter<M>) executionContext));
    }

    return result;
  }

  private Throwable handleError(Throwable t, ExecutionContextAdapter<M> executionContext) {
    CompletionCallback completionCallback = executionContext.getVariable(COMPLETION_CALLBACK_CONTEXT_PARAM);
    if (completionCallback != null) {
      if (t instanceof Exception) {
        completionCallback.error(t);
      } else {
        completionCallback.error(new MuleRuntimeException(t));
      }
    }

    return t;
  }

  @Override
  public void initialise() throws InitialisationException {
    initialiseIfNeeded(executor, true, muleContext);
  }

  @Override
  public void start() throws MuleException {
    startIfNeeded(executor);
  }

  @Override
  public void stop() throws MuleException {
    stopIfNeeded(executor);
  }

  @Override
  public void dispose() {
    disposeIfNeeded(executor, LOGGER);
  }

  @Override
  public void setMuleContext(MuleContext context) {
    muleContext = context;
    executor.setMuleContext(muleContext);
  }

  @Override
  public Function<ExecutionContext<M>, Map<String, Object>> createArgumentResolver(M operationModel) {
    return executor instanceof OperationArgumentResolverFactory
        ? executor.createArgumentResolver(operationModel)
        : ec -> emptyMap();
  }
}
