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

package com.cedarpolicy.model.exception;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An exception which is thrown when Cedar encounters an error in a supplied query which caused it
 * to stop processing; for example, a syntax error in a policy string.
 */
public class BadRequestException extends AuthException {
    private final List<String> errors;

    /**
     * Failure due to bad request.
     *
     * @param errors List of Errors.
     */
    public BadRequestException(String[] errors) {
        super("Bad request: " + String.join("\n", errors));
        this.errors = new ArrayList<>(Arrays.asList(errors));
    }

    /**
     * Get the errors.
     *
     * @return the error messages returned by Cedar
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
