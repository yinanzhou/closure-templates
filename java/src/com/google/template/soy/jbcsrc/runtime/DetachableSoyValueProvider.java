/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.jbcsrc.runtime;


import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;

/**
 * A special implementation of {@link SoyValueProvider} to use as a shared base class for the {@code
 * jbcsrc} implementations of the generated {@code LetValueNode} and {@code CallParamValueNode}
 * implementations.
 *
 * <p>This class resolves to a {@link SoyValue} and calls {@link SoyValue#render}. If you need to
 * resolve to a {@link SoyValueProvider} to call {@link SoyValueProvider#renderAndResolve}, use
 * {@link DetachableSoyValueProviderProvider} instead.
 */
public final class DetachableSoyValueProvider implements SoyValueProvider {
  /** A lambda-able interface for implementing a lazy value block. */
  @FunctionalInterface
  public interface Impl {
    /** Returns either a RenderResult meaning we aren't done or a SoyValue for our final value. */
    Object evaluate();
  }

  public static DetachableSoyValueProvider create(Impl impl) {
    return new DetachableSoyValueProvider(impl);
  }

  private Object implOrValue;

  private DetachableSoyValueProvider(Impl impl) {
    this.implOrValue = impl;
  }

  @Override
  public final SoyValue resolve() {
    JbcSrcRuntime.awaitProvider(this);
    return (SoyValue) implOrValue;
  }

  @Override
  public final RenderResult status() {
    var local = implOrValue;
    if (local instanceof SoyValue) {
      return RenderResult.done();
    }
    var result = ((Impl) local).evaluate();
    if (result instanceof RenderResult) {
      return (RenderResult) result;
    }
    implOrValue = (SoyValue) result;
    return RenderResult.done();
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable) throws IOException {
    RenderResult result = status();
    if (result.isDone()) {
      SoyValue resolved = resolve();
      if (resolved == null) {
        appendable.append("null");
      } else {
        resolved.render(appendable);
      }
    }
    return result;
  }
}
