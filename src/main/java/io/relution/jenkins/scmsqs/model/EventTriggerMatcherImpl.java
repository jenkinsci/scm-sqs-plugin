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

package io.relution.jenkins.scmsqs.model;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.multiplescms.MultiSCM;

import java.util.List;

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import io.relution.jenkins.scmsqs.interfaces.Event;
import io.relution.jenkins.scmsqs.interfaces.EventTriggerMatcher;
import jenkins.model.Jenkins;


public class EventTriggerMatcherImpl implements EventTriggerMatcher {

    @Override
    public boolean matches(final List<Event> events, final SCM scm) {
        if (events == null || scm == null) {
            return false;
        }

        for (final Event event : events) {
            if (this.matches(event, scm)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean matches(final Event event, final SCM scm) {
        if (event == null || scm == null) {
            return false;
        }

        if (this.isGitScmAvailable() && this.matchesGitSCM(event, scm)) {
            return true;

        } else if (this.isMultiScmAvailable() && this.matchesMultiSCM(event, scm)) {
            return true;

        } else {
            return false;

        }
    }

    private boolean matchesGitSCM(final Event event, final SCM scmProvider) {
        if (!(scmProvider instanceof hudson.plugins.git.GitSCM)) {
            return false;
        }

        final GitSCM git = (GitSCM) scmProvider;
        final List<RemoteConfig> configs = git.getRepositories();
        final List<BranchSpec> branches = git.getBranches();

        return this.matchesConfigs(event, configs) && this.matchesBranches(event, branches);
    }

    private boolean matchesMultiSCM(final Event event, final SCM scmProvider) {
        if (!(scmProvider instanceof org.jenkinsci.plugins.multiplescms.MultiSCM)) {
            return false;
        }

        final MultiSCM multiSCM = (MultiSCM) scmProvider;
        final List<SCM> scms = multiSCM.getConfiguredSCMs();

        for (final SCM scm : scms) {
            if (this.matches(event, scm)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesBranches(final Event event, final List<BranchSpec> branches) {
        for (final BranchSpec branch : branches) {
            if (this.matchesBranch(event, branch)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBranch(final Event event, final BranchSpec branch) {
        return branch.matches(event.getBranch());
    }

    private boolean matchesConfigs(final Event event, final List<RemoteConfig> configs) {
        for (final RemoteConfig config : configs) {
            if (this.matchesConfig(event, config)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesConfig(final Event event, final RemoteConfig config) {
        for (final URIish uri : config.getURIs()) {
            if (event.isMatch(uri)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMultiScmAvailable() {
        final Jenkins jenkins = Jenkins.getInstance();

        if (jenkins == null) {
            return false;
        }

        return jenkins.getPlugin("multiple-scms") != null;
    }

    private boolean isGitScmAvailable() {
        final Jenkins jenkins = Jenkins.getInstance();

        if (jenkins == null) {
            return false;
        }

        return jenkins.getPlugin("git") != null;
    }
}