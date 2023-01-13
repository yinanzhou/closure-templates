/*
 * Copyright 2018 Google Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.Optional;
import java.util.function.Consumer;

/** Represents a string literal expression. */
@AutoValue
@Immutable
abstract class StringLiteral extends Expression {

  static Expression create(String literalValue) {
    return new AutoValue_StringLiteral(/* initialStatements= */ ImmutableList.of(), literalValue);
  }

  abstract String literalValue();

  @Override
  public boolean isCheap() {
    return true;
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {}

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append(quoteAndEscape(literalValue(), ctx.getFormatOptions()));
  }

  @Override
  public JsExpr singleExprOrName(FormatOptions formatOptions) {
    return new JsExpr(quoteAndEscape(literalValue(), formatOptions), Integer.MAX_VALUE);
  }

  private static String quoteAndEscape(String literal, FormatOptions formatOptions) {
    // Escape non-ASCII characters since browsers are inconsistent in how they interpret utf-8 in
    // JS source files.
    String escaped =
        BaseUtils.escapeToWrappedSoyString(
            literal, formatOptions.htmlEscapeStrings(), QuoteStyle.SINGLE);

    // </script in a JavaScript string will end the current script tag in most browsers. Escape the
    // forward slash in the string to get around this issue.
    return escaped.replace("</script", "<\\/script");
  }

  @Override
  public Optional<String> asStringLiteral() {
    return Optional.of(literalValue());
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {}
}
