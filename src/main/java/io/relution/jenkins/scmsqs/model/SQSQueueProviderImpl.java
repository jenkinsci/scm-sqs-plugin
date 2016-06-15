
package io.relution.jenkins.scmsqs.model;

import java.util.List;

import io.relution.jenkins.scmsqs.SQSTrigger;
import io.relution.jenkins.scmsqs.interfaces.SQSQueue;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueProvider;


public class SQSQueueProviderImpl implements SQSQueueProvider {

    @Override
    public List<? extends SQSQueue> getSqsQueues() {
        final SQSTrigger.DescriptorImpl descriptor = SQSTrigger.DescriptorImpl.get();
        return descriptor.getSqsQueues();
    }

    @Override
    public SQSQueue getSqsQueue(final String uuid) {
        final SQSTrigger.DescriptorImpl descriptor = SQSTrigger.DescriptorImpl.get();
        return descriptor.getSqsQueue(uuid);
    }
}
