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
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
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

public class ThumbnailGeneratorJobEventHandler
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

	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
	SlingRepository repository;

	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
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

	@Override
	public void handleEvent(Event event) {
		if (EventUtil.isLocal(event)) {
			JobUtil.processJob(event, this);
		}
	}

	@Override
	public boolean process(Event event) {
		Session session = null;
		ResourceResolver resourceResolver = null;

		try {
			String resourcePath = (String) event.getProperty("resourcePath");
			session = repository.loginAdministrative(null);

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

				if (event
						.getTopic()
						.equals(ThumbnailGeneratorResourceChangeListener.THUMBNAIL_REMOVE_TOPIC)) {
					// remove
					deleteThumbnailsForImage(session, resourcePath);
				} else if (event
						.getTopic()
						.equals(ThumbnailGeneratorResourceChangeListener.THUMBNAIL_GENERATE_TOPIC)) {
					// insert
					Resource res = resourceResolver.getResource(resourcePath);

					if (ResourceUtil.isA(res, "nt:file")) {
						createThumbnailsForImage(res);
					}

				}
			if (session != null && session.isLive() && session.hasPendingChanges())
				session.save();
			return true;
		} catch (RepositoryException e) {
			log.error("RepositoryException: " + e);
			return false;
		} catch (Exception e) {
			log.error("Exception: " + e);
			return false;
		} finally {
		    if (resourceResolver != null) resourceResolver.close();
			if (session != null)
				session.logout();
		}
	}

	public boolean createThumbnailsForImage(Resource resource)
			throws RepositoryException, Exception {
		try {
			log.info("Generating thumbnail for image "+resource.getPath());
			Node node = null;
			if (resource != null) node = resource.adaptTo(Node.class);
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
					.getNodes(resource.getName() + "*");
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

				String thumbnailName = resource.getName() + "." + width + "."
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
					final File tmp = File.createTempFile(getClass().getSimpleName(), resource.getName()+"."+Calendar.getInstance().getTimeInMillis());
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
		
									@Override
									public InputStream getStream()
											throws RepositoryException {
										try {
											is = new FileInputStream(tmp);
										} catch (FileNotFoundException e) {
											log.error("IOError: ",e);
										}
										return is;
									}
		
									@Override
									public int read(byte[] b, long position)
											throws IOException, RepositoryException {
										return is.read(b, (int) position, 4096);
									}
		
									@Override
									public long getSize() throws RepositoryException {
										try {
											return is.available();
										} catch (IOException e) {
											throw new RepositoryException(e);
										}
									}
		
									@Override
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
						thumbnail.setProperty("originalNodePath", resource.getPath());
						thumbnail.setProperty("originalNodeLastModified", node
								.getNode("jcr:content").getProperty("jcr:lastModified").getDate());					
						thumbnail.setProperty("width", destWidth);
						thumbnail.setProperty("height", destHeight);
						log.info(" -> generated name: " + thumbnail.getPath()
								+ " Width: {} Height: {} ",
								Integer.toString(destWidth),
								Integer.toString(destHeight));
						//session.save();
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
		}
	}

	public void deleteThumbnailsForImage(Session session, String resourcePath)
			throws RepositoryException, Exception {
		try {
			
			// if thumbnail folder not exists we deleting nodes from it
			if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);
			if (resourcePath.lastIndexOf("/")<=0) return;
			String parentPath = resourcePath.substring(0, resourcePath.lastIndexOf("/"));
			String imageName = resourcePath.substring(resourcePath.lastIndexOf("/")+1, resourcePath.length()-1);

			log.info("Delete thumbnail for image "+imageName+" at "+parentPath);
			
			Node node = null;
			if (session.getRootNode().hasNode(parentPath)) {
				node = session.getRootNode().getNode(parentPath);
			}
			
			if (node != null && node.hasNode(thumbnailFolder)) {
				Node thumbnailFolderNode = node.getNode(
						thumbnailFolder);

				// Removing thumbnail images
				NodeIterator iter = thumbnailFolderNode.getNodes(imageName
						+ "*");
				while (iter.hasNext()) {
					Node rm = iter.nextNode();
					log.info(" -> Removing thumbnail: " + rm.getName());
					rm.remove();
				}
			}
		} catch (Exception e) {
			log.error("deleteThumbnailsForImage",e);
		} finally {
		}
	}

}
