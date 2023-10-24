package com.cedarpolicy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.lang.NullPointerException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import java.util.List;

import org.junit.jupiter.api.Assertions;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.cedarpolicy.value.EntityTypeName;

public class EntityTypeNameTests {

    @Test
    public void simpleExample() {
        var o = EntityTypeName.parse("hello");
        assertTrue(o.isPresent());
        assertEquals(o.get().getBaseName(), "hello");
    }

    @Test
    public void simpleWithNamespace() {
        var o = EntityTypeName.parse("hello::world");
        assertTrue(o.isPresent());
        assertEquals(o.get().getBaseName(), "world");
        var l = o.get().getNamespaceComponents().collect(Collectors.toList());
        assertEquals(l.size(), 1);
        assertEquals(l.get(0), "hello");
    }

    @Test
    public void simpleWithNestedNamespace() {
        var o = EntityTypeName.parse("com::cedarpolicy::value::EntityTypeName");
        assertTrue(o.isPresent());
        assertEquals(o.get().getBaseName(), "EntityTypeName");
        var l = o.get().getNamespaceComponents().collect(Collectors.toList());
        assertEquals(l.size(), 3);
        assertEquals(l.get(0), "com");
        assertEquals(l.get(1), "cedarpolicy");
        assertEquals(l.get(2), "value");
    }

    @Test
    public void simpleInvalid() {
        var o = EntityTypeName.parse("[]");
        assertFalse(o.isPresent());
    }

    @Test
    public void partInvalid() {
        var o = EntityTypeName.parse("foo::bar::bad!#f::another");
        assertFalse(o.isPresent());
    }

    @Test
    public void nullSafety() { 
        assertThrows(NullPointerException.class, 
        () -> EntityTypeName.parse(null),
        "Null pointer exception should be thrown"
        );
    }

    @Property
    public void equalNull(@ForAll @From("multiLevelName") EntityTypeName n) { 
        assertFalse(n.equals(null));
    }

    @Test
    public void emptyString() { 
        assertFalse(EntityTypeName.parse("").isPresent());
    }

    @Property 
    public void roundTrip(@ForAll @From("multiLevelName") EntityTypeName name) { 
        var s = name.toString(); 
        var o = EntityTypeName.parse(s); 
        assertTrue(o.isPresent());
        assertEquals(o.get(), name); 
        assertEquals(o.get().hashCode(), name.hashCode());
        assertEquals(s, o.get().toString());
    }

    @Property 
    public void singleLevelRoundTrip(@ForAll @From("validName") String name) { 
        var o = EntityTypeName.parse(name);
        assertTrue(o.isPresent());
        var e = o.get(); 
        assertEquals(e.toString(), name); 
        assertEquals(EntityTypeName.parse(e.toString()).get(), e);
    }
    

    @Provide
    public static Arbitrary<EntityTypeName> multiLevelName() { 
        Arbitrary<List<String>> namespace = validName().collect(lst -> lst.size() >= 1 );
        return namespace.map(parts -> 
            EntityTypeName
                .parse(parts
                    .stream()
                    .collect(Collectors.joining("::")))
                .get());
    }

    @Provide
    public static Arbitrary<String> validName() { 
        var first = Arbitraries.chars().alpha();
        var rest = Arbitraries.strings().alpha().numeric().ofMinLength(0);
        return Combinators.combine(first, rest).as((f,r) -> f + r);
    }

}
