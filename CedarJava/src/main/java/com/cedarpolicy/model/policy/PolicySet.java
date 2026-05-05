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

import static com.cedarpolicy.CedarJson.objectWriter;
import com.cedarpolicy.SharedCedarInternals;
import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.exception.CacheException;
import com.cedarpolicy.model.exception.InternalException;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Policy set containing policies in the Cedar language. */
public class PolicySet {
    private static final String PROP_MAX_CACHED = "cedar.cache.maxPolicySets";
    private static final int DEFAULT_MAX_CACHED = 1024;

    static {
        LibraryLoader.loadLibrary();
        String maxProp = System.getProperty(PROP_MAX_CACHED);
        setCacheMaxPolicySets(maxProp != null ? Integer.parseInt(maxProp) : DEFAULT_MAX_CACHED);
    }

    /** Static policies */
    public Set<Policy> policies;

    /** Template-linked policies */
    public List<TemplateLink> templateLinks;

    /** Policy templates */
    public Set<Policy> templates;

    public PolicySet() {
        this.policies = Collections.emptySet();
        this.templates = Collections.emptySet();
        this.templateLinks = Collections.emptyList();
    }

    public PolicySet(Set<Policy> policies) {
        this.policies = policies;
        this.templates = Collections.emptySet();
        this.templateLinks = Collections.emptyList();
    }

    public PolicySet(Set<Policy> policies, Set<Policy> templates) {
        this.policies = policies;
        this.templates = templates;
        this.templateLinks = Collections.emptyList();
    }

    public PolicySet(Set<Policy> policies, Set<Policy> templates, List<TemplateLink> templateLinks) {
        this.policies = policies;
        this.templates = templates;
        this.templateLinks = templateLinks;
    }

    /**
     * Get the static policies in the policy set.
     *
     * @return A map from policy id to `Policy` object
     */
    public Map<String, String> getStaticPolicies() {
        return policies.stream().collect(Collectors.toMap(Policy::getID, Policy::getSource));
    }

    /**
     * Get the templates in the policy set.
     *
     * @return A map from policy id to `Policy` object
     */
    public Map<String, String> getTemplates() {
        return templates.stream().collect(Collectors.toMap(Policy::getID, Policy::getSource));
    }

    /**
     * Gets number of static policies in the Policy Set.
     *
     * @return number of static policies, returns 0 if policies set is null
     */
    public int getNumPolicies() {
        return policies != null ? policies.size() : 0;
    }

    /**
     * Gets number of templates in the Policy Set.
     *
     * @return number of templates, returns 0 if templates set is null
     */
    public int getNumTemplates() {
        return templates != null ? templates.size() : 0;
    }

    /**
      * Converts the PolicySet object to a Cedar JSON string representation.
      *
      * @return Cedar JSON string representation of the PolicySet
      * @throws InternalException if there is an error during JSON conversion in the Rust native code
      * @throws JsonProcessingException if there is an error serializing the object to JSON
      */
      public String toJson() throws InternalException, JsonProcessingException {
        return policySetToJson(objectWriter().writeValueAsString(this));
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

    // --- Caching support ---

    private volatile String cacheId;

    /**
     * Mark this policy set for caching on the Rust side. The policies are
     * pre-parsed immediately and reused on subsequent authorization calls.
     * The cached data is automatically freed when this object is garbage collected.
     *
     * <p>If called again after mutation, the cache is updated with the current
     * state. If the content has not changed, calling this method again is a
     * no-op from a correctness standpoint (the policies are re-parsed on the
     * Rust side).
     *
     * <p>For the cached path to be used during authorization, both the policy set
     * and any associated schema must be cached. If only the policy set is cached
     * but the schema is not, authorization will fall back to the uncached path.
     *
     * @throws CacheException if the policies fail to parse during caching.
     */
    public synchronized void cache() throws CacheException {
        String oldId = cacheId;
        String id = (oldId != null) ? oldId : UUID.randomUUID().toString();
        preparseOnRustSide(id);
        if (oldId == null) {
            cacheId = id;
            SharedCedarInternals.registerCleanup(this, new PolicySetCacheCleanup(id));
        }
    }

    /**
     * Get the cache key for this policy set, if cached.
     *
     * @return The cache key if cached, or empty if not cached.
     */
    public Optional<String> cacheKey() {
        String id = cacheId;
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    private void preparseOnRustSide(String id) throws CacheException {
        try {
            String policiesJson = objectWriter().writeValueAsString(this);
            preparsePolicySetJni(id, policiesJson);
        } catch (JsonProcessingException e) {
            throw new CacheException("JSON Serialization Error", e);
        } catch (InternalException e) {
            throw new CacheException("Failed to cache policy set", e);
        }
    }

    private static final class PolicySetCacheCleanup implements Runnable {
        private final String id;

        PolicySetCacheCleanup(String id) {
            this.id = id;
        }

        @Override
        public void run() {
            removeCachedPolicySetJni(id);
        }
    }

    private static native PolicySet parsePoliciesJni(String policiesStr) throws InternalException, NullPointerException;
    private static native String policySetToJson(String policySetStr) throws InternalException, NullPointerException;
    private static native void preparsePolicySetJni(String id, String policiesJson) throws InternalException;
    private static native void removeCachedPolicySetJni(String id);
    private static native void setCacheMaxPolicySets(int max);
}
