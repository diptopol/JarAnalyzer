package ca.concordia.jaranalyzer;

import org.junit.Test;

import java.util.List;

/**
 * @author Diptopol
 * @since 12/23/2020 9:54 PM
 */
public class TypeInferenceAPITest {

    @Test
    public void testGetQualifiedClassName() {
        List<String> qualifiedNameList = TypeInferenceAPI.getQualifiedClassName("AtomicLong");

        assert qualifiedNameList.size() == 1;
        assert "java.util.concurrent.atomic.AtomicLong".equals(qualifiedNameList.get(0));
    }
}