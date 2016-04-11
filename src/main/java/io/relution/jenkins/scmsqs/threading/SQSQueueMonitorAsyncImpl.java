/*
 * Copyright 2016 M-Way Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.relution.jenkins.scmsqs.threading;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.relution.jenkins.scmsqs.interfaces.SQSFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueListener;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitor;
import io.relution.jenkins.scmsqs.logging.Log;
import io.relution.jenkins.scmsqs.util.ThrowIf;


public class SQSQueueMonitorAsyncImpl implements SQSQueueMonitor {

    private final ReceiveAsyncHandler    receiveHandler;
    private final DeleteAsyncHandler     deleteHandler;

    private final ExecutorService        executor;
    private final SQSFactory             factory;
    private final SQSQueue               queue;

    private int                          requestCount;
    private AmazonSQSAsync               client;

    private final Object                 listenersLock = new Object();
    private final List<SQSQueueListener> listeners     = new ArrayList<>();

    private final AtomicBoolean          isRunning     = new AtomicBoolean();
    private volatile boolean             isShutDown;

    public SQSQueueMonitorAsyncImpl(final ExecutorService executor, final SQSFactory factory, final SQSQueue queue) {
        ThrowIf.isNull(executor, "executor");
        ThrowIf.isNull(factory, "factory");
        ThrowIf.isNull(queue, "queue");

        this.receiveHandler = new ReceiveAsyncHandler(this);
        this.deleteHandler = new DeleteAsyncHandler(this);

        this.executor = executor;
        this.factory = factory;
        this.queue = queue;
    }

    @Override
    public boolean add(final SQSQueueListener listener) {
        synchronized (this.listenersLock) {
            if (this.listeners.add(listener) && this.listeners.size() == 1) {
                this.isShutDown = false;
                this.execute();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean remove(final SQSQueueListener listener) {
        synchronized (this.listenersLock) {
            if (this.listeners.remove(listener) && this.listeners.isEmpty()) {
                this.shutDown();
                return true;
            }
        }
        return false;
    }

    @Override
    public void run() {
        try {
            if (this.isShutDown) {
                return;
            }

            if (!this.isRunning.compareAndSet(false, true)) {
                Log.warning("Monitor for %s already started", this.queue);
                return;
            }

            Log.fine("Start synchronous monitor for %s", this.queue);
            this.receiveMessageAsync();

        } catch (final com.amazonaws.services.sqs.model.QueueDoesNotExistException e) {
            Log.warning("Queue %s does not exist, monitor stopped", this.queue);
            this.isShutDown = true;

        } catch (final Exception e) {
            Log.severe(e, "Unknown error, monitor for queue %s stopped", this.queue);
            this.isShutDown = true;

        }
    }

    @Override
    public void shutDown() {
        Log.info("Shut down monitor for %s", this.queue);
        this.isShutDown = true;
    }

    @Override
    public boolean isShutDown() {
        return this.isShutDown;
    }

    private void execute() {
        this.client = this.factory.createSQSAsync(this.queue);
        this.requestCount = 0;

        this.executor.execute(this);
    }

    private void onShutDown() {
        if (!this.isRunning.compareAndSet(true, false)) {
            Log.warning("Monitor for %s already stopped", this.queue);
        }
        this.isShutDown = true;
    }

    private void receiveMessageAsync() {
        if (this.isShutDown) {
            Log.info("Already shut down, will not send requests for %s", this.queue);
            this.onShutDown();
            return;
        }

        try {
            this.requestCount++;
            Log.fine("Send async receive message request #%d for %s", this.requestCount, this.queue);

            final ReceiveMessageRequest request = this.factory.createReceiveMessageRequest(this.queue);
            this.client.receiveMessageAsync(request, this.receiveHandler);

        } catch (final com.amazonaws.AmazonServiceException e) {
            Log.severe(e, "Failed to send async receive message request for %s", this.queue);
        }
    }

    private void notifyListeners(final List<Message> messages) {
        if (this.isShutDown) {
            Log.info("Already shut down, will not notify listeners for %s", this.queue);
            this.onShutDown();
            return;
        }

        final List<SQSQueueListener> listeners = this.getListeners();

        for (final SQSQueueListener listener : listeners) {
            listener.handleMessages(messages);
        }
    }

    private void deleteMessageBatchAsync(final List<Message> messages) {
        if (this.isShutDown) {
            Log.info("Already shut down, will not delete messages for %s", this.queue);
            this.onShutDown();
            return;
        }

        final DeleteMessageBatchRequest request = this.factory.createDeleteMessageBatchRequest(this.queue, messages);
        Log.info("Send async delete request for %d message(s) to %s", messages.size(), this.queue);
        this.client.deleteMessageBatchAsync(request, this.deleteHandler);
    }

    private void onReceiveMessageError(final Exception exception) {
        if (exception instanceof com.amazonaws.services.sqs.model.QueueDoesNotExistException) {
            Log.severe("Queue %s does not exist, stopping monitor", this.queue);
            this.onShutDown();
            return;
        }

        Log.severe(exception, "Request from %s failed", this.queue);
        this.receiveMessageAsync();
    }

    private void onReceiveMessageSuccess(final ReceiveMessageRequest request, final ReceiveMessageResult result) {
        final List<Message> messages = result.getMessages();

        if (messages.isEmpty()) {
            Log.fine("Received no messages from %s", this.queue);
            this.receiveMessageAsync();
        } else {
            Log.info("Received %d message(s) from %s", messages.size(), this.queue);
            this.notifyListeners(messages);
            this.deleteMessageBatchAsync(messages);
        }
    }

    private void onDeleteMessageError(final Exception exception) {
        if (exception instanceof com.amazonaws.services.sqs.model.QueueDoesNotExistException) {
            Log.severe("Queue %s does not exist, stopping monitor", this.queue);
            this.onShutDown();
            return;
        }

        Log.severe(exception, "Delete from %s failed", this.queue);
        this.receiveMessageAsync();
    }

    private void onDeleteMessageSuccess(final DeleteMessageBatchRequest request, final DeleteMessageBatchResult result) {
        final List<?> failed = result.getFailed();
        final List<?> success = result.getSuccessful();
        Log.info("Deleted %d message(s) (%d failed) from %s", success.size(), failed.size(), this.queue);

        this.receiveMessageAsync();
    }

    private List<SQSQueueListener> getListeners() {
        synchronized (this.listenersLock) {
            return new ArrayList<>(this.listeners);
        }
    }

    private static class ReceiveAsyncHandler implements AsyncHandler<ReceiveMessageRequest, ReceiveMessageResult> {

        private final SQSQueueMonitorAsyncImpl monitor;

        public ReceiveAsyncHandler(final SQSQueueMonitorAsyncImpl monitor) {
            this.monitor = monitor;
        }

        @Override
        public void onError(final Exception exception) {
            this.monitor.onReceiveMessageError(exception);
        }

        @Override
        public void onSuccess(final ReceiveMessageRequest request, final ReceiveMessageResult result) {
            this.monitor.onReceiveMessageSuccess(request, result);
        }
    }

    private static class DeleteAsyncHandler implements AsyncHandler<DeleteMessageBatchRequest, DeleteMessageBatchResult> {

        private final SQSQueueMonitorAsyncImpl monitor;

        public DeleteAsyncHandler(final SQSQueueMonitorAsyncImpl monitor) {
            this.monitor = monitor;
        }

        @Override
        public void onError(final Exception exception) {
            this.monitor.onDeleteMessageError(exception);
        }

        @Override
        public void onSuccess(final DeleteMessageBatchRequest request, final DeleteMessageBatchResult result) {
            this.monitor.onDeleteMessageSuccess(request, result);
        }
    }
}
