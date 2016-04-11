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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.util.StreamTaskListener;
import io.relution.jenkins.scmsqs.logging.Log;


public class SQSTriggerBuilder implements Runnable {

    private final SQSTrigger            trigger;
    private final AbstractProject<?, ?> job;

    private final DateFormat            formatter = DateFormat.getDateTimeInstance();

    public SQSTriggerBuilder(final SQSTrigger trigger, final AbstractProject<?, ?> job) {
        this.trigger = trigger;
        this.job = job;
    }

    @Override
    public void run() {
        final File log = this.trigger.getLogFile();

        try (final StreamTaskListener listener = new StreamTaskListener(log)) {
            this.buildIfChanged(listener);

        } catch (final IOException e) {
            Log.severe(e, "Failed to record SCM polling");

        }
    }

    private void buildIfChanged(final StreamTaskListener listener) {
        final PrintStream logger = listener.getLogger();
        final long now = System.currentTimeMillis();

        logger.format("Started on %s", this.toDateTime(now));
        final boolean hasChanges = this.job.poll(listener).hasChanges();
        logger.println("Done. Took " + this.toTimeSpan(now));

        if (!hasChanges) {
            logger.println("No changes");
        } else {
            logger.println("Changes found");
            this.build(logger, now);
        }
    }

    private void build(final PrintStream logger, final long now) {
        final String note = "SQS poll initiated on " + this.toDateTime(now);
        final Cause cause = new Cause.RemoteCause("SQS trigger", note);

        if (this.job.scheduleBuild(cause)) {
            logger.println("Job queued");
        } else {
            logger.println("Job NOT queued - it was determined that this job has been queued already.");
        }
    }

    private String toDateTime(final long timestamp) {
        final Date date = new Date(timestamp);
        return this.formatter.format(date);
    }

    private String toTimeSpan(final long timestamp) {
        final long now = System.currentTimeMillis();
        return Util.getTimeSpanString(now - timestamp);
    }
}
