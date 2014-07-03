# SymmetricDS Publisher

Custom SymmetricDS router that publishes database change batches to a JMS queue, containing the table, database, schema,
and primary key of the row that changed. This is used for triggering a queue worker which will then re-read the model 
from the database using SqlAlchemy, serialize it to JSON, and cache it in MongoDB.

Changes are published to JMS in the following XML format:

    <changes count="1">
        <change type="U">
            <table>users</table>
            <key>
                <column name="id">chrisl</column>
            </key>
        </change>
        ...
    </changes>

# Installation

1. Run `mvn package` to build the jar file.
2. Copy the jar to `<symmetricds_install_dir>/lib`
3. Edit `<symmetricds_install_dir>/conf/symmetric-extensions.xml`

... 

    <bean id="mqPublisher" class="com.lumapictures.symmetric.integrate.QueueTriggeringDataRouter">
        <property name="publisher">
            <bean class="org.jumpmind.symmetric.integrate.SimpleJmsPublisher">
                <property name="jmsTemplateBeanName" value="mqJmsTemplate" />
                <property name="enabled" value="true"/>
            </bean>
        </property>
        <property name="nodeGroups">
            <list>
                <value>NODE_NAME_GOES_HERE</value>
                <value>...</value>
            </list>
        </property>
    </bean>

    <bean id="jmsFactory" class="org.apache.activemq.spring.ActiveMQConnectionFactory">
        <property name="brokerURL">
            <value>tcp://YOUR_SERVER:61616</value>
        </property>
        <property name="userName">
            <value>YOUR_USERNAME</value>
        </property>
        <property name="password">
            <value>YOUR_PASSWORD</value>
        </property>
    </bean>

    <bean id="destination" class="org.apache.activemq.command.ActiveMQQueue">
        <constructor-arg value="YOUR_QUEUE_NAME" />
    </bean>

    <bean id="mqJmsTemplate" class="org.springframework.jms.core.JmsTemplate">
        <property name="connectionFactory">
            <ref local="jmsFactory" />
        </property>
        <property name="defaultDestination" ref="destination" />
    </bean>
