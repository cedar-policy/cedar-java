package com.cedarpolicy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cedarpolicy.value.EntityIdentifier;
import net.jqwik.api.Property;

import net.jqwik.api.ForAll;

public class EntityIdTests {

    @Property
    void anyString(@ForAll String s) {
        var id = new EntityIdentifier(s);
        var asStr = id.getRepr();
        assertTrue(asStr.length() >= s.length());
    }

}
