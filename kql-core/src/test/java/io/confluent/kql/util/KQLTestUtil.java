package io.confluent.kql.util;


import io.confluent.kql.metastore.KQLStream;
import io.confluent.kql.metastore.KQLTable;
import io.confluent.kql.metastore.KQLTopic;
import io.confluent.kql.metastore.MetaStore;
import io.confluent.kql.metastore.MetaStoreImpl;
import io.confluent.kql.serde.json.KQLJsonTopicSerDe;
import org.apache.kafka.connect.data.SchemaBuilder;

public class KQLTestUtil {

  public static MetaStore getNewMetaStore() {

    MetaStore metaStore = new MetaStoreImpl();

    SchemaBuilder schemaBuilder1 = SchemaBuilder.struct()
        .field("COL0", SchemaBuilder.INT64_SCHEMA)
        .field("COL1", SchemaBuilder.STRING_SCHEMA)
        .field("COL2", SchemaBuilder.STRING_SCHEMA)
        .field("COL3", SchemaBuilder.FLOAT64_SCHEMA)
        .field("COL4", SchemaBuilder.array(SchemaBuilder.FLOAT64_SCHEMA))
        .field("COL5", SchemaBuilder.map(SchemaBuilder.STRING_SCHEMA, SchemaBuilder.FLOAT64_SCHEMA));

    KQLTopic
        kqlTopic1 =
        new KQLTopic("TEST1", "test1", new KQLJsonTopicSerDe(null));

    KQLStream kqlStream = new KQLStream("TEST1", schemaBuilder1, schemaBuilder1.field("COL0"),
                                        kqlTopic1);

    metaStore.putTopic(kqlTopic1);
    metaStore.putSource(kqlStream);

    SchemaBuilder schemaBuilder2 = SchemaBuilder.struct()
        .field("COL0", SchemaBuilder.INT64_SCHEMA)
        .field("COL1", SchemaBuilder.STRING_SCHEMA)
        .field("COL2", SchemaBuilder.STRING_SCHEMA)
        .field("COL3", SchemaBuilder.FLOAT64_SCHEMA)
        .field("COL4", SchemaBuilder.BOOLEAN_SCHEMA);

    KQLTopic
        kqlTopic2 =
        new KQLTopic("TEST2", "test2", new KQLJsonTopicSerDe(null));
    KQLTable kqlTable = new KQLTable("TEST2", schemaBuilder2, schemaBuilder2.field("COL0"),
                                     kqlTopic2, "TEST2", false);

    metaStore.putTopic(kqlTopic2);
    metaStore.putSource(kqlTable);

    SchemaBuilder schemaBuilderOrders = SchemaBuilder.struct()
            .field("ORDERTIME", SchemaBuilder.INT64_SCHEMA)
            .field("ORDERID", SchemaBuilder.STRING_SCHEMA)
            .field("ITEMID", SchemaBuilder.STRING_SCHEMA)
            .field("ORDERUNITS", SchemaBuilder.FLOAT64_SCHEMA);

    KQLTopic
            kqlTopicOrders =
            new KQLTopic("ORDERS_TOPIC", "orders_topic", new KQLJsonTopicSerDe(null));

    KQLStream kqlStreamOrders = new KQLStream("ORDERS", schemaBuilderOrders, schemaBuilderOrders.field("ORDERTIME"),
                                              kqlTopicOrders);

    metaStore.putTopic(kqlTopicOrders);
    metaStore.putSource(kqlStreamOrders);

    return metaStore;
  }
}
