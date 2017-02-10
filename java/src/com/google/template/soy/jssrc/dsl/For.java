/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.jssrc.dsl;

import static com.google.template.soy.jssrc.dsl.OutputContext.EXPRESSION;

import com.google.auto.value.AutoValue;

/** Represents a {@code for} statement. */
@AutoValue
abstract class For extends CodeChunk {

  abstract String localVar();

  abstract CodeChunk.WithValue initial();

  abstract CodeChunk.WithValue limit();

  abstract CodeChunk.WithValue increment();

  abstract CodeChunk body();

  static For create(
      String localVar,
      CodeChunk.WithValue initial,
      CodeChunk.WithValue limit,
      CodeChunk.WithValue increment,
      CodeChunk body) {
    return new AutoValue_For(localVar, initial, limit, increment, body);
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    initial().collectRequires(collector);
    limit().collectRequires(collector);
    increment().collectRequires(collector);
    body().collectRequires(collector);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(initial())
        .appendInitialStatements(limit())
        .appendInitialStatements(increment());

    ctx.append("for (var " + localVar() + " = ")
        .appendOutputExpression(initial(), EXPRESSION)
        .append("; " + localVar() + " < ")
        .appendOutputExpression(limit(), EXPRESSION)
        .append("; " + localVar() + " += ")
        .appendOutputExpression(increment(), EXPRESSION)
        .append(") ");

    try (FormattingContext ignored = ctx.enterBlock()) {
      ctx.appendAll(body());
    }
  }
}
