package org.jumpmind.symmetric.is.core.runtime.component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.is.core.model.Model;
import org.jumpmind.symmetric.is.core.model.SettingDefinition;
import org.jumpmind.symmetric.is.core.model.SettingDefinition.Type;
import org.jumpmind.symmetric.is.core.runtime.EntityData;
import org.jumpmind.symmetric.is.core.runtime.Message;
import org.jumpmind.symmetric.is.core.runtime.StartupMessage;
import org.jumpmind.symmetric.is.core.runtime.flow.IMessageTarget;

@ComponentDefinition(
        category = ComponentCategory.PROCESSOR,
        typeName = Joiner.TYPE,
        iconImage = "joiner.png",
        inputMessage = MessageType.ENTITY,
        outgoingMessage = MessageType.ENTITY,
        inputOutputModelsMatch=true
        )
public class Joiner extends AbstractComponentRuntime {

    public static final String TYPE = "Joiner";

    @SettingDefinition(
            order = 10,
            required = true,
            type = Type.TEXT,
            label = "Join Entity.Attribute")
    public final static String JOIN_ATTRIBUTE = "join.attribute";

    @SettingDefinition(
            order = 20,
            required = false,
            type = Type.INTEGER,
            defaultValue = "10",
            label = "Rows/Msg")
    public final static String ROWS_PER_MESSAGE = "rows.per.message";

    int rowsPerMessage;
    String joinAttribute;
    String joinAttributeId;
    Map<Object, EntityData> joinedData = new LinkedHashMap<Object, EntityData>();

    @Override
    protected void start() {        
        applySettings();
    }

    @Override
    public void handle(Message inputMessage, IMessageTarget messageTarget) {
        getComponentStatistics().incrementInboundMessages();
        if (!(inputMessage instanceof StartupMessage)) {
            ArrayList<EntityData> payload = inputMessage.getPayload();
            join(payload);
        }
    }

    @Override
    public void lastMessageReceived(IMessageTarget messageTarget) {        
        ArrayList<EntityData> dataToSend=new ArrayList<EntityData>();
        Iterator<EntityData> itr = joinedData.values().iterator();
        int nbrRecs=0;
        while (itr.hasNext()) {
            if (nbrRecs >= rowsPerMessage) {
                sendMessage(dataToSend, messageTarget, false);
                dataToSend = new ArrayList<EntityData>();
                nbrRecs = 0;
            }
            nbrRecs++;            
            dataToSend.add(itr.next());
        }
        if (dataToSend != null && dataToSend.size() > 0) {
            sendMessage(dataToSend, messageTarget, true);
        }
    }
    
    private void sendMessage(ArrayList<EntityData> dataToSend, IMessageTarget messageTarget,
            boolean lastMessage) {        
        Message newMessage = new Message(getFlowStepId());
        newMessage.getHeader().setLastMessage(lastMessage);
        newMessage.setPayload(dataToSend);
        getComponentStatistics().incrementOutboundMessages();
        messageTarget.put(newMessage);      
    }
    
    private void applySettings() {
        TypedProperties properties = getComponent().toTypedProperties(getSettingDefinitions(false));
        joinAttribute = properties.get(JOIN_ATTRIBUTE);
        if (joinAttribute == null) {
            throw new IllegalStateException("Join attribute must be specified.");
        }
        Model inputModel = this.getComponent().getInputModel();
        String[] joinAttributeElements = joinAttribute.split("[.]");
        if (joinAttributeElements.length != 2) {
            throw new IllegalStateException("Join attribute must be specified as 'entity.attribute'");
        }
        joinAttributeId = inputModel.getAttributeByName(joinAttributeElements[0], joinAttributeElements[1]).getId();
        if (joinAttributeId == null) {
            throw new IllegalStateException("Join attribute must be a valid 'entity.attribute' in the input model.");
        }   
    }
    
    private void join(ArrayList<EntityData> records) {
        
        for (int i=0;i<records.size();i++) {
            getComponentStatistics().incrementNumberEntitiesProcessed();
            EntityData record = records.get(i);
            Object keyValue = record.get(joinAttributeId);
            EntityData existingRecord = joinedData.get(keyValue);
            if (existingRecord != null) {
                mergeRecords(record, existingRecord);
                joinedData.put(keyValue, existingRecord);
            } else {
                joinedData.put(keyValue, record);
            }
        }
    }
    
    private void mergeRecords(EntityData sourceRecord, EntityData targetRecord) {

        Iterator<Map.Entry<String, Object>> itr = sourceRecord.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String,Object> column = (Map.Entry<String, Object>)itr.next();
            if (column != null) {
                targetRecord.put(column.getKey(), column.getValue());
            }
        }
    }
}
