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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutorService;

import io.relution.jenkins.scmsqs.interfaces.SQSFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueListener;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitor;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitorScheduler;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueProvider;


public class SQSQueueMonitorSchedulerImplTest {

    @Mock
    private ExecutorService          executor;

    @Mock
    private SQSQueueProvider         provider;

    @Mock
    private SQSFactory               factory;

    @Mock
    private SQSQueueMonitor          monitorA;

    @Mock
    private SQSQueueMonitor          monitorB;

    @Mock
    private SQSQueueListener         listenerA1;

    @Mock
    private SQSQueueListener         listenerA2;

    @Mock
    private SQSQueueListener         listenerB1;

    @Mock
    private SQSQueueListener         listenerC1;

    @Mock
    private SQSQueue                 queueA;

    @Mock
    private SQSQueue                 queueB;

    private SQSQueueMonitorScheduler scheduler;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(this.factory.createMonitor(this.executor, this.queueA)).thenReturn(this.monitorA);
        Mockito.when(this.factory.createMonitor(this.executor, this.queueB)).thenReturn(this.monitorB);

        Mockito.when(this.listenerA1.getQueueUuid()).thenReturn("a");
        Mockito.when(this.listenerA2.getQueueUuid()).thenReturn("a");
        Mockito.when(this.listenerB1.getQueueUuid()).thenReturn("b");
        Mockito.when(this.listenerC1.getQueueUuid()).thenReturn("c");

        Mockito.when(this.queueA.getUuid()).thenReturn("a");
        Mockito.when(this.queueB.getUuid()).thenReturn("b");

        Mockito.when(this.provider.getSqsQueue("a")).thenReturn(this.queueA);
        Mockito.when(this.provider.getSqsQueue("b")).thenReturn(this.queueB);

        this.scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);
    }

    @Test
    public void shouldThrowIfRegisterNullListener() {
        assertThatThrownBy(new ThrowingCallable() {

            @Override
            public void call() throws Throwable {
                SQSQueueMonitorSchedulerImplTest.this.scheduler.register(null);
            }

        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotThrowIfUnregisterNullListener() {
        assertThat(this.scheduler.unregister(null)).isFalse();
    }

    @Test
    public void shouldNotThrowIfUnregisterUnknownListener() {
        assertThat(this.scheduler.unregister(this.listenerC1)).isFalse();
    }

    @Test
    public void shouldUseSingleMonitorInstancePerQueue() {
        assertThat(this.scheduler.register(this.listenerA1)).isTrue();

        Mockito.verify(this.provider, times(1)).getSqsQueue("a");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);

        assertThat(this.scheduler.register(this.listenerA2)).isTrue();

        Mockito.verify(this.provider, times(2)).getSqsQueue("a");
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).add(this.listenerA2);
    }

    @Test
    public void shouldUseSeparateMonitorInstanceForEachQueue() {
        assertThat(this.scheduler.register(this.listenerA1)).isTrue();

        Mockito.verify(this.provider, times(1)).getSqsQueue("a");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);

        assertThat(this.scheduler.register(this.listenerB1)).isTrue();

        Mockito.verify(this.provider, times(1)).getSqsQueue("b");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorB).add(this.listenerB1);
        Mockito.verifyNoMoreInteractions(this.monitorA);

        assertThat(this.scheduler.register(this.listenerA2)).isTrue();

        Mockito.verify(this.provider, times(2)).getSqsQueue("a");
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).add(this.listenerA2);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }

    @Test
    public void shouldNotCreateMonitorForUnknownQueue() {
        assertThat(this.scheduler.register(this.listenerC1)).isFalse();

        Mockito.verify(this.provider, times(1)).getSqsQueue("c");
        Mockito.verifyZeroInteractions(this.factory);
    }

    @Test
    public void shouldCreateNewMonitorAfterUnregisterLast() {
        assertThat(this.scheduler.register(this.listenerA1)).isTrue();

        Mockito.verify(this.provider, times(1)).getSqsQueue("a");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);

        assertThat(this.scheduler.unregister(this.listenerA1)).isTrue();
        Mockito.verify(this.monitorA).remove(this.listenerA1);

        assertThat(this.scheduler.register(this.listenerA2)).isTrue();

        Mockito.verify(this.provider, times(2)).getSqsQueue("a");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA2);
    }

    @Test
    public void shouldNotCreateNewMonitorIfMoreListenersOnUnregister() {
        assertThat(this.scheduler.register(this.listenerA1)).isTrue();
        assertThat(this.scheduler.register(this.listenerA2)).isTrue();

        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA2);

        assertThat(this.scheduler.unregister(this.listenerA1)).isTrue();
        Mockito.verify(this.monitorA, times(1)).remove(this.listenerA1);

        assertThat(this.scheduler.register(this.listenerA1)).isTrue();
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA, times(2)).add(this.listenerA1);
    }

    @Test
    public void shouldDoNothingOnConfigurationChangedIfUnchanged() {
        assertThat(this.scheduler.register(this.listenerA1)).isTrue();
        assertThat(this.scheduler.register(this.listenerB1)).isTrue();

        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorB, times(1)).add(this.listenerB1);

        this.scheduler.onConfigurationChanged();

        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).isShutDown();
        Mockito.verify(this.monitorB).isShutDown();
        Mockito.verifyNoMoreInteractions(this.monitorA);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }

    @Test
    public void shouldStopMonitorOnConfigurationChangedIfQueueRemoved() {
        assertThat(this.scheduler.register(this.listenerA1)).isTrue();
        assertThat(this.scheduler.register(this.listenerB1)).isTrue();

        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorB, times(1)).add(this.listenerB1);

        Mockito.when(this.provider.getSqsQueue("b")).thenReturn(null);

        this.scheduler.onConfigurationChanged();

        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).isShutDown();
        Mockito.verify(this.monitorB).shutDown();
        Mockito.verifyNoMoreInteractions(this.monitorA);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }
}
