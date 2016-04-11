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

package io.relution.jenkins.scmsqs.model.entities.codecommit;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.transport.URIish;

import io.relution.jenkins.scmsqs.interfaces.Event;


public class CodeCommitEvent implements Event {

    private final static String HOST = "git-codecommit.%s.amazonaws.com";
    private final static String PATH = "/v1/repos/%s";

    private final String        host;
    private final String        path;

    private final String        branch;

    public CodeCommitEvent(final Record record, final Reference reference) {
        final String arn = record.getEventSourceARN();
        final String[] tokens = arn.split(":", 6);

        this.host = String.format(HOST, tokens[3]);
        this.path = String.format(PATH, tokens[5]);

        final String name = reference.getName();
        this.branch = StringUtils.stripStart(name, "refs/");
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public String getUser() {
        return null;
    }

    @Override
    public String getBranch() {
        return this.branch;
    }

    @Override
    public boolean isMatch(final URIish uri) {
        if (uri == null) {
            return false;
        }

        if (!StringUtils.equals(this.host, uri.getHost())) {
            return false;
        }

        if (!StringUtils.equals(this.path, uri.getPath())) {
            return false;
        }

        return true;
    }
}
