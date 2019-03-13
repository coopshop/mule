/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.execution;

import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.process.CompletionCallback;

import java.util.concurrent.CompletableFuture;

public class FutureCompletionCallback implements CompletionCallback<Object, Object> {

  private final CompletableFuture<Object> future;

  public FutureCompletionCallback(CompletableFuture<Object> future) {
    this.future = future;
  }

  @Override
  public void success(Result<Object, Object> result) {
    future.complete(result);
  }

  @Override
  public void error(Throwable e) {
    future.completeExceptionally(e);
  }
}
