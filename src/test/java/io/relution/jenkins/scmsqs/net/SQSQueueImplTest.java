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

package io.relution.jenkins.scmsqs.net;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.BatchResultErrorEntry;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResult;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResultEntry;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.relution.jenkins.scmsqs.interfaces.SQSFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;


public class SQSQueueImplTest {

    @Mock
    private SQSFactory                factory;

    @Mock
    private AmazonSQSAsync            sqs;

    @Mock
    private SQSQueue                  queue;

    @Mock
    private ReceiveMessageRequest     receiveRequest;

    @Mock
    private ReceiveMessageResult      receiveResult;

    @Mock
    private DeleteMessageBatchRequest deleteRequest;

    @Mock
    private DeleteMessageBatchResult  deleteResult;

    private final List<Message>       messages = new ArrayList<>();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(this.factory.createSQS(Matchers.any(SQSQueue.class))).thenReturn(this.sqs);
        Mockito.when(this.factory.createSQSAsync(Matchers.any(SQSQueue.class))).thenReturn(this.sqs);
        Mockito.when(this.factory.createReceiveMessageRequest(this.queue)).thenReturn(this.receiveRequest);
        Mockito.when(this.factory.createDeleteMessageBatchRequest(this.queue, this.messages)).thenReturn(this.deleteRequest);

        final Message message = new Message();
        this.messages.add(message);

        Mockito.when(this.sqs.receiveMessage(this.receiveRequest)).thenReturn(this.receiveResult);
        Mockito.when(this.sqs.deleteMessageBatch(this.deleteRequest)).thenReturn(this.deleteResult);

        Mockito.when(this.receiveResult.getMessages()).thenReturn(this.messages);

        final List<DeleteMessageBatchResultEntry> result = Collections.emptyList();
        final List<BatchResultErrorEntry> errors = Collections.emptyList();
        Mockito.when(this.deleteResult.getSuccessful()).thenReturn(result);
        Mockito.when(this.deleteResult.getFailed()).thenReturn(errors);
    }

    @Test
    public void shouldReturnMessages() {
        final SQSChannel channel = new SQSChannelImpl(this.sqs, this.queue, this.factory);

        final List<Message> messages = channel.getMessages();

        assertThat(messages).isSameAs(this.messages);
    }

    @Test
    public void shouldDeleteMessages() {
        final SQSChannel channel = new SQSChannelImpl(this.sqs, this.queue, this.factory);

        channel.deleteMessages(this.messages);

        Mockito.verify(this.factory).createDeleteMessageBatchRequest(this.queue, this.messages);
        Mockito.verify(this.sqs).deleteMessageBatch(this.deleteRequest);
    }
}
