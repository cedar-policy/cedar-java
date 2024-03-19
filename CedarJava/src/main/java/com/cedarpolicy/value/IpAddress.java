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

package com.cedarpolicy.value;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents a Cedar ip address extension value. Ip address are encoded as strings in dot-decimal
 * notation (e.g., <code>"192.168.1.0"</code>). Values for CIDR ranges may also be constructed by
 * providing strings in CIDR notation.
 */
public class IpAddress extends Value {

    private static class IpAddressValidator {
        private static final Pattern ipv4Pattern =
                Pattern.compile(
                        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.)"
                                + "{3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

        public static boolean validIPv4(String ip) {
            if (ip == null || ip.isEmpty()) return false;
            ip = ip.trim();
            if ((ip.length() < 6) || (ip.length() > 15)) return false;
            try {
                Matcher matcher = ipv4Pattern.matcher(ip);
                return matcher.matches();
            } catch (PatternSyntaxException ex) {
                return false;
            }
        }

        private static final Pattern ipv6Pattern =
                Pattern.compile(
                        "^(([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|"
                                + "(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|:))|"
                                + "(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:))|"
                                + "(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|:))|"
                                + "(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|:))|"
                                + "(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|:))|"
                                + "(([0-9A-Fa-f]{1,4}:)(((:[0-9A-Fa-f]{1,4}){1,6})|:))|"
                                + "((:)(((:[0-9A-Fa-f]{1,4}){1,7})|:))|$");

        public static boolean validIPv6(String ip) {
            if (ip == null || ip.isEmpty()) return false;
            ip = ip.trim();
            if ((ip.length() < 3) || (ip.length() > 39)) return false;
            try {
                Matcher matcher = ipv6Pattern.matcher(ip);
                return matcher.matches();
            } catch (PatternSyntaxException ex) {
                return false;
            }
        }
    }

    /** ip address as a string. */
    private final String ipAddress;

    /**
     * Construct IPAddress.
     *
     * @param ipAddress IP address as a String.
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public IpAddress(String ipAddress) throws NullPointerException, IllegalArgumentException {
        if (!IpAddressValidator.validIPv4(ipAddress) && !IpAddressValidator.validIPv6(ipAddress)) {
            throw new IllegalArgumentException(
                    "Input string is not a valid IPv4 or IPv6 address\n"
                            + "(Note we do not allow mixing IPv4 and IPv6 syntax. E.g., \"::ffff:127.0.0.1\") \n "
                            + ipAddress);
        }
        this.ipAddress = ipAddress;
    }

    /** Convert IPAddress to Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return "ip(\"" + ipAddress + "\")";
    }

    /** Equals. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IpAddress ipAddress1 = (IpAddress) o;
        return ipAddress.equals(ipAddress1.ipAddress);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(ipAddress);
    }

    /** As a string. */
    @Override
    public String toString() {
        return ipAddress;
    }
}
