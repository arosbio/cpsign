/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.Version.InvalidVersionException;
import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestVersion {

    @Test
    public void testConstructors(){
        Version v = new Version(19, 5);
        Assert.assertEquals(19, v.getMajor());
        Assert.assertEquals(5, v.getMinor());
        Assert.assertEquals(0, v.getPatch());
        Assert.assertEquals("", v.getSuffix());

        try{
            new Version(-1, 0, 1);
            Assert.fail("Major must be >=0");
        } catch (InvalidVersionException e){}
        try{
            new Version(1, -3, 1);
            Assert.fail("Minor must be >=0");
        } catch (InvalidVersionException e){}

        try{
            new Version(1, 0, -1);
            Assert.fail("Patch must be >=0");
        } catch (InvalidVersionException e){}
    }

    @Test
    public void testEquals(){
        Version v = new Version(1, 5, 6);
        Assert.assertTrue(v.equals(v));
        Version v2 = new Version(1, 5, 6);
        Assert.assertTrue(v.equals(v2));

        Version v_suff = new Version(1, 5, 6,"-4561.x");
        Assert.assertFalse(v.equals(v_suff));

        // Some failing
        Assert.assertFalse(v.equals(new Version(1,6,5)));
    }

    @Test
    public void testParseMethod(){
        Version v = Version.parseVersion("1.0.5");
        Assert.assertEquals(new Version(1,0,5), v);

        v = Version.parseVersion("1.0.5-alpha2");
        Assert.assertEquals(new Version(1,0,5,"-alpha2"), v);

        String input = "1.0.10.alpha2.rxy";
        v = Version.parseVersion(input);
        Assert.assertEquals(new Version(1,0,10,".alpha2.rxy"), v);
        Assert.assertEquals(input,v.toString());

        input = "1.0.10-alpha2_rxy";
        v = Version.parseVersion(input);
        Assert.assertEquals(new Version(1,0,10,"-alpha2_rxy"), v);
        Assert.assertEquals(input,v.toString());
    }

    final private boolean printVersion=false;

	@Test
	public void testParseVersion() throws Exception{
		doParseCheck("1.1.1-rc1", 1,1,1,"-rc1");
		doParseCheck("1.1.1.rc1", 1,1,1,".rc1");
		doParseCheck("1.1.1_rc1", 1,1,1,"_rc1");
		doParseCheck("1.1.1.alpha.1", 1,1,1,".alpha.1");
		doParseCheck("1.1.1.alpha.1\n", 1,1,1,".alpha.1");
		doParseCheck("1.1", 1,1,0,"");
	}
	
	public void doParseCheck(String version, int major, int minor, int patch, String suff){
		Version v = Version.parseVersion(version);
		if(printVersion)
			System.out.println(v);
		Assert.assertEquals(major, v.getMajor());
		Assert.assertEquals(minor, v.getMinor());
		Assert.assertEquals(patch, v.getPatch());
		Assert.assertEquals(suff, v.getSuffix());
	}
	
	@Test
	public void testFailingVersions() {
		doCheckFailingVersion("0a.2.3");
		doCheckFailingVersion("a.2.3");
		doCheckFailingVersion("0.2a.3");
		doCheckFailingVersion("0.B.3");
		doCheckFailingVersion("0.2.Y");
		doCheckFailingVersion("0.fail.3");
	}
	
	public void doCheckFailingVersion(String version){
		try{
			Version.parseVersion(version);
			Assert.fail();
		} catch(IllegalArgumentException e){}
	}
	
	@Test
	public void testDefaultVersion() {
		Version v = Version.defaultVersion();
		if(printVersion)
			System.out.println(v);
		Assert.assertEquals(1, v.getMajor());
		Assert.assertEquals(0, v.getMinor());
		Assert.assertEquals(0, v.getPatch());
	}


}