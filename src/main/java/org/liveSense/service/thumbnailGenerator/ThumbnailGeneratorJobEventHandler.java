/*
 *  Copyright 2010 Robert Csakany <robson@semmi.se>.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

package org.liveSense.service.thumbnailGenerator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.event.EventUtil;
import org.apache.sling.event.jobs.JobProcessor;
import org.apache.sling.event.jobs.JobUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.liveSense.core.AdministrativeService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jhlabs.image.ScaleFilter;

@Component(label="%thumbnailGeneratorJobEventHandler.name",
        description="%thumbnailGeneratorJobEventHandler.description",
        immediate=true,
        metatype=true,
        policy=ConfigurationPolicy.OPTIONAL)
@Service(value = org.osgi.service.event.EventHandler.class)
@Property(name = "event.topics", value = {
		ThumbnailGeneratorResourceChangeListener.THUMBNAIL_GENERATE_TOPIC,
		ThumbnailGeneratorResourceChangeListener.THUMBNAIL_REMOVE_TOPIC })
public class ThumbnailGeneratorJobEventHandler extends AdministrativeService
		implements JobProcessor, EventHandler {

	/**
	 * default log
	 */
	private final Logger log = LoggerFactory
			.getLogger(ThumbnailGeneratorJobEventHandler.class);

	public static final String PARAM_THUMBNAIL_RESOLUTIONS = "thumbnailResolutions";
	public static final String THUMBNAIL_RESOLUTION_50_0 = "50x0";
	public static final String THUMBNAIL_RESOLUTION_100_0 = "100x0";
	public static final String THUMBNAIL_RESOLUTION_200_0 = "200x0";
	public static final String[] DEFAULT_THUMBNAIL_RESOLUTIONS = new String[] {
			THUMBNAIL_RESOLUTION_50_0, THUMBNAIL_RESOLUTION_100_0, THUMBNAIL_RESOLUTION_200_0 };

	public static final String PARAM_THUMBNAIL_FOLDER = "thumbnailFolder";
	public static final String DEFAULT_THUMBNAIL_FOLDER = "_thumbnails_";

	@Property(name = PARAM_THUMBNAIL_RESOLUTIONS, label = "%resolutions.name", description = "%resolutions.description", value = {
			THUMBNAIL_RESOLUTION_50_0, THUMBNAIL_RESOLUTION_100_0, THUMBNAIL_RESOLUTION_200_0 })
	private String[] thumbnailResolutions = DEFAULT_THUMBNAIL_RESOLUTIONS;

	@Property(name = PARAM_THUMBNAIL_FOLDER, label = "%folder", description = "%folder.description", value = DEFAULT_THUMBNAIL_FOLDER)
	private String thumbnailFolder = DEFAULT_THUMBNAIL_FOLDER;

	@Reference
	SlingRepository repository;

	@Reference
	ResourceResolverFactory resourceResolverFactory;

	/**
	 * Activates this component.
	 * 
	 * @param componentContext
	 *            The OSGi <code>ComponentContext</code> of this component.
	 */
	protected void activate(ComponentContext componentContext)
			throws RepositoryException {
		// Setting up thumbnailResolutions
		thumbnailResolutions = OsgiUtil.toStringArray(componentContext
				.getProperties().get(PARAM_THUMBNAIL_RESOLUTIONS),
				DEFAULT_THUMBNAIL_RESOLUTIONS);
		// Setting up thumbnail folder
		thumbnailFolder = (String) componentContext.getProperties().get(
				PARAM_THUMBNAIL_FOLDER);
	}

	public void handleEvent(Event event) {
		if (EventUtil.isLocal(event)) {
			JobUtil.processJob(event, this);
		}
	}

	public boolean process(Event event) {
		Session session = null;
		ResourceResolver resourceResolver = null;

		try {
			String resourcePath = (String) event.getProperty("resourcePath");
			session = getAdministrativeSession(repository);

			Map<String, Object> authInfo = new HashMap<String, Object>();
			authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
					session);
			try {
				resourceResolver = resourceResolverFactory
						.getResourceResolver(authInfo);
			} catch (LoginException e) {
				log.error("Authentication error");
				return false;
			}

			Resource res = resourceResolver.getResource(resourcePath);
			if (ResourceUtil.isA(res, "nt:file")) {
				if (event
						.getTopic()
						.equals(ThumbnailGeneratorResourceChangeListener.THUMBNAIL_REMOVE_TOPIC)) {
					// remove
					deleteThumbnailsForImage(resourcePath);
				} else if (event
						.getTopic()
						.equals(ThumbnailGeneratorResourceChangeListener.THUMBNAIL_GENERATE_TOPIC)) {
					// insert
					createThumbnailsForImage(resourcePath);
				}
			}
			return true;
		} catch (RepositoryException e) {
			log.error("RepositoryException: " + e);
			return false;
		} catch (Exception e) {
			log.error("Exception: " + e);
			return false;
		} finally {
			try {
			    	if (resourceResolver != null) resourceResolver.close();
				releaseAdministrativeSession(session);
			} catch (RepositoryException e) {
				log.error("Error on logout administrative session");
			}
		}
	}

	public boolean createThumbnailsForImage(String path)
			throws RepositoryException, Exception {
		Session session = null;
		try {
			log.info("Generating thumbnail for image "+path);
			session = getAdministrativeSession(repository);
			String resourceName = path.substring(path.lastIndexOf("/") + 1);

			Map<String, Object> authInfo = new HashMap<String, Object>();
			authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
					session);
			ResourceResolver resourceResolver = null;
			try {
				resourceResolver = resourceResolverFactory
						.getResourceResolver(authInfo);
			} catch (LoginException e) {
				log.error("Authentication error");
				throw new RepositoryException();
			}

			Resource res = resourceResolver.getResource(path);
			Node node = null;
			if (res != null) node = res.adaptTo(Node.class);
			if (node == null) return false;

			// if thumbnail folder does not exists we generate it
			if (!node.getParent().hasNode(thumbnailFolder)) {
				node.getParent().addNode(thumbnailFolder, "thumbnail:thumbnailFolder");
			}
			// session.save();
			Node thumbnailFolderNode = node.getParent()
					.getNode(thumbnailFolder);

			// Removing thumbnail images
			NodeIterator iter = thumbnailFolderNode
					.getNodes(resourceName + "*");
			while (iter.hasNext()) {
				Node rm = iter.nextNode();
				if (rm.isNodeType("thumbnail:thumbnailImage") && 
						rm.hasProperty("originalNodeLastModified") &&
						rm.getProperty("originalNodeLastModified").getDate().equals(node
							.getNode("jcr:content").getProperty("jcr:lastModified").getDate())) {		
				} else {
					log.info(" -> Removing old thumbnail: " + rm.getName());
					rm.remove();
				}
			}

			// Generating thumbnail images
			for (int i = 0; i < thumbnailResolutions.length; i++) {
				String[] reso = thumbnailResolutions[i].split("x");

				int width, height;
				width = Integer.parseInt(reso[0]);
				height = Integer.parseInt(reso[1]);

				String thumbnailName = res.getName() + "." + width + "."
				+ height + ".jpg";
				
				if (!thumbnailFolderNode.hasNode(thumbnailName)) {
					
					final BufferedImage src = ImageIO.read(node
							.getNode("jcr:content").getProperty("jcr:data")
							.getBinary().getStream());
					if (src == null) {
						final StringBuffer sb = new StringBuffer();
						for (String fmt : ImageIO.getReaderFormatNames()) {
							sb.append(fmt);
							sb.append(' ');
						}
						throw new IOException(
								"Unable to read image, registered formats: " + sb);
					}
	
					final double scale = (double) width / src.getWidth();
	
					int destWidth = width;
					int destHeight = height > 0 ? height : new Double(scale
							* src.getHeight()).intValue();
	
					log.info(" ---> Generating thumbnail, w={}, h={}", destWidth,
							destHeight);
	
					final BufferedImage dest = new BufferedImage(destWidth,
							destHeight, BufferedImage.TYPE_INT_RGB);
	
					ScaleFilter filter = new ScaleFilter(destWidth, destHeight);
					filter.filter(src, dest);
					final File tmp = File.createTempFile(getClass().getSimpleName(), resourceName+"."+Calendar.getInstance().getTimeInMillis());
					try {
						FileOutputStream outs = new FileOutputStream(tmp);
						ImageIO.write(dest, "jpg", outs);
						outs.flush();
						outs.close();

						// Create thumbnail node and set the mandatory properties
						Node thumbnail = thumbnailFolderNode.addNode(thumbnailName,
								"thumbnail:thumbnailImage");
						thumbnail.addNode("jcr:content", "nt:resource").setProperty(
								"jcr:data", new Binary() {
									InputStream is;
		
									public InputStream getStream()
											throws RepositoryException {
										try {
											is = new FileInputStream(tmp);
										} catch (FileNotFoundException e) {
											log.error("IOError: ",e);
										}
										return is;
									}
		
									public int read(byte[] b, long position)
											throws IOException, RepositoryException {
										return is.read(b, (int) position, 4096);
									}
		
									public long getSize() throws RepositoryException {
										try {
											return is.available();
										} catch (IOException e) {
											throw new RepositoryException(e);
										}
									}
		
									public void dispose() {
										try {
											is.close();
										} catch (Exception e) {
											log.error("Dispose error!");
										}
									}
								});
						thumbnail.getNode("jcr:content").setProperty("jcr:lastModified",
								Calendar.getInstance());
						thumbnail.getNode("jcr:content").setProperty("jcr:mimeType", "image/jpg");
						thumbnail.setProperty("originalNodePath", path);
						thumbnail.setProperty("originalNodeLastModified", node
								.getNode("jcr:content").getProperty("jcr:lastModified").getDate());					
						thumbnail.setProperty("width", destWidth);
						thumbnail.setProperty("height", destHeight);
						log.info(" -> generated name: " + thumbnail.getPath()
								+ " Width: {} Height: {} ",
								Integer.toString(destWidth),
								Integer.toString(destHeight));
						session.save();
					} catch (Exception e) {
						return false;
					} finally {
						if (tmp != null) {
							tmp.delete();
						}
					}
				}
			}
			return true;

		} finally {
			releaseAdministrativeSession(session);
		}
	}

	public void deleteThumbnailsForImage(String path)
			throws RepositoryException, Exception {
		Session session = null;
		try {
			session = getAdministrativeSession(repository);
			String resourceName = path.substring(path.lastIndexOf("/") + 1);
			
			
			String parentFolder = path.substring(0, path.lastIndexOf("/"));

			Map<String, Object> authInfo = new HashMap<String, Object>();
			authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
					session);
			ResourceResolver resourceResolver = null;
			try {
				resourceResolver = resourceResolverFactory
						.getResourceResolver(authInfo);
			} catch (LoginException e) {
				log.error("Authentication error");
				throw new RepositoryException();
			}
			Resource res = null;
			Node node = null;
			try {
				res = resourceResolver.getResource(parentFolder);
			} finally {
			}
			
			if (res != null) node = res.adaptTo(Node.class);

			// if thumbnail folder not exists we deleting nodes from it
			if (node != null && node.hasNode(thumbnailFolder)) {
				Node thumbnailFolderNode = node.getParent().getNode(
						thumbnailFolder);

				// Removing thumbnail images
				NodeIterator iter = thumbnailFolderNode.getNodes(resourceName
						+ "*");
				while (iter.hasNext()) {
					Node rm = iter.nextNode();
					log.info(" -> Removing thumbnail: " + rm.getName());
					rm.remove();
				}
			}
			session.save();
		} finally {
			releaseAdministrativeSession(session);
		}
	}

}
