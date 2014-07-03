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
    