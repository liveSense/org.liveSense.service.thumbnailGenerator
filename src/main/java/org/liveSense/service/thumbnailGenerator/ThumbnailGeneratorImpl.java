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
/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 13, 2010
 */
import com.jhlabs.image.ScaleFilter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.ObservationManager;
import org.apache.sling.commons.osgi.OsgiUtil;

import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.utils.AdministrativeService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observe the users content for changes, and generate
 * thumbnails when images are added.
 *
 * @scr.component immediate="true" metatype="true"
 *
 */

public class ThumbnailGeneratorImpl extends AdministrativeService implements ThumbnailGenerator {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailGeneratorImpl.class);
//    private Map<String, String> supportedMimeTypes = new HashMap<String, String>();

    private ObservationManager observationManager;
    /** @scr.reference */
    private SlingRepository repository;
    /**
     * @scr.property    label="%contentPathes.name"
     *                  description="%contentPathes.description"
     *                  valueRef="DEFAULT_CONTENT_PATHES"
     */
    public static final String PARAM_CONTENT_PATHES = "contentPathes";
    public static final String[] DEFAULT_CONTENT_PATHES = {"/sites", "/users"};
    private String[] contentPathes = DEFAULT_CONTENT_PATHES;
    /**
     * @scr.property    label="%thumbnailResolutions.name"
     *                  description="%thumbnailResolutions.description"
     *                  valueRef="DEFAULT_THUMBNAIL_RESOLUTIONS"
     */
    public static final String PARAM_THUMBNAIL_RESOLUTIONS = "thumbnailResolutions";
    public static final String[] DEFAULT_THUMBNAIL_RESOLUTIONS = {"50,0", "100,0", "200,0"};
    private String[] thumbnailResolutions = DEFAULT_THUMBNAIL_RESOLUTIONS;

    /**
     * @scr.property    label="%thumbnailFolder.name"
     *                  description="%thumbnailFolder.description"
     *                  valueRef="DEFAULT_THUMBNAIL_FOLDER"
     */
    public static final String PARAM_THUMBNAIL_FOLDER = "thumbnailFolder";
    public static final String DEFAULT_THUMBNAIL_FOLDER = "_thumbnails_";
    private String thumbnailFolder = DEFAULT_THUMBNAIL_FOLDER;

    /**
     * @scr.property    label="%supportedMimeTypes.name"
     *                  description="%supportedMimeTypes.description"
     *                  valueRef="DEFAULT_SUPPORTED_MIME_TYPES"
     */
    public static final String PARAM_SUPPORTED_MIME_TYPES = "supportedMimeTypes";
    public static final String[] DEFAULT_SUPPORTED_MIME_TYPES = {"image/jpeg","image/gif","image/png"};
    private String[] supportedMimeTypes = DEFAULT_SUPPORTED_MIME_TYPES;

    Session session;

    private ArrayList<ThumbnailGeneratorEventListener> eventListeners = new ArrayList<ThumbnailGeneratorEventListener>();

    /**
     * Activates this component.
     *
     * @param componentContext The OSGi <code>ComponentContext</code> of this
     *            component.
     */
    protected void activate(ComponentContext componentContext) throws RepositoryException {
        Dictionary<?, ?> props = componentContext.getProperties();
        
        // Setting up contentPathes
        String[] contentPathesNew = OsgiUtil.toStringArray(componentContext.getProperties().get(PARAM_CONTENT_PATHES), DEFAULT_CONTENT_PATHES);
        boolean contentPathesChanged = false;
        if (contentPathesNew.length != this.contentPathes.length) {
            contentPathesChanged = true;
        } else {
            for (int i = 0; i < contentPathesNew.length; i++) {
                if (!contentPathesNew[i].equals(this.contentPathes[i])) {
                    contentPathesChanged = true;
                }
            }
            if (contentPathesChanged) {
                StringBuffer contentPathesValueList = new StringBuffer();
                StringBuffer contentPathesNewValueList = new StringBuffer();

                for (int i = 0; i < contentPathesNew.length; i++) {
                    if (i != 0) {
                        contentPathesNewValueList.append(", ");
                    }
                    contentPathesNewValueList.append(contentPathesNew[i].toString());
                }

                for (int i = 0; i < contentPathes.length; i++) {
                    if (i != 0) {
                        contentPathesValueList.append(", ");
                    }
                    contentPathesValueList.append(contentPathes[i].toString());
                }
                log.info("Setting new contentPathes: {}) (was: {})", contentPathesNewValueList.toString(), contentPathesValueList.toString());
                this.contentPathes = contentPathesNew;
            }
        }

        // Setting up thumbnailResolutions
        String[] thumbnailResolutionsNew = OsgiUtil.toStringArray(componentContext.getProperties().get(PARAM_THUMBNAIL_RESOLUTIONS), DEFAULT_THUMBNAIL_RESOLUTIONS);
        boolean thumbnailResolutionsChanged = false;
        if (thumbnailResolutionsNew.length != this.thumbnailResolutions.length) {
            thumbnailResolutionsChanged = true;
        } else {
            for (int i = 0; i < thumbnailResolutionsNew.length; i++) {
                if (!thumbnailResolutionsNew[i].equals(this.thumbnailResolutions[i])) {
                    thumbnailResolutionsChanged = true;
                }
            }
            if (thumbnailResolutionsChanged) {
                StringBuffer thumbnailResolutionsValueList = new StringBuffer();
                StringBuffer thumbnailResolutionsNewValueList = new StringBuffer();

                for (int i = 0; i < thumbnailResolutionsNew.length; i++) {
                    if (i != 0) {
                        thumbnailResolutionsNewValueList.append(", ");
                    }
                    thumbnailResolutionsNewValueList.append(thumbnailResolutionsNew[i].toString());
                }

                for (int i = 0; i < thumbnailResolutions.length; i++) {
                    if (i != 0) {
                        thumbnailResolutionsValueList.append(", ");
                    }
                    thumbnailResolutionsValueList.append(thumbnailResolutions[i].toString());
                }
                log.info("Setting new thumbnailResolutions: {}) (was: {})", thumbnailResolutionsNewValueList.toString(), thumbnailResolutionsValueList.toString());
                this.thumbnailResolutions = thumbnailResolutionsNew;
            }
        }

        // Setting up supportedMimeTypes
        String[] supportedMimeTypesNew = OsgiUtil.toStringArray(componentContext.getProperties().get(PARAM_SUPPORTED_MIME_TYPES), DEFAULT_SUPPORTED_MIME_TYPES);
        boolean supportedMimeTypesChanged = false;
        if (supportedMimeTypesNew.length != this.supportedMimeTypes.length) {
            supportedMimeTypesChanged = true;
        } else {
            for (int i = 0; i < supportedMimeTypesNew.length; i++) {
                if (!supportedMimeTypesNew[i].equals(this.supportedMimeTypes[i])) {
                    supportedMimeTypesChanged = true;
                }
            }
            if (supportedMimeTypesChanged) {
                StringBuffer supportedMimeTypesValueList = new StringBuffer();
                StringBuffer supportedMimeTypesNewValueList = new StringBuffer();

                for (int i = 0; i < supportedMimeTypesNew.length; i++) {
                    if (i != 0) {
                        supportedMimeTypesNewValueList.append(", ");
                    }
                    supportedMimeTypesNewValueList.append(supportedMimeTypesNew[i].toString());
                }

                for (int i = 0; i < supportedMimeTypes.length; i++) {
                    if (i != 0) {
                        supportedMimeTypesValueList.append(", ");
                    }
                    supportedMimeTypesValueList.append(supportedMimeTypes[i].toString());
                }
                log.info("Setting new supportedMimeTypes: {}) (was: {})", supportedMimeTypesNewValueList.toString(), supportedMimeTypesValueList.toString());
                this.supportedMimeTypes = supportedMimeTypesNew;
            }
        }



        String thumbnailFolderNew = (String) componentContext.getProperties().get(PARAM_THUMBNAIL_FOLDER);
        if (thumbnailFolderNew == null || thumbnailFolderNew.length() == 0) {
            thumbnailFolderNew = DEFAULT_THUMBNAIL_FOLDER;
        }
        if (!thumbnailFolderNew.equals(this.thumbnailFolder)) {
            log.info("Setting new thumbnailFolder {} (was {})", thumbnailFolderNew, this.thumbnailFolder);
            this.thumbnailFolder = thumbnailFolderNew;
        }

        session = getAdministrativeSession(repository);
        if (repository.getDescriptor(Repository.OPTION_OBSERVATION_SUPPORTED).equals("true")) {
            observationManager = session.getWorkspace().getObservationManager();
            //String[] types = {"nt:resource"};
            for (int i = 0; i < contentPathes.length; i++) {
                String[] propType = {"nt:resource"};
                ThumbnailGeneratorEventListener listener = new ThumbnailGeneratorEventListener(this);
                observationManager.addEventListener(listener, Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED, contentPathes[i], true, null, propType, true);
                eventListeners.add(listener);

                String[] fileType = {"nt:file"};
                listener = new ThumbnailGeneratorEventListener(this);
                observationManager.addEventListener(listener, Event.NODE_REMOVED, contentPathes[i], true, null, fileType, true);
                eventListeners.add(listener);
            }

        }
    }
    

    @Override
    public void deactivate(ComponentContext componentContext) throws RepositoryException {
        super.deactivate(componentContext);
        if (observationManager != null) {
            for (ThumbnailGeneratorEventListener listener : eventListeners) {
                observationManager.removeEventListener(listener);
            }
        }
    }


    boolean isValidMimeType(Node node) throws RepositoryException {
        if (node.getNode("jcr:content").hasProperty("jcr:mimeType")) {
            final String mimeType = node.getNode("jcr:content").getProperty("jcr:mimeType").getString();

            for (int i = 0; i < supportedMimeTypes.length; i++) {
                String actMimeType = supportedMimeTypes[i];
                if (mimeType.equals(actMimeType)) {
                    return true;
                }

            }
        } else {
            return false;
        }
        return false;
    }

    public void createThumbnailsForImage(String parentFolder, String fileName) throws RepositoryException, Exception {
        if (!session.isLive()) session = getAdministrativeSession(repository);
        Node node = session.getRootNode().getNode(parentFolder+"/"+fileName);
        
		// File node
		if (node.hasNode("jcr:content")) {
			if (isValidMimeType(node)) {
				// Scale to a temp file for simplicity
				log.info("Creating thumbnails for node {}", node.getPath());

				for (int i = 0; i < thumbnailResolutions.length; i++) {
					String[] res = thumbnailResolutions[i].split(",");
					createThumbnail(node, Integer.parseInt(res[0]), Integer.parseInt(res[1]), "image/jpeg", ".jpg");
				}

			} else {
				log.debug("No thumbnail created, not an image file: {} - {}", node.getPath(), node.getNode("jcr:content").getProperty("jcr:mimeType"));
			}
			session.save();
		} else {
			log.debug("Not a file node: ", node.getPath());
		}
    }

    public void deleteThumbnailsForImage(String parentFolder, String fileName) throws RepositoryException, Exception {
        if (!session.isLive()) session = getAdministrativeSession(repository);
       
		if (session.getRootNode().hasNode(parentFolder)) {
			Node node = session.getRootNode().getNode(parentFolder);

			Node thumbnailFolder = getThumbnailFolder(node,false);
			if (thumbnailFolder == null) return;
			NodeIterator iter = thumbnailFolder.getNodes(fileName+"*");
			while (iter.hasNext()) {
				Node rm = iter.nextNode();
				log.info(" -> Remove thumbnail: "+rm.getName());
				rm.remove();
			}
	        session.save();
		}
    }

    private void createThumbnail(Node image, int scaleWidth, int scaleHeight, String mimeType, String suffix) throws Exception {
        final File tmp = File.createTempFile(getClass().getSimpleName(), suffix);
        try {
            scale(image.getNode("jcr:content").getProperty("jcr:data").getStream(), scaleWidth, scaleHeight, new FileOutputStream(tmp), suffix);

            // Create thumbnail node and set the mandatory properties
            String thumbnailName = image.getName() + "_" + scaleWidth + "_" + scaleHeight + suffix;
            Node thumbnailFolder = getThumbnailFolder(image.getParent(), true);

            if (thumbnailFolder.hasNode(thumbnailName)) {
                thumbnailFolder.getNode(thumbnailName).remove();
            }
            Node thumbnail = thumbnailFolder.addNode(thumbnailName, "nt:file");
            Node contentNode = thumbnail.addNode("jcr:content", "nt:resource");
            contentNode.setProperty("jcr:data", new FileInputStream(tmp));
            contentNode.setProperty("jcr:lastModified", Calendar.getInstance());
            contentNode.setProperty("jcr:mimeType", mimeType);
            log.info(" -> generated name: "+ contentNode.getPath()+" Width: {} Height: {} ", Integer.toString(scaleWidth), Integer.toString(scaleHeight));
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }


    private Node getThumbnailFolder(Node node, boolean create) throws Exception {
        if (node.hasNode(thumbnailFolder)) {
            log.debug(thumbnailFolder + " node exists already at " + node.getPath());
            return node.getNode(thumbnailFolder);
        } else {
            if (create) {
                log.info(thumbnailFolder + " node does not exists at " + node.getPath()+" , creating...");
                Node t = node.addNode(thumbnailFolder, "nt:folder");
                return t;
            }
            return null;
        }
    }

    private void scale(InputStream inputStream, int width, int height, OutputStream outputStream, String suffix) throws IOException {
        if (inputStream == null) {
            throw new IOException("InputStream is null");
        }


        final BufferedImage src = ImageIO.read(inputStream);
        if (src == null) {
            final StringBuffer sb = new StringBuffer();
            for (String fmt : ImageIO.getReaderFormatNames()) {
                sb.append(fmt);
                sb.append(' ');
            }
            throw new IOException("Unable to read image, registered formats: " + sb);
        }

        final double scale = (double) width / src.getWidth();

        int destWidth = width;
        int destHeight = height > 0 ? height : new Double(scale * src.getHeight()).intValue();

        log.info(" ---> Generating thumbnail, w={}, h={}", destWidth, destHeight);

        BufferedImage dest = new BufferedImage(destWidth, destHeight, BufferedImage.TYPE_INT_RGB);

        ScaleFilter filter = new ScaleFilter(destWidth, destHeight);
        filter.filter(src, dest);

        ImageIO.write(dest, suffix.substring(1), outputStream);
    }

    public String getThumbnailFolder() {
        return thumbnailFolder;
    }
}

