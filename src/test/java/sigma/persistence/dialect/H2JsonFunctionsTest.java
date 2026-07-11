package sigma.persistence.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class H2JsonFunctionsTest {

    @Test
    @DisplayName("jsonValue extracts string field")
    void testJsonValueString() {
        assertEquals("Laptop", H2JsonFunctions.jsonValue("{\"name\":\"Laptop\"}", "$.name"));
    }

    @Test
    @DisplayName("jsonValue extracts number as text")
    void testJsonValueNumber() {
        assertEquals("42.5", H2JsonFunctions.jsonValue("{\"price\":42.5}", "$.price"));
    }

    @Test
    @DisplayName("jsonValue extracts boolean as text")
    void testJsonValueBoolean() {
        assertEquals("true", H2JsonFunctions.jsonValue("{\"inStock\":true}", "$.inStock"));
    }

    @Test
    @DisplayName("jsonValue resolves nested paths")
    void testJsonValueNestedPath() {
        assertEquals("NYC", H2JsonFunctions.jsonValue("{\"address\":{\"city\":\"NYC\"}}", "$.address.city"));
    }

    @Test
    @DisplayName("jsonValue returns null for missing field")
    void testJsonValueMissingField() {
        assertNull(H2JsonFunctions.jsonValue("{\"name\":\"x\"}", "$.other"));
    }

    @Test
    @DisplayName("jsonValue returns null for object values")
    void testJsonValueObjectValue() {
        assertNull(H2JsonFunctions.jsonValue("{\"nested\":{\"a\":1}}", "$.nested"));
    }

    @Test
    @DisplayName("jsonValue returns null for JSON null and invalid input")
    void testJsonValueNullAndInvalid() {
        assertNull(H2JsonFunctions.jsonValue("{\"x\":null}", "$.x"));
        assertNull(H2JsonFunctions.jsonValue("not json", "$.x"));
        assertNull(H2JsonFunctions.jsonValue(null, "$.x"));
        assertNull(H2JsonFunctions.jsonValue("{}", null));
    }

    @Test
    @DisplayName("jsonQuery extracts objects and arrays as JSON text")
    void testJsonQueryContainers() {
        assertEquals("{\"a\":1}", H2JsonFunctions.jsonQuery("{\"nested\":{\"a\":1}}", "$.nested"));
        assertEquals("[1,2]", H2JsonFunctions.jsonQuery("{\"items\":[1,2]}", "$.items"));
    }

    @Test
    @DisplayName("jsonQuery returns null for scalars and missing fields")
    void testJsonQueryScalars() {
        assertNull(H2JsonFunctions.jsonQuery("{\"name\":\"x\"}", "$.name"));
        assertNull(H2JsonFunctions.jsonQuery("{\"name\":\"x\"}", "$.other"));
    }

    @Test
    @DisplayName("jsonNumber extracts numeric values")
    void testJsonNumber() {
        assertEquals(42.5, H2JsonFunctions.jsonNumber("{\"price\":42.5}", "$.price"));
        assertEquals(7.0, H2JsonFunctions.jsonNumber("{\"count\":7}", "$.count"));
    }

    @Test
    @DisplayName("jsonNumber returns null for non-numeric values")
    void testJsonNumberNonNumeric() {
        assertNull(H2JsonFunctions.jsonNumber("{\"name\":\"x\"}", "$.name"));
        assertNull(H2JsonFunctions.jsonNumber("{\"name\":\"x\"}", "$.missing"));
        assertNull(H2JsonFunctions.jsonNumber("broken", "$.x"));
    }
}
