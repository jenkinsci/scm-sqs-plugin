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

import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;

import io.relution.jenkins.scmsqs.interfaces.SQSFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueListener;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitor;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitorScheduler;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueProvider;
import io.relution.jenkins.scmsqs.logging.Log;
import io.relution.jenkins.scmsqs.util.ThrowIf;


public class SQSQueueMonitorSchedulerImpl implements SQSQueueMonitorScheduler {

    private final ExecutorService              executor;
    private final SQSQueueProvider             provider;
    private final SQSFactory                   factory;

    private final Map<String, SQSQueueMonitor> monitors = new HashMap<>();

    @Inject
    public SQSQueueMonitorSchedulerImpl(final ExecutorService executor, final SQSQueueProvider provider, final SQSFactory factory) {
        this.executor = executor;
        this.provider = provider;
        this.factory = factory;
    }

    @Override
    public boolean register(final SQSQueueListener listener) {
        ThrowIf.isNull(listener, "listener");

        Log.info("Register SQS listener");
        final String uuid = listener.getQueueUuid();
        final SQSQueue queue = this.provider.getSqsQueue(uuid);

        if (queue == null) {
            Log.warning("No queue for {%s}, aborted", uuid);
            return false;
        }

        this.register(listener, uuid, queue);
        return true;
    }

    @Override
    public boolean unregister(final SQSQueueListener listener) {
        if (listener == null) {
            return false;
        }

        Log.info("Unregister SQS listener");
        final String uuid = listener.getQueueUuid();
        final SQSQueueMonitor monitor = this.monitors.get(uuid);

        if (monitor == null) {
            Log.warning("No monitor for {%s}, aborted", uuid);
            return false;
        }

        Log.info("Remove listener from monitor for {%s}", uuid);
        if (monitor.remove(listener)) {
            monitor.shutDown();
        }

        if (monitor.isShutDown()) {
            Log.info("Monitor is shut down, remove monitor for {%s}", uuid);
            this.monitors.remove(uuid);
        }

        return true;
    }

    @Override
    public void onConfigurationChanged() {
        final Iterator<Entry<String, SQSQueueMonitor>> entries = this.monitors.entrySet().iterator();

        while (entries.hasNext()) {
            final Entry<String, SQSQueueMonitor> entry = entries.next();
            this.reconfigure(entries, entry);
        }
    }

    private void register(final SQSQueueListener listener, final String uuid, final SQSQueue queue) {
        SQSQueueMonitor monitor = this.monitors.get(uuid);

        if (monitor == null) {
            Log.info("No monitor exists, creating new monitor for %s", queue);
            monitor = this.factory.createMonitor(this.executor, queue);
            this.monitors.put(uuid, monitor);
        }

        Log.info("Add listener to monitor for %s", queue);
        monitor.add(listener);
    }

    private void reconfigure(final Iterator<Entry<String, SQSQueueMonitor>> entries, final Entry<String, SQSQueueMonitor> entry) {
        final String uuid = entry.getKey();
        final SQSQueueMonitor monitor = entry.getValue();
        final SQSQueue queue = this.provider.getSqsQueue(uuid);

        if (queue == null) {
            Log.info("Queue {%s} removed, shut down monitor", uuid);
            monitor.shutDown();
            entries.remove();

        } else if (monitor.isShutDown()) {
            Log.info("Monitor for queue {%s} is shut down, restart", uuid);
            this.executor.execute(monitor);

        }
    }
}
