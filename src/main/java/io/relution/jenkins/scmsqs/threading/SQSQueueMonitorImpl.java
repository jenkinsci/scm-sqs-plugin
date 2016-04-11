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

import com.amazonaws.services.sqs.AmazonSQS;
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


public class SQSQueueMonitorImpl implements SQSQueueMonitor {

    private final static String          ERROR_WRONG_QUEUE = "The specified listener is associated with another queue.";

    private final ExecutorService        executor;
    private final SQSFactory             factory;
    private final SQSQueue               queue;

    private int                          requestCount;
    private AmazonSQS                    client;

    private final Object                 listenersLock     = new Object();
    private final List<SQSQueueListener> listeners         = new ArrayList<>();

    private final AtomicBoolean          isRunning         = new AtomicBoolean();
    private volatile boolean             isShutDown;

    public SQSQueueMonitorImpl(final ExecutorService executor, final SQSFactory factory, final SQSQueue queue) {
        ThrowIf.isNull(executor, "executor");
        ThrowIf.isNull(factory, "factory");
        ThrowIf.isNull(queue, "queue");

        this.executor = executor;
        this.factory = factory;
        this.queue = queue;
    }

    @Override
    public boolean add(final SQSQueueListener listener) {
        ThrowIf.isNull(listener, "listener");
        ThrowIf.notEqual(listener.getQueueUuid(), this.queue.getUuid(), ERROR_WRONG_QUEUE);

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
        if (listener == null) {
            return false;
        }

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
            this.processMessages();

            if (!this.isShutDown) {
                this.executor.execute(this);
            }

        } catch (final com.amazonaws.services.sqs.model.QueueDoesNotExistException e) {
            Log.warning("Queue %s does not exist, monitor stopped", this.queue);
            this.isShutDown = true;

        } catch (final Exception e) {
            Log.severe(e, "Unknown error, monitor for queue %s stopped", this.queue);
            this.isShutDown = true;

        } finally {
            if (!this.isRunning.compareAndSet(true, false)) {
                Log.warning("Monitor for %s already stopped", this.queue);
            }
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
        this.client = this.factory.createSQS(this.queue);
        this.requestCount = 0;

        this.executor.execute(this);
    }

    private void processMessages() {
        final ReceiveMessageResult receiveResult = this.receiveMessage();

        if (this.isShutDown || receiveResult == null) {
            return;
        }

        final List<Message> messages = receiveResult.getMessages();

        if (this.notifyListeners(messages)) {
            this.deleteMessages(messages);
        }
    }

    private ReceiveMessageResult receiveMessage() {
        try {
            this.requestCount++;
            Log.fine("Send receive message request #%d for %s", this.requestCount, this.queue);

            final ReceiveMessageRequest request = this.factory.createReceiveMessageRequest(this.queue);
            return this.client.receiveMessage(request);

        } catch (final com.amazonaws.AmazonServiceException e) {
            Log.severe(e, "Failed to send receive message request for %s", this.queue);

        }
        return null;
    }

    private boolean notifyListeners(final List<Message> messages) {
        if (messages.isEmpty()) {
            Log.fine("Received no messages from %s", this.queue);
            return false;
        }

        Log.info("Received %d message(s) from %s", messages.size(), this.queue);
        final List<SQSQueueListener> listeners = this.getListeners();

        for (final SQSQueueListener listener : listeners) {
            listener.handleMessages(messages);
        }

        return true;
    }

    private void deleteMessages(final List<Message> messages) {
        final DeleteMessageBatchResult deleteResult = this.deleteMessageBatch(messages);

        if (deleteResult == null) {
            return;
        }

        final List<?> failed = deleteResult.getFailed();
        final List<?> success = deleteResult.getSuccessful();
        Log.info("Deleted %d message(s) (%d failed) from %s", success.size(), failed.size(), this.queue);
    }

    private DeleteMessageBatchResult deleteMessageBatch(final List<Message> messages) {
        try {
            final DeleteMessageBatchRequest request = this.factory.createDeleteMessageBatchRequest(this.queue, messages);
            Log.info("Send delete request for %d message(s) to %s", messages.size(), this.queue);
            return this.client.deleteMessageBatch(request);

        } catch (final com.amazonaws.AmazonServiceException e) {
            Log.severe(e, "Delete from %s failed", this.queue);

        }
        return null;
    }

    private List<SQSQueueListener> getListeners() {
        synchronized (this.listenersLock) {
            return new ArrayList<>(this.listeners);
        }
    }
}
