/*
 * Copyright 2022-2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cedarpolicy.model.slice;

import com.cedarpolicy.loader.LibraryLoader;

import com.cedarpolicy.model.exception.InternalException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Policy Set containing policies in the Cedar language. */
public class PolicySet {
    static {
        LibraryLoader.loadLibrary();
    }

    /** Policy set. */
    public Set<Policy> policies;

    /** Template Instantiations. */
    public List<TemplateInstantiation> templateInstantiations;

    /** Templates. */
    public Set<Policy> templates;

    public PolicySet() {
        this.policies = Collections.emptySet();
        this.templates = Collections.emptySet();
        this.templateInstantiations = Collections.emptyList();
    }

    public PolicySet(Set<Policy> policies) {
        this.policies = policies;
        this.templates = Collections.emptySet();
        this.templateInstantiations = Collections.emptyList();
    }

    public PolicySet(Set<Policy> policies, Set<Policy> templates) {
        this.policies = policies;
        this.templates = templates;
        this.templateInstantiations = Collections.emptyList();
    }

    public PolicySet(Set<Policy> policies, Set<Policy> templates, List<TemplateInstantiation> templateInstantiations) {
        this.policies = policies;
        this.templates = templates;
        this.templateInstantiations = templateInstantiations;
    }

    /**
     * Parse multiple policies and templates from a file into a PolicySet.
     * @param filePath the path to the file containing the policies
     * @return a PolicySet containing the parsed policies
     * @throws InternalException
     * @throws IOException
     * @throws NullPointerException
     */
    public static PolicySet parsePolicies(Path filePath) throws InternalException, IOException {
        // Read the file contents into a String
        String policiesString = Files.readString(filePath);
        return parsePolicies(policiesString);
    }

    /**
     * Parse a string containing multiple policies and templates into a PolicySet.
     * @param policiesString the string containing the policies
     * @return a PolicySet containing the parsed policies
     * @throws InternalException
     * @throws NullPointerException
     */
    public static PolicySet parsePolicies(String policiesString) throws InternalException {
        PolicySet policySet = parsePoliciesJni(policiesString);
        return policySet;
    }

    private static native PolicySet parsePoliciesJni(String policiesStr) throws InternalException, NullPointerException;
}
