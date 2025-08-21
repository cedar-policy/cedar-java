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

package com.cedarpolicy.value.functions;

import lombok.Getter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.cedarpolicy.value.DateTime;
import com.cedarpolicy.value.Duration;
import com.cedarpolicy.value.Value;

@Getter
public class Offset extends Value {

    private final Duration offsetDuration;
    private final DateTime dateTime;

    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public Offset(DateTime dateTime, Duration offsetDuration) throws NullPointerException, IllegalArgumentException {
        this.dateTime = dateTime;
        this.offsetDuration = offsetDuration;
    }

    @Override
    public String toCedarExpr() {
        return String.format("%s.offset(%s)", this.dateTime.toCedarExpr(), this.offsetDuration.toCedarExpr());
    }
}
