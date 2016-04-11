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

package io.relution.jenkins.scmsqs.interfaces;

import java.util.List;

import hudson.scm.SCM;


/**
 * Interface definition for classes that match events to source code management (SCM)
 * configurations.
 */
public interface EventTriggerMatcher {

    /**
     * Returns a value indicating whether any of the specified events matches the specified SCM
     * configuration.
     * @param events The collection of {@link Event}s to test against the SCM configuration.
     * @param scm The {@link SCM} to test against.
     * @return {@code true} if any of the specified events matches the specified SCM configuration;
     * otherwise, {@code false}.
     */
    boolean matches(List<Event> events, SCM scm);

    /**
     * Returns a value indicating whether the specified event matches the specified SCM
     * configuration.
     * @param event The {@link Event} to test against the SCM configuration.
     * @param scm The {@link SCM} to test against.
     * @return {@code true} if the specified event matches the specified SCM configuration;
     * otherwise, {@code false}.
     */
    boolean matches(Event event, SCM scm);
}
