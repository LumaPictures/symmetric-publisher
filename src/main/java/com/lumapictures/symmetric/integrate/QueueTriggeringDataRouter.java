package com.lumapictures.symmetric.integrate;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.ext.INodeGroupExtensionPoint;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.integrate.IPublisher;
import org.jumpmind.symmetric.model.*;
import org.jumpmind.symmetric.route.IDataRouter;
import org.jumpmind.symmetric.route.SimpleRouterContext;
import org.jumpmind.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class QueueTriggeringDataRouter implements
        IDataRouter, IExtensionPoint, INodeGroupExtensionPoint, ISymmetricEngineAware {

    protected final String CHANGE_CACHE = "XML_CHANGE_CACHE_" + this.hashCode();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected ISymmetricEngine engine;
    protected IPublisher publisher;
    protected Format xmlFormat;

    private String[] nodeGroups;
    private boolean onePerBatch = false;

    public QueueTriggeringDataRouter() {
        xmlFormat = Format.getCompactFormat();
        xmlFormat.setOmitDeclaration(true);
    }

    public void setPublisher(IPublisher publisher) {
        this.publisher = publisher;
    }

    public void setNodeGroups(String[] nodeGroups) {
        this.nodeGroups = nodeGroups;
    }

    public void setNodeGroup(String nodeGroup) {
        this.nodeGroups = new String[] { nodeGroup };
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public String[] getNodeGroupIdsToApplyTo() {
        return nodeGroups;
    }

    @Override
    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }

    /**
     * Indicates that one message should be published per batch. If this is set
     * to false, then only one message will be published once for each set of
     * data that is routed (even though it may have been routed to several nodes
     * across several different batches).
     *
     * @param onePerBatch
     */
    public void setOnePerBatch(boolean onePerBatch) {
        this.onePerBatch = onePerBatch;
    }

    /**
     * Determines what nodes to route to.
     *
     * Since all we're doing is using this router for queueing, no routing decisions
     * are actually being made, and thus an empty set of target nodes is returned.
     */
    @Override
    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData,
                                    Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed,
                                    TriggerRouter triggerRouter) {

        Element change = createChange(dataMetaData);
        if (change != null) {
            // Cache change, where it will be later published in a batch
            List<Element> changes = getChanges(context);
            changes.add(change);
        }

        return Collections.emptySet();
    }

    /**
     * Completes the current OutgoingBatch.
     * @see org.jumpmind.symmetric.service.impl.RouterService#completeBatchesAndCommit(org.jumpmind.symmetric.route.ChannelRouterContext)
     *
     * @param context the router context.
     * @param batch the batch to complete.
     */
    @Override
    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch) {
        log.info("completeBatch() called");
        if (onePerBatch && hasChanges(context))
            publishChanges(context);
    }

    /**
     * Completes the current context.
     * Called after all batches are completed.
     * @see org.jumpmind.symmetric.service.impl.RouterService#completeBatchesAndCommit(org.jumpmind.symmetric.route.ChannelRouterContext)
     *
     * @param context the router context.
     */
    @Override
    public void contextCommitted(SimpleRouterContext context) {
        log.info("contextCommitted() called");
        if (hasChanges(context))
            publishChanges(context);
    }

    /**
     * Creates an XML Element representing a change in dataMetaData.
     *
     * @param dataMetaData the change information.
     * @return Element for change, or null if no change could be created.
     */
    protected Element createChange(DataMetaData dataMetaData) {
        TriggerHistory history = dataMetaData.getTriggerHistory();
        Data data = dataMetaData.getData();

        String databaseName = history.getSourceCatalogName();
        String schemaName = history.getSourceSchemaName();
        String tableName = data.getTableName();

        Element change = new Element("change");
        change.setAttribute("type", data.getDataEventType().getCode()); // I, U, D

        // Database + Table
        if (databaseName != null)
            change.addContent(new Element("database").setText(databaseName));
        if (schemaName != null)
            change.addContent(new Element("schema").setText(schemaName));
        change.addContent(new Element("table").setText(tableName));

        // Primary Key
        Element key = new Element("key");
        change.addContent(key);
        String[] primaryKeyCols = history.getParsedPkColumnNames();
        String[] primaryKeyVal = data.toParsedPkData();
        if (primaryKeyCols == null || primaryKeyVal == null)
            return null;

        assert primaryKeyCols.length == primaryKeyVal.length;
        for (int i = 0; i < primaryKeyCols.length; ++i) {
            String columnName = primaryKeyCols[i];
            Element column = new Element("column");
            key.addContent(column);
            column.setAttribute("name", columnName);
            column.setText( String.valueOf(primaryKeyVal[i]) );
        }

        return change;
    }

    /**
     * Gets list of all cached changes, pending publication.
     *
     * @param context the router context.
     * @return list of change elements.
     */
    @SuppressWarnings("unchecked")
    protected synchronized List<Element> getChanges(Context context) {
        List<Element> cache = (List<Element>)context.get(CHANGE_CACHE);
        if (cache == null) {
            cache = new LinkedList<Element>();
            context.put(CHANGE_CACHE, cache);
        }
        return cache;
    }

    /**
     * Are there any cached changes to publish?
     *
     * @param context the router context.
     * @return whether or not cached changes exist.
     */
    protected synchronized boolean hasChanges(SimpleRouterContext context) {
        return getChanges(context).size() > 0;
    }

    /**
     * Publishes all cached changes.
     *
     * @param context the router context.
     */
    protected synchronized void publishChanges(Context context) {
        Element root = new Element("changes");

        List<Element> changes = getChanges(context);
        int numChanges = changes.size();
        if (numChanges == 0)
            return;

        root.setAttribute("count", String.valueOf(numChanges));
        root.addContent(changes);

        String xml = new XMLOutputter(xmlFormat).outputString(new Document(root));
        log.debug("Publishing change batch: {}", xml);
        publisher.publish(context, xml);

        changes.clear();
    }

}
