package org.liveSense.service.thumbnailGenerator;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.core.AdministrativeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(label="%thumbnailGeneratorJobEventHandler.name",
        description="%thumbnailGeneratorJobEventHandler.description",
        immediate=true,
        metatype=true,
        policy=ConfigurationPolicy.OPTIONAL)
@Service(value=java.lang.Runnable.class)
@Properties(value = {
		@Property(
				name="scheduler.name", 
				value="ThumbnailGeneratorPeriodicalGeneration"),
		@Property(
				name="scheduler.expression", 
				value="0 0 * ? * * "),
		@Property(		
				name = "event.topics", value = {
				ThumbnailGeneratorResourceChangeListener.THUMBNAIL_GENERATE_TOPIC,
				ThumbnailGeneratorResourceChangeListener.THUMBNAIL_REMOVE_TOPIC })
		}
)


public class ThumbnailGeneratorPeriodicalGeneration extends AdministrativeService implements Runnable {
	private static final Logger log = LoggerFactory.getLogger(ThumbnailGeneratorPeriodicalGeneration.class);

	
	@Reference
	SlingRepository repository;
		
	public void run() {
		// Scanning folders for images
		Session session = null;
		log.info("Starting scan for images...");
		try {
			session = getAdministrativeSession(repository);
			NodeIterator nodes = session.getWorkspace().getQueryManager().createQuery("select * from [jcr:content]", Query.JCR_SQL2).execute().getNodes();
			while (nodes.hasNext()) {
				Node node = nodes.nextNode();
				if (node.getParent().getPrimaryNodeType().getName().equals("thumbnail:thumbnailImage")) {
					
				}
			}
			
			
		} catch (RepositoryException e) {
			log.error(e.getMessage());
		} finally {
			try {
				releaseAdministrativeSession(session);
			} catch (RepositoryException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		
		
	}

}
