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
import static org.mockito.Mockito.times;

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
    ExecutorService  executor;

    @Mock
    SQSQueueProvider provider;

    @Mock
    SQSFactory       factory;

    @Mock
    SQSQueueMonitor  monitorA;

    @Mock
    SQSQueueMonitor  monitorB;

    @Mock
    SQSQueueListener listenerA1;

    @Mock
    SQSQueueListener listenerA2;

    @Mock
    SQSQueueListener listenerB1;

    @Mock
    SQSQueueListener listenerC1;

    @Mock
    SQSQueue         queueA;

    @Mock
    SQSQueue         queueB;

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
    }

    @Test
    public void shouldThrowIfRegisterNullListener() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);
        Throwable thrown = null;

        try {
            scheduler.register(null);
        } catch (final Throwable t) {
            thrown = t;
        }

        assertThat(thrown).isNotNull();
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotThrowIfUnregisterNullListener() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.unregister(null)).isFalse();
    }

    @Test
    public void shouldNotThrowIfUnregisterUnknownListener() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.unregister(this.listenerC1)).isFalse();
    }

    @Test
    public void shouldUseSingleMonitorInstancePerQueue() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.register(this.listenerA1)).isTrue();

        Mockito.verify(this.provider, times(1)).getSqsQueue("a");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);

        assertThat(scheduler.register(this.listenerA2)).isTrue();

        Mockito.verify(this.provider, times(2)).getSqsQueue("a");
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).add(this.listenerA2);
    }

    @Test
    public void shouldUseSeparateMonitorInstanceForEachQueue() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.register(this.listenerA1)).isTrue();

        Mockito.verify(this.provider, times(1)).getSqsQueue("a");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);

        assertThat(scheduler.register(this.listenerB1)).isTrue();

        Mockito.verify(this.provider, times(1)).getSqsQueue("b");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorB).add(this.listenerB1);
        Mockito.verifyNoMoreInteractions(this.monitorA);

        assertThat(scheduler.register(this.listenerA2)).isTrue();

        Mockito.verify(this.provider, times(2)).getSqsQueue("a");
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).add(this.listenerA2);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }

    @Test
    public void shouldNotCreateMonitorForUnknownQueue() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.register(this.listenerC1)).isFalse();

        Mockito.verify(this.provider, times(1)).getSqsQueue("c");
        Mockito.verifyZeroInteractions(this.factory);
    }

    @Test
    public void shouldCreateNewMonitorAfterUnregisterLast() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.register(this.listenerA1)).isTrue();

        Mockito.verify(this.provider, times(1)).getSqsQueue("a");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA1);

        assertThat(scheduler.unregister(this.listenerA1)).isTrue();
        Mockito.verify(this.monitorA).remove(this.listenerA1);

        assertThat(scheduler.register(this.listenerA2)).isTrue();

        Mockito.verify(this.provider, times(2)).getSqsQueue("a");
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA).add(this.listenerA2);
    }

    @Test
    public void shouldNotCreateNewMonitorIfMoreListenersOnUnregister() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.register(this.listenerA1)).isTrue();
        assertThat(scheduler.register(this.listenerA2)).isTrue();

        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA2);

        assertThat(scheduler.unregister(this.listenerA1)).isTrue();
        Mockito.verify(this.monitorA, times(1)).remove(this.listenerA1);

        assertThat(scheduler.register(this.listenerA1)).isTrue();
        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA, times(2)).add(this.listenerA1);
    }

    @Test
    public void shouldDoNothingOnConfigurationChangedIfUnchanged() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.register(this.listenerA1)).isTrue();
        assertThat(scheduler.register(this.listenerB1)).isTrue();

        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorB, times(1)).add(this.listenerB1);

        scheduler.onConfigurationChanged();

        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).isShutDown();
        Mockito.verify(this.monitorB).isShutDown();
        Mockito.verifyNoMoreInteractions(this.monitorA);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }

    @Test
    public void shouldStopMonitorOnConfigurationChangedIfQueueRemoved() {
        final SQSQueueMonitorScheduler scheduler = new SQSQueueMonitorSchedulerImpl(this.executor, this.provider, this.factory);

        assertThat(scheduler.register(this.listenerA1)).isTrue();
        assertThat(scheduler.register(this.listenerB1)).isTrue();

        Mockito.verify(this.factory).createMonitor(this.executor, this.queueA);
        Mockito.verify(this.factory).createMonitor(this.executor, this.queueB);
        Mockito.verify(this.monitorA, times(1)).add(this.listenerA1);
        Mockito.verify(this.monitorB, times(1)).add(this.listenerB1);

        Mockito.when(this.provider.getSqsQueue("b")).thenReturn(null);

        scheduler.onConfigurationChanged();

        Mockito.verifyNoMoreInteractions(this.factory);
        Mockito.verify(this.monitorA).isShutDown();
        Mockito.verify(this.monitorB).shutDown();
        Mockito.verifyNoMoreInteractions(this.monitorA);
        Mockito.verifyNoMoreInteractions(this.monitorB);
    }
}
