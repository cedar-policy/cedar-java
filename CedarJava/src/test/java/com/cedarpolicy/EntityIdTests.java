import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cedarpolicy.value.EntityIdentifier;
import net.jqwik.api.Property;

import net.jqwik.api.ForAll;

public class EntityIdTests {

    @Property
    void anyString(@ForAll String s) {
        var id = new EntityIdentifier(s);
        var as_str = id.getRepr();
        assertTrue(as_str.length() >= s.length());
    }

}
