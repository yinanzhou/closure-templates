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


import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A special implementation of {@link SoyValueProvider} to use as a shared base class for the {@code
 * jbcsrc} implementations of the generated {@code LetContentNode} and {@code CallParamContentNode}
 * implementations.
 */
public final class DetachableContentProvider implements SoyValueProvider {

  /** A lambda-able interface for implementing a lazy content block. */
  @FunctionalInterface
  public interface Impl {
    RenderResult render(LoggingAdvisingAppendable appendable) throws IOException;
  }

  public static DetachableContentProvider create(Impl impl) {
    return new DetachableContentProvider(impl);
  }

  // Will be either a SanitizedContent or a StringData.
  private SoyValue resolvedValue;
  private BufferingAppendable buffer;

  // Will be either an LoggingAdvisingAppendable.BufferingAppendable or a TeeAdvisingAppendable
  // depending on whether we are being resolved via 'status()' or via 'renderAndResolve()'
  private LoggingAdvisingAppendable builder;

  private Impl impl;

  private DetachableContentProvider(Impl impl) {
    this.impl = impl;
  }

  @Override
  public final SoyValue resolve() {
    JbcSrcRuntime.awaitProvider(this);
    return getResolvedValue();
  }

  @Override
  public final RenderResult status() {
    if (isDone()) {
      return RenderResult.done();
    }
    LoggingAdvisingAppendable.BufferingAppendable currentBuilder =
        (LoggingAdvisingAppendable.BufferingAppendable) builder;
    if (currentBuilder == null) {
      builder = currentBuilder = LoggingAdvisingAppendable.buffering();
    }
    RenderResult result;
    try {
      result = impl.render(currentBuilder);
    } catch (IOException ioe) {
      throw new AssertionError("impossible", ioe);
    }
    if (result.isDone()) {
      buffer = currentBuilder;
      builder = null;
      impl = null;
    }
    return result;
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable) throws IOException {
    if (isDone()) {
      buffer.replayOn(appendable);
      return RenderResult.done();
    }

    TeeAdvisingAppendable currentBuilder = (TeeAdvisingAppendable) builder;
    if (currentBuilder == null) {
      builder = currentBuilder = new TeeAdvisingAppendable(appendable);
    }
    RenderResult result = impl.render(currentBuilder);
    if (result.isDone()) {
      buffer = currentBuilder.buffer;
      builder = null;
      impl = null;
    }
    return result;
  }

  private boolean isDone() {
    return resolvedValue != null || buffer != null;
  }

  private SoyValue getResolvedValue() {
    SoyValue local = resolvedValue;
    if (local == null) {
      if (buffer != null) {
        // This drops logs, but that is sometimes necessary.  We should make sure this only happens
        // when it has to by making sure that renderAndResolve is used for all printing usecases
        local = buffer.getAsSoyValue();
        resolvedValue = local;
      } else {
        throw new AssertionError("getResolvedValue() should only be called if the value isDone.");
      }
    }
    return local;
  }

  /**
   * An {@link AdvisingAppendable} that forwards to a delegate appendable but also saves all the
   * same forwarded content into a buffer.
   *
   * <p>See: <a href="http://en.wikipedia.org/wiki/Tee_%28command%29">Tee command for the unix
   * command on which this is based.
   */
  private static final class TeeAdvisingAppendable extends LoggingAdvisingAppendable {
    final BufferingAppendable buffer = LoggingAdvisingAppendable.buffering();
    final LoggingAdvisingAppendable delegate;

    TeeAdvisingAppendable(LoggingAdvisingAppendable delegate) {
      this.delegate = delegate;
    }

    @Override
    protected void notifyKindAndDirectionality(ContentKind kind, @Nullable Dir contentDir)
        throws IOException {
      delegate.setKindAndDirectionality(kind, contentDir);
      buffer.setKindAndDirectionality(kind, contentDir);
    }

    @CanIgnoreReturnValue
    @Override
    public TeeAdvisingAppendable append(CharSequence csq) throws IOException {
      delegate.append(csq);
      buffer.append(csq);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public TeeAdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
      delegate.append(csq, start, end);
      buffer.append(csq, start, end);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public TeeAdvisingAppendable append(char c) throws IOException {
      delegate.append(c);
      buffer.append(c);
      return this;
    }

    @Override
    public boolean softLimitReached() {
      return delegate.softLimitReached();
    }

    @Override
    public void flushBuffers(int depth) {
      // This is a 'root' appendable so while we have a delegate there should never be a case where
      // we need to be flushed through.
      throw new AssertionError("should not be called");
    }

    @Override
    public String toString() {
      return buffer.toString();
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      delegate.enterLoggableElement(statement);
      buffer.enterLoggableElement(statement);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      delegate.exitLoggableElement();
      buffer.exitLoggableElement();
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      delegate.appendLoggingFunctionInvocation(funCall, escapers);
      buffer.appendLoggingFunctionInvocation(funCall, escapers);
      return this;
    }
  }
}
