Publishes all table changes to a configured IPublisher, containing just enough metadata to record the fact that a change occurred. Changes are sent over JMS in an XML format like so:

    <change type="U">
        <table>users</table>
        <key>
            <column name="id">chrisl</column>
        </key>
    </change>
