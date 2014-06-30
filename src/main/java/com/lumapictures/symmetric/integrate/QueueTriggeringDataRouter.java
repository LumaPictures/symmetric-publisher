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
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.IDataRouter;
import org.jumpmind.symmetric.route.SimpleRouterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

public class QueueTriggeringDataRouter implements
        IDataRouter, IExtensionPoint, INodeGroupExtensionPoint, ISymmetricEngineAware {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected ISymmetricEngine engine;
    protected IPublisher publisher;
    private String[] nodeGroups;
    private boolean onePerBatch = false;
    protected Format xmlFormat;

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

    protected boolean doesDocumentExistToPublish(SimpleRouterContext context) {
        return false;
    }

    @Override
    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData,
                                    Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed,
                                    TriggerRouter triggerRouter) {
        String databaseName = dataMetaData.getTriggerHistory().getSourceCatalogName();
        String schemaName = dataMetaData.getTriggerHistory().getSourceSchemaName();
        String tableName = dataMetaData.getData().getTableName();

        Element change = new Element("change");
        change.setAttribute("type", dataMetaData.getData().getDataEventType().getCode()); // I, U, D

        // Database + Table
        if (databaseName != null)
            change.addContent(new Element("database").setText(databaseName));
        if (schemaName != null)
            change.addContent(new Element("schema").setText(schemaName));
        change.addContent(new Element("table").setText(tableName));

        // Primary Key
        Element key = new Element("key");
        change.addContent(key);
        String[] primaryKeyCols = dataMetaData.getTriggerHistory().getParsedPkColumnNames();
        String[] primaryKeyVal = dataMetaData.getData().toParsedPkData();
        assert primaryKeyCols.length == primaryKeyVal.length;
        for (int i = 0; i < primaryKeyCols.length; ++i) {
            String columnName = primaryKeyCols[i];
            Element column = new Element("column");
            key.addContent(column);
            column.setAttribute("name", columnName);
            column.setText( String.valueOf(primaryKeyVal[i]) );
        }

        // Publish change XML
        String xml = new XMLOutputter(xmlFormat).outputString(new Document(change));
        log.debug("Sending XML to IPublisher: {}", xml);
        publisher.publish(context, xml.toString());

        return Collections.emptySet();
    }

    @Override
    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch) {
    }

    @Override
    public void contextCommitted(SimpleRouterContext context) {
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

}
