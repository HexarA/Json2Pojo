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
        assertEquals("AbstractTest", GeneratePojos.formatClassName("abstractTest"));
        assertEquals("Piñata", GeneratePojos.formatClassName("piñata"));
        assertEquals("Test", GeneratePojos.formatClassName("1Test"));
        assertEquals("InvalidChars", GeneratePojos.formatClassName("Invalid@$%@#$^&#%@Chars"));
    }

    @Test
    public void formatFieldNameWithMPrefix() throws Exception {
        assertEquals("mTest", GeneratePojos.formatFieldName("test", true));
        assertEquals("mTest2", GeneratePojos.formatFieldName("test2", true));
        assertEquals("mTestWithUnderscores", GeneratePojos.formatFieldName("test_with_underscores", true));
        assertEquals("mTestWithHyphens", GeneratePojos.formatFieldName("test-with-hyphens", true));
        assertEquals("mAbstract", GeneratePojos.formatFieldName("abstract", true));
        assertEquals("mPiñata", GeneratePojos.formatFieldName("piñata", true));
        assertEquals("mTest", GeneratePojos.formatFieldName("1Test", true));
        assertEquals("mInvalidChars", GeneratePojos.formatFieldName("Invalid@$%@#$^&#%@Chars", true));
    }

    @Test
    public void formatFieldNameWithoutMPrefix() throws Exception {
        assertEquals("test", GeneratePojos.formatFieldName("test", false));
        assertEquals("test2", GeneratePojos.formatFieldName("test2", false));
        assertEquals("testWithUnderscores", GeneratePojos.formatFieldName("test_with_underscores", false));
        assertEquals("testWithHyphens", GeneratePojos.formatFieldName("test-with-hyphens", false));
        assertEquals("abstract", GeneratePojos.formatFieldName("abstract", false));
        assertEquals("piñata", GeneratePojos.formatFieldName("piñata", false));
        assertEquals("test", GeneratePojos.formatFieldName("1Test", false));
        assertEquals("invalidChars", GeneratePojos.formatFieldName("Invalid@$%@#$^&#%@Chars", false));
    }

}