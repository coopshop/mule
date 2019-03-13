/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.execution;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.runtime.core.internal.util.rx.ImmediateScheduler.IMMEDIATE_SCHEDULER;
import static org.mule.runtime.module.extension.api.runtime.privileged.ExecutionContextProperties.COMPLETION_CALLBACK_CONTEXT_PARAM;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.construct.ConstructModel;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.retry.policy.RetryPolicyTemplate;
import org.mule.runtime.core.api.streaming.CursorProviderFactory;
import org.mule.runtime.core.api.streaming.StreamingManager;
import org.mule.runtime.extension.api.runtime.operation.CompletableComponentExecutor;
import org.mule.runtime.extension.api.runtime.operation.CompletableComponentExecutorFactory;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.process.CompletionCallback;
import org.mule.runtime.module.extension.api.runtime.privileged.ExecutionContextAdapter;
import org.mule.runtime.module.extension.internal.loader.java.property.ImplementingMethodModelProperty;
import org.mule.runtime.module.extension.internal.runtime.DefaultExecutionContext;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class OperationExecutorFactoryWrapperTestCase extends AbstractMuleTestCase {

  @Mock(answer = RETURNS_DEEP_STUBS)
  private CompletableComponentExecutorFactory executorFactory;

  @Mock
  private CompletableComponentExecutor executor;

  private ExecutionContextAdapter ctx;
  private CompletableOperationExecutorFactoryWrapper wrapper;

  @Before
  public void before() {
    when(executor.execute(any())).thenReturn(completedFuture(null));
    setupExecutorFactory();
    ctx = spy(new DefaultExecutionContext(mock(ExtensionModel.class),
                                          empty(),
                                          emptyMap(),
                                          mock(ComponentModel.class),
                                          mock(CoreEvent.class),
                                          mock(CursorProviderFactory.class),
                                          mock(StreamingManager.class),
                                          mock(Component.class),
                                          mock(RetryPolicyTemplate.class),
                                          IMMEDIATE_SCHEDULER,
                                          mock(MuleContext.class)));

    wrapper = new CompletableOperationExecutorFactoryWrapper(executorFactory, emptyList());
  }

  @Test
  public void javaBlockingOperation() throws Exception {
    setupJava();
    assertOperation(true, true);
  }

  @Test
  public void javaNonBlockingOperation() throws Exception {
    setupJava();
    when(executor.execute(any())).thenAnswer((Answer<CompletableFuture<Object>>) invocationOnMock -> {
      ExecutionContextAdapter ctx = (ExecutionContextAdapter) invocationOnMock.getArguments()[0];
      ((CompletionCallback) ctx.getVariable(COMPLETION_CALLBACK_CONTEXT_PARAM)).success(mock(Result.class));
      return completedFuture(null);
    });
    assertOperation(true, false);
  }

  @Test
  public void nonJavaNonBlockingOperation() throws Exception {
    assertOperation(false, false);
  }

  @Test
  public void nonJavaBlockingOperation() throws Exception {
    assertOperation(false, true);
  }

  @Test
  public void construct() throws Exception {
    final ConstructModel constructModel = mock(ConstructModel.class);
    when(constructModel.getModelProperty(ImplementingMethodModelProperty.class)).thenReturn(empty());
    wrapper.createExecutor(constructModel, emptyMap()).execute(ctx);
    verify(ctx, never()).setVariable(eq(COMPLETION_CALLBACK_CONTEXT_PARAM), any());
  }

  private void assertOperation(boolean java, boolean blocking) throws Exception {
    wrapper.createExecutor(mockOperation(blocking), emptyMap()).execute(ctx).get();
    verify(executor).execute(ctx);
    VerificationMode verificationMode = java && !blocking ? times(1) : never();
    verify(ctx, verificationMode).setVariable(eq(COMPLETION_CALLBACK_CONTEXT_PARAM), any());
  }

  private OperationModel mockOperation(boolean blocking) {
    OperationModel operationModel = mock(OperationModel.class);
    when(operationModel.isBlocking()).thenReturn(blocking);

    return operationModel;
  }

  private void setupExecutorFactory() {
    when(executorFactory.createExecutor(any(), any())).thenReturn(executor);
  }

  private void setupJava() {
    executor = mock(ReflectiveMethodOperationExecutor.class);
    when(executor.execute(any())).thenReturn(completedFuture(null));
    setupExecutorFactory();
  }
}
