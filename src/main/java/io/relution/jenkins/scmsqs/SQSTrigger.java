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

package io.relution.jenkins.scmsqs;

import com.amazonaws.services.sqs.model.Message;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import net.sf.json.JSONObject;

import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import hudson.Extension;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.relution.jenkins.scmsqs.interfaces.Event;
import io.relution.jenkins.scmsqs.interfaces.EventTriggerMatcher;
import io.relution.jenkins.scmsqs.interfaces.ExecutorProvider;
import io.relution.jenkins.scmsqs.interfaces.MessageParser;
import io.relution.jenkins.scmsqs.interfaces.MessageParserFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueListener;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitorScheduler;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueProvider;
import io.relution.jenkins.scmsqs.logging.Log;


public class SQSTrigger extends Trigger<AbstractProject<?, ?>> implements SQSQueueListener, Runnable {

    private final String                       queueUuid;

    private transient SQSQueueMonitorScheduler scheduler;

    private transient MessageParserFactory     messageParserFactory;
    private transient EventTriggerMatcher      eventTriggerMatcher;

    private transient ExecutorProvider           executorProvider;

    @DataBoundConstructor
    public SQSTrigger(final String queueUuid) {
        this.queueUuid = queueUuid;
    }

    public File getLogFile() {
        return new File(this.job.getRootDir(), "sqs-polling.log");
    }

    @Override
    public void start(final AbstractProject<?, ?> project, final boolean newInstance) {
        Log.info("Start trigger for project %s", project);
        this.getScheduler().register(this);
        super.start(project, newInstance);
    }

    @Override
    public void run() {
        final SQSTriggerBuilder builder = new SQSTriggerBuilder(this, this.job);
        builder.run();
    }

    @Override
    public void stop() {
        Log.info("Stop trigger (%s)", this);
        this.getScheduler().unregister(this);
        super.stop();
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return Collections.singleton(new SQSTriggerPollingAction());
    }

    @Override
    public void handleMessages(final List<Message> messages) {
        for (final Message message : messages) {
            this.handleMessage(message);
        }
    }

    @Override
    public String getQueueUuid() {
        return this.queueUuid;
    }

    @Inject
    public void setScheduler(final SQSQueueMonitorScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public SQSQueueMonitorScheduler getScheduler() {
        if (this.scheduler == null) {
            Context.injector().injectMembers(this);
        }
        return this.scheduler;
    }

    @Inject
    public void setMessageParserFactory(final MessageParserFactory factory) {
        this.messageParserFactory = factory;
    }

    public MessageParserFactory getMessageParserFactory() {
        if (this.messageParserFactory == null) {
            Context.injector().injectMembers(this);
        }
        return this.messageParserFactory;
    }

    @Inject
    public void setEventTriggerMatcher(final EventTriggerMatcher matcher) {
        this.eventTriggerMatcher = matcher;
    }

    public EventTriggerMatcher getEventTriggerMatcher() {
        if (this.eventTriggerMatcher == null) {
            Context.injector().injectMembers(this);
        }
        return this.eventTriggerMatcher;
    }

    @Inject
    public void setExecutorHolder(final ExecutorProvider holder) {
        this.executorProvider = holder;
    }

    public ExecutorProvider getExecutorHolder() {
        if (this.executorProvider == null) {
            Context.injector().injectMembers(this);
        }
        return this.executorProvider;
    }

    private void handleMessage(final Message message) {
        final MessageParser parser = this.messageParserFactory.createParser(message);
        final EventTriggerMatcher matcher = this.getEventTriggerMatcher();
        final List<Event> events = parser.parseMessage(message);

        if (matcher.matches(events, this.job.getScm())) {
            this.execute();
        }
    }

    private void execute() {
        Log.info("SQS event triggered build of %s", this.job.getFullDisplayName());
        final ExecutorService service = this.executorProvider.get();
        service.execute(this);
    }

    public final class SQSTriggerPollingAction implements Action {

        public AbstractProject<?, ?> getOwner() {
            return SQSTrigger.this.job;
        }

        @Override
        public String getIconFileName() {
            return "clipboard.png";
        }

        @Override
        public String getDisplayName() {
            return "SQS Activity Log";
        }

        @Override
        public String getUrlName() {
            return "SQSActivityLog";
        }

        public String getLog() throws IOException {
            return Util.loadFile(SQSTrigger.this.getLogFile());
        }

        public void writeLogTo(final XMLOutput out) throws IOException {
            final AnnotatedLargeText<?> log = new AnnotatedLargeText<SQSTriggerPollingAction>(
                    SQSTrigger.this.getLogFile(),
                    Charset.defaultCharset(),
                    true,
                    this);

            log.writeHtmlTo(0, out.asWriter());
        }
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor implements SQSQueueProvider {

        private static final String                             DISPLAY_NAME             = "Trigger build when a message is published to an Amazon SQS queue";

        private static final String                             ERROR_QUEUE_UNAVAILABLE  = "No queues have been configured. Add at least one queue to the global Jenkins configuration.";
        private static final String                             ERROR_QUEUE_UUID_UNKNOWN = "The previously selected queue no longer exists. Select a new queue and save the configuration.";

        private static final String                             INFO_QUEUE_DEFAULT       = "Selected first available queue. Verify the selection and save the configuration.";

        private static final String                             KEY_SQS_QUEUES           = "sqsQueues";
        private volatile List<SQSTriggerQueue>                  sqsQueues;

        private volatile transient Map<String, SQSTriggerQueue> sqsQueueMap;
        private volatile transient SQSQueueMonitorScheduler     scheduler;

        public static DescriptorImpl get() {
            return Trigger.all().get(DescriptorImpl.class);
        }

        public DescriptorImpl() {
            this.load();
        }

        @Override
        public synchronized void load() {
            super.load();
            this.initQueueMap();
        }

        @Override
        public boolean isApplicable(final Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        public ListBoxModel doFillQueueUuidItems() {
            final List<SQSTriggerQueue> queues = this.getSqsQueues();
            final ListBoxModel items = new ListBoxModel();

            for (final SQSTriggerQueue queue : queues) {
                items.add(queue.getName(), queue.getUuid());
            }

            return items;
        }

        public FormValidation doCheckQueueUuid(@QueryParameter final String value) {
            if (this.getSqsQueues().size() == 0) {
                return FormValidation.error(ERROR_QUEUE_UNAVAILABLE);
            }

            if (StringUtils.isEmpty(value)) {
                return FormValidation.ok(INFO_QUEUE_DEFAULT);
            }

            final SQSQueue queue = this.getSqsQueue(value);

            if (queue == null) {
                return FormValidation.error(ERROR_QUEUE_UUID_UNKNOWN);
            }

            return FormValidation.ok();
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject json) throws FormException {
            final Object sqsQueues = json.get(KEY_SQS_QUEUES);

            this.sqsQueues = req.bindJSONToList(SQSTriggerQueue.class, sqsQueues);
            this.initQueueMap();
            this.save();

            this.scheduler.onConfigurationChanged();
            return true;
        }

        @Override
        public List<SQSTriggerQueue> getSqsQueues() {
            if (this.sqsQueues == null) {
                this.load();
            }
            if (this.sqsQueues == null) {
                return Collections.emptyList();
            }
            return this.sqsQueues;
        }

        @Override
        public SQSQueue getSqsQueue(final String uuid) {
            if (this.sqsQueueMap == null) {
                return null;
            }
            return this.sqsQueueMap.get(uuid);
        }

        @Inject
        public void setScheduler(final SQSQueueMonitorScheduler sQSQueueMonitorScheduler) {
            this.scheduler = sQSQueueMonitorScheduler;
        }

        public SQSQueueMonitorScheduler getScheduler() {
            if (this.scheduler == null) {
                Context.injector().injectMembers(this);
            }
            return this.scheduler;
        }

        private void initQueueMap() {
            if (this.sqsQueues == null) {
                return;
            }

            this.sqsQueueMap = Maps.newHashMapWithExpectedSize(this.sqsQueues.size());

            for (final SQSTriggerQueue queue : this.sqsQueues) {
                this.sqsQueueMap.put(queue.getUuid(), queue);
            }
        }
    }
}
