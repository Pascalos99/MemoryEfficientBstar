package test;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({ Test_ArtificialGamePosition.class, Test_GameTreeNode.class, Test_SearchTreeNode.class, Test_BstarBasic.class, Test_BstarSquaredSimple.class })
public class AllTests {

}
