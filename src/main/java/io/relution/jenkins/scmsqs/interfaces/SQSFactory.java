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

package io.relution.jenkins.scmsqs.interfaces;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.concurrent.ExecutorService;


/**
 * Interface definition for factories that can create {@link SQSQueueMonitor} instances and related
 * classes. The instances returned by a factory implementation can be used to monitor a particular
 * {@link SQSQueue} by polling the queue for new messages.
 */
public interface SQSFactory {

    /**
     * Returns a new Amazon SQS instance that can be used to access the specified queue.
     * @param queue The {@link SQSQueue} for which to create a client.
     * @return A new instance of an {@link AmazonSQS} that is suitable for synchronous access to
     * the specified queue.
     */
    AmazonSQS createSQS(final SQSQueue queue);

    /**
     * Returns a new Amazon SQS instance that can be used to access the specified queue.
     * @param queue The {@link SQSQueue} for which to create a client.
     * @return A new instance of an {@link AmazonSQSAsync} that is suitable for asynchronous access
     * to the specified queue.
     */
    AmazonSQSAsync createSQSAsync(final SQSQueue queue);

    /**
     * Returns a new monitor instance that can be used to poll the specified queue for new
     * messages.
     * @param executor The {@link ExecutorService} used to execute the monitor.
     * @param queue The {@link SQSQueue} for which to create a monitor.
     * @return A new {@link SQSQueueMonitor} instance suitable for monitoring the specified queue.
     */
    SQSQueueMonitor createMonitor(final ExecutorService executor, final SQSQueue queue);

    /**
     * Returns a new request that can be used to receive messages from the specified queue.
     * @param queue The {@link SQSQueue} for which to create the request.
     * @return A {@link ReceiveMessageRequest} that can be used to request messages from the
     * specified queue.
     */
    ReceiveMessageRequest createReceiveMessageRequest(final SQSQueue queue);

    /**
     * Returns a new request that can be used to delete previously received messages from the
     * specified queue.
     * <p>
     * The specified messages must have been received by a previous receive message request to
     * the same queue.
     * @param queue The {@link SQSQueue} from which to delete the specified messages.
     * @param messages The collection of {@link Message}s to delete.
     * @return A {@link DeleteMessageBatchRequest} that can be used to delete messages from the
     * specified queue.
     */
    DeleteMessageBatchRequest createDeleteMessageBatchRequest(final SQSQueue queue, final List<Message> messages);
}
