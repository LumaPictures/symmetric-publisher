# SymmetricDS Publisher

This is a custom SymmetricDS router that publishes all changes to a message queue. We have some Python queue workers that will then operate on the data to transform and store it in various ways.

Changes are published in the following XML format:

    <changes count="1">
        <change type="U">
            <database>somedb</database>
            <table>users</table>
            <key>
                <column name="id">chris</column>
            </key>
            <old>
                <column name="id">chris</column>
                <column name="first_name">Chris</column>
                <column name="last_name">Lyon</column>
            </old>
        </change>
        ...
    </changes>

# Installation

1. Run `mvn package` to build the jar file.
2. Copy the jar to `<symmetricds_install_dir>/lib`
3. Edit `<symmetricds_install_dir>/conf/symmetric-extensions.xml` and configure the Spring beans.

Here's an example Spring bean configuration that wires symmetric-publisher to an ActiveMQ message queue:

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
