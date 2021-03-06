/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package net.consensys.orion.http.server.vertx;

import net.consensys.orion.exception.OrionErrorCode;
import net.consensys.orion.exception.OrionException;
import net.consensys.orion.http.server.HttpContentType;
import net.consensys.orion.http.server.HttpError;
import net.consensys.orion.utils.Serializer;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HttpErrorHandler implements Handler<RoutingContext> {
  private static final Logger log = LogManager.getLogger();

  public HttpErrorHandler() {}

  @Override
  public void handle(final RoutingContext failureContext) {
    final int statusCode = statusCode(failureContext);
    final HttpServerResponse response = failureContext.response().setStatusCode(statusCode);

    if (hasError(failureContext)) {
      final Buffer buffer = errorJson(failureContext);
      response.putHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.JSON.httpHeaderValue).end(buffer);
    } else {
      response.end();
    }
  }

  private Buffer errorJson(final RoutingContext failureContext) {
    final OrionErrorCode orionError = orionError(failureContext.failure());
    final HttpError httpError = new HttpError(orionError);
    final Buffer buffer = Buffer.buffer(Serializer.serialize(HttpContentType.JSON, httpError));

    if (failureContext.statusCode() == 404) {
      // To reduce noise 404s are only logged as debug. A 404 is returned for the common use case where Besu receives a
      // private marker transaction for a privacy group that this Orion is not part of.
      log.debug("Not Found Error: \"" + orionError + "\" Body: \"" + failureContext.getBodyAsString() + "\"");
    } else {
      log.error(failureContext.currentRoute().getPath() + " failed " + httpError, failureContext.failure());
    }
    return buffer;
  }

  /**
   * Status code may not have been set (left as a negative number), in which case assume server side issue.
   */
  private int statusCode(final RoutingContext failureContext) {
    return failureContext.statusCode() < 0 ? HttpResponseStatus.INTERNAL_SERVER_ERROR.code()
        : failureContext.statusCode();
  }

  private boolean hasError(final RoutingContext failureContext) {
    return failureContext.failure() != null;
  }

  private OrionErrorCode orionError(final Throwable failure) {

    if (failure instanceof OrionException) {
      log.debug(failure);
      return ((OrionException) failure).code();
    }

    log.warn("Non OrionException, default unmapped code used", failure);
    return OrionErrorCode.UNMAPPED;
  }
}
