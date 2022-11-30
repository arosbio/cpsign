/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.TestResources;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;
import com.arosbio.testutils.TestEnv;

@Category(UnitTest.class)
public class TestURIUtils extends TestEnv {

	@Test
	public void testGlob() throws Exception {
		File thisDir = new File("").getAbsoluteFile();
		URI logbackFile = new File(thisDir,"src/main/resources/logback.xml").toURI();
		List<URI> testXMLFiles = UriUtils.getGlobMatches("src/main/resources/*.xml");
		
		Assert.assertEquals(logbackFile.getPath(), testXMLFiles.get(0).getPath());
		Assert.assertEquals(logbackFile, testXMLFiles.get(0));

		// The pom.xml file in root of this repo
		List<URI> matches = UriUtils.getGlobMatches("*.xml");
		Assert.assertEquals(1,matches.size());

		Assert.assertEquals(new File(thisDir,"pom.xml").toURI(), matches.get(0));
	}

	@Test
	public void testGetResources() throws IOException {
		// Get the svmlight files from test resources folder
		String jsonTestGlob = "src/test/resources/**.txt";
		System.err.println(UriUtils.getResources(Arrays.asList(jsonTestGlob)));
		Assert.assertEquals(1,UriUtils.getResources(Arrays.asList(jsonTestGlob)).size());
		Assert.assertEquals(
			UriUtils.getResources(Arrays.asList(jsonTestGlob)).size()+1,
			UriUtils.getResources(Arrays.asList(jsonTestGlob, "https://www.facebook.com")).size());
		// printLogs();
	}
	
	

	@Test
	public void testVerifyURI() throws Exception {
		// HTTP
		URI restWebService = new URI("http://google.com");
		Assert.assertEquals("http",restWebService.getScheme());
		Assert.assertTrue(UriUtils.canReadFromURI(restWebService));
 
		// FILE
		URI realFile = TestResources.SVMLIGHTFiles.CLASSIFICATION_2CLASS_20.toURI();
		
		Assert.assertTrue(realFile.getScheme().equals("jar")||realFile.getScheme().equals("file"));
		Assert.assertTrue(UriUtils.canReadFromURI(realFile));

		File tmpFile = TestUtils.createTempFile("thisIsTmp", ".txt");
		//				System.out.println(tmpFile.getAbsolutePath());
		//				System.out.println(tmpFile.getPath());
		Assert.assertTrue(UriUtils.canReadFromURI(tmpFile.toURI()));
		Assert.assertTrue(UriUtils.canReadFromURI(new File(tmpFile.getAbsolutePath()).toURI()));
	}

	@Test
	public void testUsingTildePath() throws Exception {
		Path userHome = Paths.get(System.getProperty("user.home"));
		//		System.out.println(userHome);
		Path datasetsPath = Paths.get(this.getClass().getResource("/test folder/").toURI());
		Assert.assertTrue(datasetsPath.toFile().exists());
		//		System.out.println("chang exists: " + changPath.toFile().exists());
		//		System.out.println(changPath);

		Path relDatasets = userHome.relativize(datasetsPath);
		Assert.assertFalse("relative path should not contain Users",relDatasets.toString().contains("Users"));
		//		System.out.println("relative chang: " + relChang);

		URI uriFromTilde = UriUtils.getURI("~/"+relDatasets);

		// Remove trailing dashes (in case there is any)
		String dirStr = datasetsPath.toString();
		if (dirStr.endsWith("/")){
			dirStr = dirStr.substring(0, dirStr.length()-1);
		}
		String tildeStr = uriFromTilde.getPath();
		if (tildeStr.endsWith("/")){
			tildeStr = tildeStr.substring(0, tildeStr.length()-1);
		}
		Assert.assertEquals(dirStr, tildeStr);
		Assert.assertTrue(UriUtils.isLocalFile(uriFromTilde));
	}

	@Test
	public void testRelativePath() throws Exception {
		String relativePath = "src/test/resources/test folder";

		URI datasetsURI = UriUtils.getURI(relativePath);

		File datasetsFile = UriUtils.getIfLocalFile(datasetsURI);
		Assert.assertTrue(datasetsFile.exists());
		Assert.assertTrue(datasetsFile.isDirectory());
	}

	@Test
	public void testRelativePathStartingWithDot() throws Exception {
		String relativePath = "./src/test/resources/test folder/";

		URI datasetsURI = UriUtils.getURI(relativePath);
		File datasetsFile = UriUtils.getIfLocalFile(datasetsURI);
		Assert.assertTrue(datasetsFile.exists());
		Assert.assertTrue(datasetsFile.isDirectory());

	}

	@Test
	public void testRelativePathStartingWithMultipleDots() throws Exception {
		String relativePath = "../depict/pom.xml";

		URI datasetsURI = UriUtils.getURI(relativePath);
		File datasetsFile = UriUtils.getIfLocalFile(datasetsURI);
		Assert.assertTrue(datasetsFile.exists());
		Assert.assertTrue(datasetsFile.isFile());

	}

	@Test
	public void testGetResourceNameStripFileSuffix() throws Exception {
		File testFile = TestUtils.createTempFile("my_file", ".jar");

		String fullName = testFile.getName();
		String expectedName = fullName.substring(0,fullName.length()-4);
		Assert.assertEquals(expectedName, UriUtils.getResourceNameStripFileSuffix(testFile.toURI()));

		Assert.assertEquals("file", UriUtils.getResourceNameStripFileSuffix(new URI("https://www.google.com/resources/file.txt")));
		Assert.assertEquals("filename", UriUtils.getResourceNameStripFileSuffix(new URI("attachment:/resource%20number/filename")));
		Assert.assertEquals("project-name", UriUtils.getResourceNameStripFileSuffix(new URI("git://github.com/user/project-name.git")));
		Assert.assertEquals("some_file", UriUtils.getResourceNameStripFileSuffix(new URI("https://github.com/some_file.txt")));
		Assert.assertEquals("ames_126", UriUtils.getResourceNameStripFileSuffix(new URI("jar:file:/Users/star/.m2/repository/com/arosbio/test-utils/2.0.0-beta16-SNAPSHOT/test-utils-2.0.0-beta16-SNAPSHOT-tests.jar!/chem/classification/ames_126.sdf")));

		// URI uri = new URI("jar:file:/Users/star/.m2/repository/com/arosbio/test-utils/2.0.0-beta16-SNAPSHOT/test-utils-2.0.0-beta16-SNAPSHOT-tests.jar!/chem/classification/ames_126.sdf");
		// SYS_ERR.println("scheme: " + uri.getScheme());
		// SYS_ERR.println("fragment: " + uri.getFragment());
		// SYS_ERR.println("auth: " + uri.getAuthority());
		// SYS_ERR.println("query: " + uri.getQuery());
		// SYS_ERR.println("raw query: " + uri.getRawQuery());
		// SYS_ERR.println("schem spec part: " + uri.getSchemeSpecificPart());
		// String jarScheme = uri.getSchemeSpecificPart();
		// String jarEntry = jarScheme.substring(jarScheme.indexOf("!")+1);
		// SYS_ERR.println("entry: " + jarEntry);
		// URI ent = new File(jarEntry).toURI();
		// SYS_ERR.println("jar->file->uri->strip: " +UriUtils.getResourceNameStripFileSuffix(ent));
		// // URI subUri = new URI(uri.getPath());
		// // SYS_ERR.println(subUri.getPath());
		// URL url = uri.toURL();
		// SYS_ERR.println("url-file:" + url.getFile());
		// SYS_ERR.println(url.getPath());
		// SYS_ERR.println(uri.getPath());
		// SYS_ERR.println(uri.getScheme());

		// String name = UriUtils.getResourceNameStripFileSuffix(new URI("jar:file:/Users/star/.m2/repository/com/arosbio/test-utils/2.0.0-beta16-SNAPSHOT/test-utils-2.0.0-beta16-SNAPSHOT-tests.jar!/chem/classification/ames_126.sdf"));
		// SYS_ERR.print(name);
	}

	@Test
	public void testStripSuffix_methods(){
		Assert.assertEquals("my_file", UriUtils.stripSuffix("my_file.txt"));
		Assert.assertEquals("my_file.txt", UriUtils.stripGzipSuffix("my_file.txt.gz"));
		Assert.assertEquals("my_file", UriUtils.stripSuffix(UriUtils.stripGzipSuffix("my_file.txt.gz")));

		Assert.assertEquals("my_file.file1", UriUtils.stripSuffix(UriUtils.stripGzipSuffix("my_file.file1.txt")));
	}


}
