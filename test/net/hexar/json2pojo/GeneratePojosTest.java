package net.hexar.json2pojo;

import org.junit.Test;

import static org.junit.Assert.*;


public class GeneratePojosTest {

    @Test
    public void formatClassName() throws Exception {
        assertEquals("Test", GeneratePojos.formatClassName("test"));
        assertEquals("Test2", GeneratePojos.formatClassName("test2"));
        assertEquals("TestWithUnderscores", GeneratePojos.formatClassName("test_with_underscores"));
        assertEquals("TestWithHyphens", GeneratePojos.formatClassName("test-with-hyphens"));
        assertEquals("Abstract", GeneratePojos.formatClassName("abstract"));
        assertEquals("Piñata", GeneratePojos.formatClassName("piñata"));
        assertEquals("Test", GeneratePojos.formatClassName("1Test"));
        assertEquals("InvalidChars", GeneratePojos.formatClassName("Invalid@$%@#$^&#%@Chars"));
    }

    @Test
    public void formatFieldName() throws Exception {
        assertEquals("mTest", GeneratePojos.formatFieldName("test"));
        assertEquals("mTest2", GeneratePojos.formatFieldName("test2"));
        assertEquals("mTestWithUnderscores", GeneratePojos.formatFieldName("test_with_underscores"));
        assertEquals("mTestWithHyphens", GeneratePojos.formatFieldName("test-with-hyphens"));
        assertEquals("mAbstract", GeneratePojos.formatFieldName("abstract"));
        assertEquals("mPiñata", GeneratePojos.formatFieldName("piñata"));
        assertEquals("mTest", GeneratePojos.formatFieldName("1Test"));
        assertEquals("mInvalidChars", GeneratePojos.formatFieldName("Invalid@$%@#$^&#%@Chars"));
    }
}