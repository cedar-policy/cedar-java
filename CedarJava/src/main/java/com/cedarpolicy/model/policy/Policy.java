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

package com.cedarpolicy.model.policy;

import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.exception.InternalException;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.cedarpolicy.model.Effect;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;

/** Policies in the Cedar language. */
public class Policy {
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    static {
        LibraryLoader.loadLibrary();
    }

    /** Policy string. */
    public final String policySrc;
    /** Policy ID. */
    public final String policyID;
    /** Annotations */
    private Map<String, String> annotations;

    /**
     * Creates a Cedar policy object.
     *
     * @param policy   String containing the source code of a Cedar policy in the Cedar policy language.
     * @param policyID The id of this policy. Must be unique. Note: We may flip the order of the arguments here for
     *                 idiomatic reasons.
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public Policy(@JsonProperty("policySrc") String policy, @JsonProperty("policyID") String policyID)
            throws NullPointerException {

        if (policy == null) {
            throw new NullPointerException("Failed to construct policy from null string");
        }
        if (policyID == null) {
            policyID = "policy" + ID_COUNTER.addAndGet(1);
        }
        this.policySrc = policy;
        this.policyID = policyID;
    }

    /**
     * Get the policy ID.
     */
    public String getID() {
        return policyID;
    }

    /**
     * Get the policy source.
     */
    public String getSource() {
        return policySrc;
    }

    @Override
    public String toString() {
        return "// Policy ID: " + policyID + "\n" + policySrc;
    }

    /**
     * Returns the effect of a policy.
     *
     * Determines the policy effect by attempting static policy first, then template. In future, it will only support
     * static policies once new class is introduced for Template.
     *
     * @return The effect of the policy, either "permit" or "forbid"
     * @throws InternalException
     * @throws NullPointerException
     */
    public Effect effect() throws InternalException, NullPointerException {
        try {
            return Effect.fromString(policyEffectJni(policySrc)); // Get effect for static policy
        } catch (InternalException e) {
            return Effect.fromString(templateEffectJni(policySrc)); // Get effect for template
        }
    }

    /**
     * Get the JSON representation of the policy. Currently only supports static policies.
     */
    public String toJson() throws InternalException, NullPointerException {
        return toJsonJni(policySrc);
    }

    public static Policy fromJson(String policyId, String policyJson) throws InternalException, NullPointerException {
        String policyText = fromJsonJni(policyJson);
        return new Policy(policyText, policyId);
    }

    public static Policy parseStaticPolicy(String policyStr) throws InternalException, NullPointerException {
        String policyText = parsePolicyJni(policyStr);
        return new Policy(policyText, null);
    }

    public static Policy parsePolicyTemplate(String templateStr) throws InternalException, NullPointerException {
        String templateText = parsePolicyTemplateJni(templateStr);
        return new Policy(templateText, null);
    }

    /**
     * Gets a copy of the policy annotations map. Annotations are loaded lazily when this method is first called. Works
     * for both static policies and templates.
     *
     * @return A new HashMap containing the policy's annotations. For annotations without explicit values, an empty
     *         string ("") is used as the value
     */
    public Map<String, String> getAnnotations() throws InternalException {
        ensureAnnotationsLoaded();
        return new HashMap<>(this.annotations);
    }

    /**
     * Gets the value of a specific annotation by its key.
     *
     * @param key The annotation key to look up
     * @return The value associated with the annotation key, or null if the key doesn't exist
     * @throws InternalException if there is an error loading or parsing the annotations
     */
    public String getAnnotation(String key) throws InternalException {
        ensureAnnotationsLoaded();
        return this.annotations.getOrDefault(key, null);
    }

    /**
     * Ensures that the annotations map is loaded for this policy. If annotations haven't been loaded yet, attempts to
     * load them first from static policy, then falls back to template if needed.
     *
     * @throws InternalException if there is an error loading or parsing the annotations
     */
    private void ensureAnnotationsLoaded() throws InternalException {
        if (annotations == null) {
            try {
                this.annotations = getPolicyAnnotationsJni(this.policySrc);
            } catch (InternalException e) {
                if (e.getMessage().contains("expected a static policy")) {
                    this.annotations = getTemplateAnnotationsJni(this.policySrc);
                } else {
                    throw e;
                }
            }
        }
    }

    private static native String parsePolicyJni(String policyStr) throws InternalException, NullPointerException;

    private static native String parsePolicyTemplateJni(String policyTemplateStr)
            throws InternalException, NullPointerException;

    private native String toJsonJni(String policyStr) throws InternalException, NullPointerException;

    private static native String fromJsonJni(String policyJsonStr) throws InternalException, NullPointerException;

    private native String policyEffectJni(String policyStr) throws InternalException, NullPointerException;

    private native String templateEffectJni(String policyStr) throws InternalException, NullPointerException;

    private static native Map<String, String> getPolicyAnnotationsJni(String policyStr) throws InternalException;

    private static native Map<String, String> getTemplateAnnotationsJni(String policyStr) throws InternalException;
}
