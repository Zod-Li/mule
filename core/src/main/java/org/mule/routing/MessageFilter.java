/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.routing;

import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.processor.InterceptingMessageProcessor;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.routing.filter.Filter;
import org.mule.api.routing.filter.FilterUnacceptedException;
import org.mule.config.i18n.CoreMessages;
import org.mule.processor.AbstractFilteringMessageProcessor;

/**
 * Implementation of {@link InterceptingMessageProcessor} that filters message flow using a {@link Filter}. Is
 * the filter accepts the message then message flow continues to the next message processor. If the filter
 * does not accept the message processor and a message processor is configured for handling unaccepted message
 * then this will be invoked, otherwise <code>null</code> will be returned.
 * <p>
 * <b>EIP Reference:</b> {@link http://www.eaipatterns.com/Filter.html}
 */
public class MessageFilter extends AbstractFilteringMessageProcessor
{
    protected Filter filter;
    protected MessageProcessor unacceptedMessageProcessor;
    protected boolean throwOnUnaccepted;

    public MessageFilter()
    {
    }

    public MessageFilter(Filter filter)
    {
        this.filter = filter;
    }

    /**
     * @param filter
     * @param messageProcessor used to handler unaccepted messages
     */
    public MessageFilter(Filter filter, MessageProcessor messageProcessor)
    {
        this.filter = filter;
    }

    @Override
    protected boolean accept(MuleEvent event)
    {
        if (filter == null)
        {
            return true;
        }

        if (event != null)
        {
            return filter.accept(event.getMessage());
        }
        else
        {
            return false;
        }
    }

    protected MuleEvent handleUnaccepted(MuleEvent event) throws MuleException
    {
        if (unacceptedMessageProcessor == null)
        {
            super.handleUnaccepted(event);
        }
        else
        {
            unacceptedMessageProcessor.process(event);
        }
        if (throwOnUnaccepted)
        {
            throw new FilterUnacceptedException(CoreMessages.messageRejectedByFilter(), event, filter);   
        }
        return null;
    }
    
    /**
     * The <code>MessageProcessor</code> that should be used to handle messaged that are not accepted by the
     * filter.
     * 
     * @param unacceptedMessageProcessor
     */
    public void setUnacceptedMessageProcessor(MessageProcessor unacceptedMessageProcessor)
    {
        this.unacceptedMessageProcessor = unacceptedMessageProcessor;
    }

    public MessageProcessor getUnacceptedMessageProcessor()
    {
        return unacceptedMessageProcessor;
    }

    public Filter getFilter()
    {
        return filter;
    }

    public void setFilter(Filter filter)
    {
        this.filter = filter;
    }

    public void setThrowOnUnaccepted(boolean throwOnUnaccepted)
    {
        this.throwOnUnaccepted = throwOnUnaccepted;
    }
    
    @Override
    public String toString()
    {
        return filter.getClass().getName() + " (wrapped by " + this.getClass().getSimpleName() + ")";
    }
}
