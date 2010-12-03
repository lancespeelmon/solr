package org.sakaiproject.nakamura.solr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.solr.Indexer;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
import org.sakaiproject.nakamura.solr.handlers.DefaultResourceTypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

@Component(immediate = true, metatype = true)
@Service(value = ResourceIndexingService.class)
public class ResourceIndexingServiceImpl implements IndexingHandler,
    ResourceIndexingService {

  private static final Object PROP_TOPICS = null;
  private static final String REMOVE_TOPIC = "REMOVED";
  private static final String CREATED_TOPIC = "CREATED";
  private static final String UPDATE_TOPIC = "UPDATED";
  private static final String[] DEFAULT_TOPICS = {
      "org/apache/sling/api/resource/Resource/CREATED",
      "org/apache/sling/api/resource/Resource/REMOVED" };
  private static final Logger LOGGER = LoggerFactory
      .getLogger(ResourceIndexingServiceImpl.class);
  @Reference
  protected Indexer contentIndexer;
  private String[] topics;

  private Map<String, IndexingHandler> indexers = Maps.newConcurrentHashMap();
  private IndexingHandler defaultHandler;

  @Activate
  public void activate(Map<String, Object> properties) {
    defaultHandler = new DefaultResourceTypeHandler();
    topics = Utils.getSetting(properties.get(PROP_TOPICS), DEFAULT_TOPICS);
    for (String topic : topics) {
      contentIndexer.addHandler(topic, this);
    }
  }

  @Deactivate
  public void deactivate(Map<String, Object> properties) {
    for (String topic : topics) {
      contentIndexer.removeHander(topic, this);
    }
  }

  public Collection<SolrInputDocument> getDocuments(Session session, Event event) {
    String topic = event.getTopic();
    if (topic.endsWith(UPDATE_TOPIC) || topic.endsWith(CREATED_TOPIC)) {
      String path = (String) event.getProperty("path");
      LOGGER.info("Update action at path:{}  require on {} ",path, event);
      if (path != null) {
        return getHander(session, path).getDocuments(session, event);
      }
    } else {
      LOGGER.info("No update action require on {} ",event);
    }
    return ImmutableList.of();
  }

  private IndexingHandler getHander(Session session, String path) {
    try {
      while (path != null && !"/".equals(path)) {
        Node n = session.getNode(path);
        LOGGER.info("Checking for Node at {} found {} ",path,n);
        if (n != null) {
          String resourceType = n.getPrimaryNodeType().getName();
          if (n.hasProperty("sling:resourceType")) {
            resourceType = n.getProperty("sling:resourceType").getString();
          }
          IndexingHandler handler = indexers.get(resourceType);
          LOGGER.info("Handler of type {} found {} from {} ",new Object[]{resourceType, handler, indexers});
          if (handler != null) {
            return handler;
          }
        }
        path = Utils.getParentPath(path);
      }
    } catch (RepositoryException e) {
      LOGGER.info(e.getMessage(), e);
    }
    return defaultHandler;
  }

  public Collection<String> getDeleteQueries(Session session, Event event) {
    String topic = event.getTopic();
    if (topic.endsWith(REMOVE_TOPIC) || topic.endsWith(UPDATE_TOPIC)) {
      String path = (String) event.getProperty("path");
      if (path != null) {
          return getHander(session, path).getDeleteQueries(session, event);
       }
    } else {
      LOGGER.info("No delete action require on {} ",event);
    }
    return ImmutableList.of();
  }

  public void addHandler(String key, IndexingHandler handler) {
    LOGGER.info("Added New Indexer as {} at {} ",key, handler);
    indexers.put(key, handler);
  }

  public void removeHander(String key, IndexingHandler handler) {
    if (handler.equals(indexers.get(key))) {
      indexers.remove(key);
    }
  }


}