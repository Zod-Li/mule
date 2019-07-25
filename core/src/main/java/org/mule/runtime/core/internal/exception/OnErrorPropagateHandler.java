/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.exception;

import static java.util.regex.Pattern.compile;
import static reactor.core.publisher.Mono.just;

import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.transaction.TransactionCoordination;
import org.mule.runtime.core.privileged.exception.MessageRedeliveredException;
import org.mule.runtime.core.privileged.exception.TemplateOnErrorHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.mule.runtime.core.privileged.transaction.TransactionAdapter;
import org.reactivestreams.Publisher;

/**
 * Handler that will propagate errors and rollback transactions. Replaces the rollback-exception-strategy from Mule 3.
 *
 * @since 4.0
 */
public class OnErrorPropagateHandler extends TemplateOnErrorHandler {

  private static final Pattern ERROR_HANDLER_LOCATION_PATTERN = compile(".*/.*/.*");

  @Override
  public boolean acceptsAll() {
    return errorTypeMatcher == null && when == null;
  }

  @Override
  protected Function<CoreEvent, CoreEvent> beforeRouting(Exception exception) {
    return event -> {
      event = super.beforeRouting(exception).apply(event);
      if (!isRedeliveryExhausted(exception) && isOwnedTransaction()) {
        rollback(exception);
      }
      return event;
    };
  }

  private boolean isTransactionInGlobalErrorHandler(TransactionAdapter transaction) {
    String transactionContainerName = transaction.getComponentLocation().get().getRootContainerName();
    return flowLocation.isPresent() && transactionContainerName.equals(flowLocation.get().getGlobalName());
  }

  private boolean isOwnedTransaction() {
    TransactionAdapter transaction = (TransactionAdapter) TransactionCoordination.getInstance().getTransaction();
    if (transaction == null || !transaction.getComponentLocation().isPresent()) {
      return false;
    }

    if (inDefaultErrorHandler()) {
      return defaultErrorHandlerOwnsTransaction(transaction);
    } else if (isTransactionInGlobalErrorHandler((transaction))) {
      // We are in a GlobalErrorHandler that is defined for the container (Flow or TryScope) that created the tx
      return true;
    } else if (flowLocation.isPresent()) {
      // We are in a Global Error Handler, which is not the one that created the Tx
      return false;
    } else {
      // We are in a simple scenario where the error handler's location ends with "/error-handler/1".
      // We cannot use the RootContainerLocation, since in case of nested TryScopes (the outer one creating the tx)
      // the RootContainerLocation will be the same for both, and we don't want the inner TryScope's OnErrorPropagate
      // to rollback the tx.
      String errorHandlerLocation = this.getLocation().getLocation();
      if (!ERROR_HANDLER_LOCATION_PATTERN.matcher(errorHandlerLocation).find()) {
        return sameRootContainerLocation(transaction);
      }
      String transactionLocation = transaction.getComponentLocation().get().getLocation();
      errorHandlerLocation = errorHandlerLocation.substring(0, errorHandlerLocation.lastIndexOf('/'));
      errorHandlerLocation = errorHandlerLocation.substring(0, errorHandlerLocation.lastIndexOf('/'));
      return (sameRootContainerLocation(transaction) && errorHandlerLocation.equals(transactionLocation));
    }
  }

  private boolean sameRootContainerLocation(TransactionAdapter transaction) {
    String transactionContainerName = transaction.getComponentLocation().get().getRootContainerName();
    return transactionContainerName.equals(this.getRootContainerLocation().getGlobalName());
  }

  private boolean inDefaultErrorHandler() {
    return getLocation() == null;
  }

  private boolean defaultErrorHandlerOwnsTransaction(TransactionAdapter transaction) {
    String transactionLocation = transaction.getComponentLocation().get().getLocation();
    if (flowLocation.isPresent()) {
      // We are in a default error handler for a TryScope, which must have been replicated to match the tx location
      // to rollback it
      return transactionLocation.equals(flowLocation.get().toString());
    } else {
      // We are in a default error handler of a Flow
      return sameRootContainerLocation(transaction);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TemplateOnErrorHandler duplicateFor(Location buildFor) {
    OnErrorPropagateHandler cpy = new OnErrorPropagateHandler();
    cpy.setFlowLocation(buildFor);
    cpy.setWhen(this.when);
    cpy.setHandleException(this.handleException);
    cpy.setErrorType(this.errorType);
    cpy.setMessageProcessors(this.getMessageProcessors());
    cpy.setEnableNotifications(this.isEnableNotifications());
    cpy.setLogException(this.logException);
    cpy.setNotificationFirer(this.notificationFirer);
    cpy.setAnnotations(this.getAnnotations());
    return cpy;
  }

  @Override
  protected List<Processor> getOwnedMessageProcessors() {
    return new ArrayList<>(super.getOwnedMessageProcessors());
  }

  private boolean isRedeliveryExhausted(Exception exception) {
    return (exception instanceof MessageRedeliveredException);
  }

  @Override
  protected Function<CoreEvent, Publisher<CoreEvent>> route(Exception exception) {
    if (isRedeliveryExhausted(exception)) {
      logger.info("Message redelivery exhausted. No redelivery exhausted actions configured. Message consumed.");
    } else {
      return super.route(exception);
    }
    return event -> just(event);
  }

}
