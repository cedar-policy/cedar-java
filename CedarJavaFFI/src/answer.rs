/*
 * Copyright Cedar Contributors
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

use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
#[serde(tag = "success")]
/// Generic Answer type designed for serialization.
pub enum Answer {
    /// The call succeeded
    #[serde(rename = "true")]
    Success {
        /// JSON containing the result of the call
        result: String,
    },
    #[serde(rename = "false")]
    /// The call failed
    Failure {
        /// Whether the failure is "internal".
        ///
        /// An "internal failure" is returned when there is a fault in the
        /// Cedar Rust code, or when there is a problem with the request in
        /// the parts which the Java library is responsible for (e.g. an
        /// unsupported operation).
        ///
        /// By contrast, a "bad request" is returned when there is an issue in the
        /// part of the request supplied by the ultimate user of the library, e.g. a
        /// syntax error in a policy.
        #[serde(rename = "isInternal")]
        is_internal: bool,
        /// String description of the error(s) that led to the failure
        errors: Vec<String>,
    },
}

impl Answer {
    /// An "internal failure" result; see docs on [`Answer::Failure`]
    pub fn fail_internally(message: String) -> Self {
        Self::Failure {
            is_internal: true,
            errors: vec![message],
        }
    }

    /// A failure result that isn't internal; see docs on
    /// `Answer::Failure`
    pub fn fail_bad_request(errors: Vec<String>) -> Self {
        Self::Failure {
            is_internal: false,
            errors,
        }
    }
}
