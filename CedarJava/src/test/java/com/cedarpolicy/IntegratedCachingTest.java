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

package com.cedarpolicy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.AuthorizationResponse.SuccessOrFailure;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.CacheException;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;

import org.junit.jupiter.api.Test;

public class IntegratedCachingTest {

    private static final EntityUID ALICE = new EntityUID(EntityTypeName.parse("User").get(), "alice");
    private static final EntityUID VIEW = new EntityUID(EntityTypeName.parse("Action").get(), "view");
    private static final EntityUID DOC = new EntityUID(EntityTypeName.parse("Resource").get(), "doc1");

    private PolicySet permitAll() {
        Set<Policy> policies = new HashSet<>();
        policies.add(new Policy("permit(principal, action, resource);", "policy0"));
        return new PolicySet(policies);
    }

    private PolicySet denyAll() {
        Set<Policy> policies = new HashSet<>();
        policies.add(new Policy("forbid(principal, action, resource);", "policy0"));
        return new PolicySet(policies);
    }

    private Set<Entity> emptyEntities() {
        return new HashSet<>();
    }

    private AuthorizationRequest request() {
        return new AuthorizationRequest(ALICE, VIEW, DOC, new HashMap<>());
    }

    @Test
    public void cachedPolicySetAllows() throws AuthException, CacheException {
        PolicySet ps = permitAll();
        ps.cache();
        var engine = new BasicAuthorizationEngine();
        AuthorizationResponse resp = engine.isAuthorized(request(), ps, emptyEntities());
        assertEquals(SuccessOrFailure.Success, resp.type);
        assertTrue(resp.success.get().isAllowed());
    }

    @Test
    public void cachedPolicySetDenies() throws AuthException, CacheException {
        PolicySet ps = denyAll();
        ps.cache();
        var engine = new BasicAuthorizationEngine();
        AuthorizationResponse resp = engine.isAuthorized(request(), ps, emptyEntities());
        assertEquals(SuccessOrFailure.Success, resp.type);
        assertFalse(resp.success.get().isAllowed());
    }

    @Test
    public void uncachedPolicySetStillWorks() throws AuthException, CacheException {
        PolicySet ps = permitAll();
        var engine = new BasicAuthorizationEngine();
        AuthorizationResponse resp = engine.isAuthorized(request(), ps, emptyEntities());
        assertEquals(SuccessOrFailure.Success, resp.type);
        assertTrue(resp.success.get().isAllowed());
    }

    @Test
    public void cacheKeyEmptyWhenNotCached() {
        PolicySet ps = permitAll();
        assertTrue(ps.cacheKey().isEmpty());
    }

    @Test
    public void cacheKeyPresentWhenCached() throws AuthException, CacheException {
        PolicySet ps = permitAll();
        ps.cache();
        assertTrue(ps.cacheKey().isPresent());
    }

    @Test
    public void cacheIsIdempotent() throws AuthException, CacheException {
        PolicySet ps = permitAll();
        ps.cache();
        String key1 = ps.cacheKey().get();
        ps.cache(); // second call should be no-op
        String key2 = ps.cacheKey().get();
        assertEquals(key1, key2);
    }

    @Test
    public void multipleCachedPolicySets() throws AuthException, CacheException {
        PolicySet permit = permitAll();
        PolicySet deny = denyAll();
        permit.cache();
        deny.cache();

        var engine = new BasicAuthorizationEngine();

        AuthorizationResponse permitResp = engine.isAuthorized(request(), permit, emptyEntities());
        assertEquals(SuccessOrFailure.Success, permitResp.type);
        assertTrue(permitResp.success.get().isAllowed());

        AuthorizationResponse denyResp = engine.isAuthorized(request(), deny, emptyEntities());
        assertEquals(SuccessOrFailure.Success, denyResp.type);
        assertFalse(denyResp.success.get().isAllowed());
    }

    @Test
    public void stressCacheMany() throws AuthException, CacheException {
        var engine = new BasicAuthorizationEngine();
        for (int i = 0; i < 500; i++) {
            PolicySet ps = permitAll();
            ps.cache();
            AuthorizationResponse resp = engine.isAuthorized(request(), ps, emptyEntities());
            assertEquals(SuccessOrFailure.Success, resp.type);
            assertTrue(resp.success.get().isAllowed());
            // ps goes out of scope — Cleaner frees the Rust cache entry
        }
    }
}
