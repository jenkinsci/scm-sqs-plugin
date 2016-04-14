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

package io.relution.jenkins.scmsqs.factories;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.amazonaws.services.sqs.buffered.QueueBufferConfig;
import com.google.inject.Inject;

import java.util.concurrent.ExecutorService;

import io.relution.jenkins.scmsqs.interfaces.SQSFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitor;
import io.relution.jenkins.scmsqs.net.RequestFactory;
import io.relution.jenkins.scmsqs.net.SQSChannel;
import io.relution.jenkins.scmsqs.net.SQSChannelImpl;
import io.relution.jenkins.scmsqs.threading.SQSQueueMonitorImpl;


public class SQSFactoryImpl implements SQSFactory {

    private final ExecutorService executor;
    private final RequestFactory  factory;

    @Inject
    public SQSFactoryImpl(final ExecutorService executor, final RequestFactory factory) {
        this.executor = executor;
        this.factory = factory;
    }

    @Override
    public AmazonSQS createSQS(final SQSQueue queue) {
        final ClientConfiguration clientConfiguration = this.getClientConfiguration(queue);
        final AmazonSQS sqs = new AmazonSQSClient(queue, clientConfiguration);

        if (queue.getEndpoint() != null) {
            sqs.setEndpoint(queue.getEndpoint());
        }

        return sqs;
    }

    @Override
    public AmazonSQSAsync createSQSAsync(final SQSQueue queue) {
        final ClientConfiguration clientConfiguration = this.getClientConfiguration(queue);
        final AmazonSQSAsyncClient sqsAsync = new AmazonSQSAsyncClient(queue, clientConfiguration, this.executor);

        if (queue.getEndpoint() != null) {
            sqsAsync.setEndpoint(queue.getEndpoint());
        }

        final QueueBufferConfig queueBufferConfig = this.getQueueBufferConfig(queue);
        final AmazonSQSBufferedAsyncClient sqsBufferedAsync = new AmazonSQSBufferedAsyncClient(sqsAsync, queueBufferConfig);

        return sqsBufferedAsync;
    }

    @Override
    public SQSChannel createChannel(final SQSQueue queue) {
        final AmazonSQS sqs = this.createSQS(queue);
        return new SQSChannelImpl(sqs, queue, this.factory);
    }

    @Override
    public SQSQueueMonitor createMonitor(final ExecutorService executor, final SQSQueue queue) {
        final SQSChannel channel = this.createChannel(queue);
        return new SQSQueueMonitorImpl(executor, channel);
    }

    private ClientConfiguration getClientConfiguration(final SQSQueue queue) {
        final ClientConfiguration config = new ClientConfiguration();

        // TODO Add support for proxy

        return config;
    }

    private QueueBufferConfig getQueueBufferConfig(final SQSQueue queue) {
        final QueueBufferConfig config = new QueueBufferConfig();

        // TODO Add more options

        config.setLongPollWaitTimeoutSeconds(queue.getWaitTimeSeconds());
        config.setLongPoll(true);

        return config;
    }
}
