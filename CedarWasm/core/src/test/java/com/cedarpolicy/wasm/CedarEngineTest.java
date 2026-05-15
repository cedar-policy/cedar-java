package com.cedarpolicy.wasm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CedarEngineTest {

    private static CedarEngine engine;

    @BeforeAll
    static void setUp() {
        engine = CedarEngine.create();
    }

    @Test
    void cedarVersion() {
        assertEquals("4.0", engine.getVersion());
    }

    @Test
    void simplePermit() {
        String request = """
                {
                  "principal": {"type": "User", "id": "alice"},
                  "action": {"type": "Action", "id": "view"},
                  "resource": {"type": "Resource", "id": "doc1"},
                  "context": {},
                  "policies": {
                    "staticPolicies": {"p0": "permit(principal,action,resource);"},
                    "templates": {},
                    "templateLinks": []
                  },
                  "entities": []
                }
                """;
        String result = engine.authorize(request);
        assertTrue(result.contains("\"decision\":\"allow\""), "Expected allow, got: " + result);
    }

    @Test
    void simpleForbid() {
        String request = """
                {
                  "principal": {"type": "User", "id": "alice"},
                  "action": {"type": "Action", "id": "view"},
                  "resource": {"type": "Resource", "id": "doc1"},
                  "context": {},
                  "policies": {
                    "staticPolicies": {"p0": "forbid(principal,action,resource);"},
                    "templates": {},
                    "templateLinks": []
                  },
                  "entities": []
                }
                """;
        String result = engine.authorize(request);
        assertTrue(result.contains("\"decision\":\"deny\""), "Expected deny, got: " + result);
    }

    @Test
    void parsePolicy() {
        String result = engine.parsePolicy("permit(principal,action,resource);");
        assertFalse(result.startsWith("ERROR:"), "Parse failed: " + result);
        assertTrue(result.contains("permit"), "Expected permit in parsed output: " + result);
    }

    @Test
    void parsePolicyInvalid() {
        String result = engine.parsePolicy("not a valid policy");
        assertTrue(result.startsWith("ERROR:"), "Expected error for invalid policy");
    }

    @Test
    void policyEffect() {
        assertEquals("permit", engine.policyEffect("permit(principal,action,resource);"));
        assertEquals("forbid", engine.policyEffect("forbid(principal,action,resource);"));
    }

    @Test
    void parseTemplate() {
        String result = engine.parseTemplate(
                "permit(principal==?principal,action,resource==?resource);");
        assertFalse(result.startsWith("ERROR:"), "Parse failed: " + result);
    }

    @Test
    void templateEffect() {
        assertEquals("permit",
                engine.templateEffect("permit(principal==?principal,action,resource==?resource);"));
    }

    @Test
    void policyToJsonAndBack() {
        String policyText = "permit(principal,action,resource);";
        String json = engine.policyToJson(policyText);
        assertFalse(json.startsWith("ERROR:"), "toJson failed: " + json);

        String roundTripped = engine.policyFromJson(json);
        assertFalse(roundTripped.startsWith("ERROR:"), "fromJson failed: " + roundTripped);
        assertTrue(roundTripped.contains("permit"));
    }

    @Test
    void policyAnnotations() {
        String policy = "@id(\"myPolicy\") @myKey(\"myValue\") permit(principal,action,resource);";
        String annotations = engine.getPolicyAnnotations(policy);
        assertFalse(annotations.startsWith("ERROR:"), "annotations failed: " + annotations);
        assertTrue(annotations.contains("\"id\""), "Expected 'id' annotation");
        assertTrue(annotations.contains("\"myPolicy\""), "Expected 'myPolicy' value");
        assertTrue(annotations.contains("\"myKey\""), "Expected 'myKey' annotation");
    }

    @Test
    void parseJsonSchema() {
        String schema = """
                {
                  "": {
                    "entityTypes": {
                      "User": {"memberOfTypes": ["Group"]},
                      "Group": {},
                      "File": {}
                    },
                    "actions": {
                      "read": {
                        "appliesTo": {
                          "principalTypes": ["User"],
                          "resourceTypes": ["File"]
                        }
                      }
                    }
                  }
                }
                """;
        String result = engine.parseJsonSchema(schema);
        assertEquals("success", result);
    }

    @Test
    void parseCedarSchema() {
        String schema = """
                entity User = { name: String };
                entity Photo;
                action view appliesTo {
                    principal: [User],
                    resource: [Photo]
                };
                """;
        String result = engine.parseCedarSchema(schema);
        assertEquals("success", result);
    }

    @Test
    void formatPolicies() {
        String result = engine.formatPolicies("permit(principal,action,resource);");
        assertFalse(result.startsWith("ERROR:"), "format failed: " + result);
        assertTrue(result.contains("permit"));
    }

    @Test
    void validatePolicies() {
        String request = """
                {
                  "schema": {
                    "": {
                      "entityTypes": {
                        "User": {},
                        "Photo": {}
                      },
                      "actions": {
                        "viewPhoto": {
                          "appliesTo": {
                            "principalTypes": ["User"],
                            "resourceTypes": ["Photo"]
                          }
                        }
                      }
                    }
                  },
                  "policies": {
                    "staticPolicies": {
                      "p0": "permit(principal == User::\\"alice\\", action == Action::\\"viewPhoto\\", resource);"
                    },
                    "templates": {},
                    "templateLinks": []
                  }
                }
                """;
        String result = engine.validate(request);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void cachedAuthorization() {
        String policiesJson = """
                {"staticPolicies": {"p0": "permit(principal,action,resource);"}, "templates": {}, "templateLinks": []}
                """;
        String parseResult = engine.preParsePolicySet("test-policies", policiesJson);
        assertFalse(parseResult.contains("errors"), "preparse failed: " + parseResult);

        String request = """
                {
                  "principal": {"type": "User", "id": "alice"},
                  "action": {"type": "Action", "id": "view"},
                  "resource": {"type": "Resource", "id": "doc1"},
                  "context": {},
                  "preparsedPolicySetId": "test-policies",
                  "validateRequest": false,
                  "entities": []
                }
                """;
        String result = engine.statefulAuthorize(request);
        assertTrue(result.contains("\"decision\":\"allow\""), "Expected allow, got: " + result);
    }
}
