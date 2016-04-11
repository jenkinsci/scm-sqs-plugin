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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.google.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import io.relution.jenkins.scmsqs.interfaces.SQSFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;
import io.relution.jenkins.scmsqs.logging.Log;


public class SQSTriggerQueue extends AbstractDescribableImpl<SQSTriggerQueue> implements SQSQueue {

    public static final Pattern  SQS_URL_PATTERN        = Pattern
            .compile("^(?:http(?:s)?://)?(?<endpoint>sqs\\..+?\\.amazonaws\\.com)/(?<id>.+?)/(?<name>.*)$");

    public static final Pattern  CODECOMMIT_URL_PATTERN = Pattern
            .compile("^(?:http(?:s)?://)?git-codecommit\\.(?<region>.+?)\\.amazonaws\\.com/v1/repos/(?<name>.*)$");

    private final String         uuid;

    private final String         nameOrUrl;
    private final String         accessKey;
    private final Secret         secretKey;

    private final int            waitTimeSeconds;
    private final int            maxNumberOfMessages;

    private String               url;
    private final String         name;
    private final String         endpoint;

    private transient SQSFactory factory;
    private transient AmazonSQS  sqs;

    private transient String     s;

    @DataBoundConstructor
    public SQSTriggerQueue(
            final String uuid,
            final String nameOrUrl,
            final String accessKey,
            final Secret secretKey,
            final int waitTimeSeconds,
            final int maxNumberOfMessages) {
        this.uuid = StringUtils.isBlank(uuid) ? UUID.randomUUID().toString() : uuid;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.nameOrUrl = nameOrUrl;

        this.waitTimeSeconds = this.limit(waitTimeSeconds, 1, 20, 20);
        this.maxNumberOfMessages = this.limit(maxNumberOfMessages, 1, 10, 10);

        final Matcher sqsUrlMatcher = SQS_URL_PATTERN.matcher(nameOrUrl);

        if (sqsUrlMatcher.matches()) {
            this.url = nameOrUrl;
            this.name = sqsUrlMatcher.group("name");
            this.endpoint = sqsUrlMatcher.group("endpoint");

        } else {
            this.name = nameOrUrl;
            this.endpoint = null;

        }

        Log.info("Create new SQSTriggerQueue(%s, %s, %s)", this.uuid, nameOrUrl, accessKey);
    }

    public AmazonSQS getSQSClient() {
        if (this.sqs == null) {
            this.sqs = this.getFactory().createSQSAsync(this);
        }
        return this.sqs;
    }

    @Inject
    public void setFactory(final SQSFactory factory) {
        this.factory = factory;
    }

    public SQSFactory getFactory() {
        if (this.factory == null) {
            Context.injector().injectMembers(this);
        }
        return this.factory;
    }

    @Override
    public String getUuid() {
        return this.uuid;
    }

    public String getNameOrUrl() {
        return this.nameOrUrl;
    }

    public String getAccessKey() {
        return this.accessKey;
    }

    public Secret getSecretKey() {
        return this.secretKey;
    }

    @Override
    public int getWaitTimeSeconds() {
        return this.waitTimeSeconds;
    }

    @Override
    public int getMaxNumberOfMessages() {
        return this.maxNumberOfMessages;
    }

    @Override
    public String getUrl() {
        if (this.url == null) {
            final AmazonSQS client = this.getSQSClient();
            final GetQueueUrlResult result = client.getQueueUrl(this.name);
            this.url = result.getQueueUrl();
        }
        return this.url;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getEndpoint() {
        return this.endpoint;
    }

    @Override
    public String getAWSAccessKeyId() {
        return this.accessKey;
    }

    @Override
    public String getAWSSecretKey() {
        if (this.secretKey == null) {
            return null;
        }
        return this.secretKey.getPlainText();
    }

    @Override
    public boolean isValid() {
        if (StringUtils.isBlank(this.getName())) {
            return false;
        }
        if (StringUtils.isEmpty(this.getAWSAccessKeyId())) {
            return false;
        }
        if (StringUtils.isEmpty(this.getAWSSecretKey())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return this.uuid.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof SQSTriggerQueue)) {
            return false;
        }
        final SQSTriggerQueue other = (SQSTriggerQueue) obj;
        if (!this.uuid.equals(other.uuid)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        if (this.s == null) {
            final StringBuilder sb = new StringBuilder();
            sb.append(this.name);

            if (!StringUtils.isBlank(this.endpoint)) {
                sb.append(" (");
                sb.append(this.endpoint);
                sb.append(")");
            }

            sb.append(" {");
            sb.append(this.uuid);
            sb.append("}");

            this.s = sb.toString();
        }
        return this.s;
    }

    private int limit(final int value, final int min, final int max, final int fallbackValue) {
        if (value < min || value > max) {
            return fallbackValue;
        } else {
            return value;
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SQSTriggerQueue> {

        private static final String ERROR_WAIT_TIME_SECONDS      = "Wait Time must be a number between 1 and 20.";
        private static final String ERROR_MAX_NUMBER_OF_MESSAGES = "Max. number of messages must be a number between 1 and 10.";

        private static final String INFO_URL_SQS                 = "You can use \"%s\" instead of the full URL";

        private static final String WARNING_URL                  = "Name or URL of an SQS queue is required";

        private static final String ERROR_URL_CODECOMMIT         = "This is a CodeCommit URL, please provide a queue name or SQS URL";
        private static final String ERROR_URL_UNKNOWN            = "This is not an SQS URL, please provide a queue name or SQS URL";

        @Override
        public String getDisplayName() {
            return "An Amazon SQS queue configuration"; // unused
        }

        public FormValidation doCheckNameOrUrl(@QueryParameter final String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.warning(WARNING_URL);
            }

            final Matcher sqsUrlMatcher = SQS_URL_PATTERN.matcher(value);

            if (sqsUrlMatcher.matches()) {
                final String name = sqsUrlMatcher.group("name");
                return FormValidation.ok(INFO_URL_SQS, name);
            }

            final Matcher ccUrlMatcher = CODECOMMIT_URL_PATTERN.matcher(value);

            if (ccUrlMatcher.matches()) {
                return FormValidation.error(ERROR_URL_CODECOMMIT);
            }

            if (StringUtils.startsWith(value, "http://") || StringUtils.startsWith(value, "https://")) {
                return FormValidation.error(ERROR_URL_UNKNOWN);
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckWaitTimeSeconds(@QueryParameter final String value) {
            return this.validateNumber(value, 1, 20, ERROR_WAIT_TIME_SECONDS);
        }

        public FormValidation doCheckMaxNumberOfMessage(@QueryParameter final String value) {
            return this.validateNumber(value, 1, 10, ERROR_MAX_NUMBER_OF_MESSAGES);
        }

        public FormValidation doValidate(
                @QueryParameter final String uuid,
                @QueryParameter final String nameOrUrl,
                @QueryParameter final String accessKey,
                @QueryParameter final Secret secretKey) throws IOException {
            try {
                final SQSTriggerQueue queue = new SQSTriggerQueue(uuid, nameOrUrl, accessKey, secretKey, 0, 0);

                if (StringUtils.isBlank(queue.getName())) {
                    return FormValidation.warning("Name or URL of the queue must be set.");
                }

                if (StringUtils.isEmpty(queue.getAWSAccessKeyId())) {
                    return FormValidation.warning("AWS access key ID must be set.");
                }

                if (StringUtils.isEmpty(queue.getAWSSecretKey())) {
                    return FormValidation.warning("AWS secret key must be set.");
                }

                final AmazonSQS client = queue.getSQSClient();

                if (client == null) {
                    return FormValidation.error("Failed to create SQS client");
                }

                final String queueName = queue.getName();
                final GetQueueUrlResult result = client.getQueueUrl(queueName);

                if (result == null) {
                    return FormValidation.error("Failed to get SQS client queue URL");
                }

                final String url = result.getQueueUrl();
                return FormValidation.ok("Access to %s successful\n(%s)", queue.getName(), url);

            } catch (final AmazonServiceException ase) {
                return FormValidation.error(ase, ase.getMessage());

            } catch (final RuntimeException ex) {
                return FormValidation.error(ex, "Error validating SQS access");
            }
        }

        private FormValidation validateNumber(final String value, final int min, final int max, final String message) {
            try {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.error(message);
                }

                final int number = Integer.parseInt(value);

                if (number < min || number > max) {
                    return FormValidation.error(message);
                }

                return FormValidation.ok();

            } catch (final NumberFormatException e) {
                return FormValidation.error(message);
            }
        }
    }
}
