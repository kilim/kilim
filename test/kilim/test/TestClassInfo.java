package kilim.test;

import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;
import kilim.analysis.ClassInfo;

public class TestClassInfo extends TestCase {
	public void testContains() throws Exception {
		List<ClassInfo> classInfoList = new LinkedList<ClassInfo>();
		
		ClassInfo classOne = new ClassInfo("kilim/S_01.class", "whocares".getBytes("UTF-8"));
		classInfoList.add(classOne);
		
		ClassInfo classTwo = new ClassInfo("kilim/S_01.class", "whocares".getBytes("UTF-8"));
		assertTrue(classInfoList.contains(classTwo));
	}
}
