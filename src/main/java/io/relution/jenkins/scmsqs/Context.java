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

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.util.concurrent.ThreadFactory;

import hudson.Extension;
import io.relution.jenkins.scmsqs.factories.ExecutorFactoryImpl;
import io.relution.jenkins.scmsqs.factories.MessageParserFactoryImpl;
import io.relution.jenkins.scmsqs.factories.SQSFactoryImpl;
import io.relution.jenkins.scmsqs.factories.ThreadFactoryImpl;
import io.relution.jenkins.scmsqs.interfaces.EventTriggerMatcher;
import io.relution.jenkins.scmsqs.interfaces.ExecutorFactory;
import io.relution.jenkins.scmsqs.interfaces.ExecutorHolder;
import io.relution.jenkins.scmsqs.interfaces.MessageParserFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSFactory;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitorScheduler;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueProvider;
import io.relution.jenkins.scmsqs.model.EventTriggerMatcherImpl;
import io.relution.jenkins.scmsqs.net.RequestFactory;
import io.relution.jenkins.scmsqs.net.RequestFactoryImpl;
import io.relution.jenkins.scmsqs.threading.ExecutorHolderImpl;
import io.relution.jenkins.scmsqs.threading.SQSQueueMonitorSchedulerImpl;


@Extension
public class Context extends com.google.inject.AbstractModule {

    private static Injector injector;

    public static Injector injector() {
        if (injector == null) {
            injector = Guice.createInjector(new Context());
        }
        return injector;
    }

    @Override
    protected void configure() {
        this.bind(ThreadFactory.class)
                .to(ThreadFactoryImpl.class)
                .in(com.google.inject.Singleton.class);

        this.bind(ExecutorFactory.class)
                .to(ExecutorFactoryImpl.class)
                .in(com.google.inject.Singleton.class);

        this.bind(ExecutorHolder.class)
                .to(ExecutorHolderImpl.class)
                .in(com.google.inject.Singleton.class);

        this.bind(SQSFactory.class)
                .to(SQSFactoryImpl.class)
                .in(com.google.inject.Singleton.class);

        this.bind(RequestFactory.class)
                .to(RequestFactoryImpl.class)
                .in(com.google.inject.Singleton.class);

        this.bind(SQSQueueProvider.class)
                .to(SQSTrigger.DescriptorImpl.class)
                .in(com.google.inject.Singleton.class);

        this.bind(SQSQueueMonitorScheduler.class)
                .to(SQSQueueMonitorSchedulerImpl.class)
                .in(com.google.inject.Singleton.class);

        this.bind(MessageParserFactory.class)
                .to(MessageParserFactoryImpl.class)
                .in(com.google.inject.Singleton.class);

        this.bind(EventTriggerMatcher.class)
                .to(EventTriggerMatcherImpl.class)
                .in(com.google.inject.Singleton.class);
    }
}
