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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UriUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(UriUtils.class);

	public static boolean isLocalPath(URI uri) throws IOException {
		if (uri == null) {
			throw new IOException("URI cannot be null");
		}
		try{
			String scheme = uri.getScheme();
			if (scheme == null || scheme.equals("file")){
				String rawPath = uri.getRawPath();
				Paths.get(URLDecoder.decode(rawPath, IOSettings.CHARSET.name()));
				return true;
			}
		} catch (InvalidPathException | UnsupportedEncodingException e){

		}
		return false;
	}



	public static boolean isLocalFile(URI uri) throws IllegalArgumentException {
		if (uri == null)
			throw new IllegalArgumentException("No URI given");
		return getIfLocalFile(uri) != null;
	}

	/**
	 * Either return a reference to a local {@link java.io.File File} or downloads a non-local
	 * URI to a local temporary file, and returns the {@link java.io.File File} reference to it
	 * @param uri the uri
	 * @return a {@link File} instance, either the same URI that was given, or downloaded to a local file
	 * @throws IOException invalid URI, issues reading it or downloading from remote location
	 */
	public static File getFile(URI uri) throws IOException {
		File theFile = null;

		// If it is a uri for a local file (prefix "file:")
		theFile = getIfLocalFile(uri);
		if (theFile != null && theFile.exists()) {
			return theFile;
		}

		// This is a non-local uri
		return downloadToLocalFile(uri);
	}


	public static URI getURI(String pathOrUri) throws IllegalArgumentException, IOException {
		LOGGER.trace("getting URI from path/URI: {}", pathOrUri);
		URI foundURI = null;

		if (pathOrUri == null)
			throw new IOException("No URI or file given");

		// Attempting first as a fully formatted URI
		if (pathOrUri.matches("\\w+:.+") && ! pathOrUri.contains("\\")){
			// This should be a URI
			try {
				foundURI = new URI(pathOrUri);
			} catch (URISyntaxException e) {
				LOGGER.debug("Failed trying to convert string: {} to URI", pathOrUri, e);
				throw new IllegalArgumentException("URI malformatted: " + pathOrUri);
			}
			if (! canReadFromURI(foundURI))
				throw new IOException("Cannot read from URI: " + pathOrUri);

			return foundURI;
		} 


		// If not a URI - check as a file
		LOGGER.trace("The argument should specify a file");
		File resolvedFile = new File(resolvePath(pathOrUri));
		LOGGER.trace("Resolved path: {}", resolvedFile);

		if(! resolvedFile.canRead())
			throw new IOException("Cannot read from file: " + pathOrUri);
		return resolvedFile.toURI();

	}

	/**
	 * Assumes that the URI has been properly 'fixed' before, so that relative paths has been
	 * changed into a proper file path
	 * @param uri the uri
	 * @return a {@link File} if the URI specifies a local file, otherwise <code>null</code>
	 */
	public static File getIfLocalFile(URI uri) {

		try {
			String scheme = uri.getScheme();
			if (scheme == null || scheme.equals("file")){
				String rawPath = uri.getRawPath();
				return new File(URLDecoder.decode(rawPath, IOSettings.CHARSET.name()));
			}
		} catch (IllegalArgumentException | UnsupportedEncodingException e){}

		return null;
	}

	/**
	 * Download a remote URI into a local file, marks the file to be deleted once the JVM
	 * exits 
	 * @param uri the resource to download
	 * @return the file of the downloaded resource
	 * @throws IOException issues reading/downloading the resource
	 */
	public static File downloadToLocalFile(URI uri) throws IOException {
		try {
			// Copy into a local file
			File localFile = File.createTempFile("tmpJarFile", ".file");
			localFile.deleteOnExit();
			FileUtils.copyURLToFile(uri.toURL(), localFile);
			LOGGER.debug("copied non-local file to local file");
			return localFile;
		} catch (IOException e) {
			LOGGER.debug("Failed downloading uri: {}", uri);
			throw new IOException("Failed downloading file: " + uri);
		}

	}

	/**
	 * Resolve a given path, which may be relative or user home-relative (starting with ~ in unix type OS)
	 * @param path an absolute path, absolute path using ~/ or relative path
	 * @return the absolute path 
	 * @throws IOException In case the path cannot be resolved 
	 */
	public static String resolvePath(String path) throws IOException {

		// Relative user home
		if (path.startsWith("~/")){
			Path userHome = Paths.get(System.getProperty("user.home"));
			if (userHome == null)
				throw new IOException("Path relative to user-home given (but no user-home set for the system): " + path);
			//			try {
			return userHome.resolve(path.substring(2)).toAbsolutePath().normalize().toString();
		}

		// Resolved absolute and relative paths
		return Paths.get(path).toAbsolutePath().normalize().toString();

	}

	/**
	 * Checks if an already resolved path exists
	 * @param path an absolute path
	 * @return {@code true} if the file exists
	 * @see #resolvePath(String)
	 */
	public static boolean verifyLocalFileExists(String path) {
		try {
			return new File(path).exists();
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Note that this returns a non-buffered InputStream. Buffering of the stream is up to the caller
	 * @param uri the resource to read from
	 * @return a stream to read from 
	 * @throws IOException any issues reading from {@code uri} parameter
	 */
	public static InputStream getInputStream(URI uri) throws IOException {
		return uri.toURL().openStream();
	}

	public static boolean canReadFromURI(URI uri) {
		try(
				InputStream is = uri.toURL().openStream()
				){
			is.read();
		} catch (IOException e){
			return false;
		}
		return true;
	}

	public static boolean verifyURINonEmpty(URI uri) {
		try(
				InputStream is = uri.toURL().openStream()
				){
			if(is.read() == -1) // -1 means end of stream, i.e. empty
				return false;
		} catch (IOException e){
			return false;
		}
		return true;
	}

	public static void createParentOfFile(File childFile) throws IOException {
		File parent = childFile.getParentFile();
		if (parent!=null){
			if (! parent.exists()){
				try{
					FileUtils.forceMkdir(parent);
				} catch (IOException e){
					throw new IOException("Could not create parent file for "+childFile);
				}
			}
		}
	}

	public static List<URI> getResources(List<String> resources) throws IOException {
		List<URI> all = new ArrayList<>();
		for (String res: resources)
			all.addAll(getResources(res));
		return all;
	}

	public static List<URI> getResources(String resources) throws IOException {
		if (resources == null)
			throw new IOException("Cannot resolve null as resource");
		resources = resources.trim();
		if (resources.isEmpty())
			throw new IOException("Cannot resolve empty input as resource");

		// Try as a valid URI
		if (resources.matches("\\w+:.+") && ! resources.contains("\\")){
			LOGGER.debug("resource should be a URI={}",resources);
			try {
				URI uri = URI.create(resources);
				return Arrays.asList(uri);
			} catch(IllegalArgumentException e) {
				LOGGER.debug("Could not parse resource as URI");
			}
		}

		// Check for glob/file input
		try {
			return getGlobMatches(resources);
		} catch (IllegalArgumentException e) {
			LOGGER.debug("Could not parse resource as glob");
		}

		throw new IllegalArgumentException(resources + " cannot be resolved to either a URI or file resource");

	}

	public static String getResourceNameStripFileSuffix(final URI uri){
		// easy case - if it's a local file
		File localFile = getIfLocalFile(uri);
		if (localFile != null){
			return stripSuffix(stripGzipSuffix(localFile.getName()));
		}
		// Not a local file - need to treat it as a proper URI

		// Special treat if JAR scheme
		if ("jar".equals(uri.getScheme())){
			LOGGER.debug("getting resource name from JAR input URI");
			String jarScheme = uri.getSchemeSpecificPart(); // JAR URIs formed like: jar:<url>!/[<entry>]
			String jarEntry = jarScheme.substring(jarScheme.indexOf("!")+1); // Take out the <entry> 
			String name = getResourceNameStripFileSuffix(new File(jarEntry).toURI());
			if (name != null && !name.isBlank()) {
				return name;
			}
		}


		String path = uri.getPath();
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash>=0){
			return stripSuffix(stripGzipSuffix(path.substring(lastSlash+1)));
		}
		// No slashes 
		return stripSuffix(stripGzipSuffix(path));
	}

	public static String stripGzipSuffix(String name){
		String lowerCase = name.toLowerCase();
		if (lowerCase.endsWith(".gz")){
			return name.substring(0, name.length() - 3);
		} else if (lowerCase.endsWith(".gzip")){
			return name.substring(0, name.length()-5);
		}
		return name; // no gzip ending
	}
	/**
	 * Strip the file-suffix if there is one. E.g. {@code .jar}, {@code .txt} or anything else
	 * @param name the resource name to process
	 * @return the name, excluding the _last_ suffix 
	 */
	public static String stripSuffix(String name){
		int lastDot = name.lastIndexOf('.');
		if (lastDot > 0){
			return name.substring(0, lastDot);
		}
		return name; // No dot - return full name
	}

	public static List<URI> getGlobMatches(final String glob) throws IOException {

		String resolvedPath = resolvePath(glob); 
		Path directory = Paths.get(resolvedPath.split("[*?\\[{]")[0]);

		if (!Files.exists(directory))
			directory = directory.getParent();
		LOGGER.debug("base directory for glob-searching={}",directory);

		try {
			GlobFinderVisitor treeVisitor = new GlobFinderVisitor(resolvedPath);
			Files.walkFileTree(directory, treeVisitor);
			return treeVisitor.matches;
		} catch(Exception e) {
			throw new IOException(glob +" was not a valid glob-expression");
		}
	}

	private static class GlobFinderVisitor
	extends SimpleFileVisitor<Path> {

		private final List<URI> matches = new ArrayList<>();
		private final PathMatcher matcher;

		GlobFinderVisitor(final String pattern){
			matcher = FileSystems.getDefault()
					.getPathMatcher("glob:" + pattern);
		}

		/**
		 * Invokes the pattern matching on each file
		 */
		@Override
		public FileVisitResult visitFile(Path file,
				BasicFileAttributes attrs) {
			if (matcher.matches(file)) {
				matches.add(file.toUri());
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file,
				IOException exc) {
			return FileVisitResult.CONTINUE;
		}
	}

}
