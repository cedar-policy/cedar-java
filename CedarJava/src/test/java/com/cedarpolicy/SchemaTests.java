package com.cedarpolicy;

import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.schema.Schema.JsonOrHuman;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SchemaTests {
    @Test
    public void parseJsonSchema() {
        assertDoesNotThrow(() -> {
            Schema.parse(JsonOrHuman.Json, "{}");
            Schema.parse(JsonOrHuman.Json, """
                    {
                        "Foo::Bar": {
                            "entityTypes": {},
                            "actions": {}
                        }
                    }
                    """);
            Schema.parse(JsonOrHuman.Json, """
                    {
                        "": {
                            "entityTypes": {
                                "User": {
                                    "shape": {
                                        "type": "Record",
                                        "attributes": {
                                            "name": {
                                                "type": "String",
                                                "required": true
                                            },
                                            "age": {
                                                "type": "Long",
                                                "required": false
                                            }
                                        }
                                    }
                                },
                                "Photo": {
                                    "memberOfTypes": [ "Album" ]
                                },
                                "Album": {}
                            },
                            "actions": {
                                "view": {
                                    "appliesTo": {
                                        "principalTypes": ["User"],
                                        "resourceTypes": ["Photo", "Album"]
                                    }
                                }
                            }
                        }
                    }
                    """);
        });
        assertThrows(Exception.class, () -> {
            Schema.parse(JsonOrHuman.Json, "{\"foo\": \"bar\"}");
            Schema.parse(JsonOrHuman.Json, "namespace Foo::Bar;");
        });
    }

    @Test
    public void parseHumanSchema() {
        assertDoesNotThrow(() -> {
            Schema.parse(JsonOrHuman.Human, "");
            Schema.parse(JsonOrHuman.Human, "namespace Foo::Bar {}");
            Schema.parse(JsonOrHuman.Human, """
                    entity User = {
                        name: String,
                        age?: Long,
                    };
                    entity Photo in Album;
                    entity Album;
                    action view
                      appliesTo { principal: [User], resource: [Album, Photo] };
                    """);
        });
        assertThrows(Exception.class, () -> {
            Schema.parse(JsonOrHuman.Human, """
                    {
                        "Foo::Bar": {
                            "entityTypes" {},
                            "actions": {}
                        }
                    }
                    """);
            Schema.parse(JsonOrHuman.Human, "namspace Foo::Bar;");
        });
    }
}
