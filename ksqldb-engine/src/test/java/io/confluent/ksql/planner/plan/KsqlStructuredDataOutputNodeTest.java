/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.planner.plan;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.confluent.ksql.execution.builder.KsqlQueryBuilder;
import io.confluent.ksql.execution.context.QueryContext;
import io.confluent.ksql.execution.ddl.commands.KsqlTopic;
import io.confluent.ksql.execution.plan.SelectExpression;
import io.confluent.ksql.metastore.model.DataSource.DataSourceType;
import io.confluent.ksql.metastore.model.KeyField;
import io.confluent.ksql.name.ColumnName;
import io.confluent.ksql.name.SourceName;
import io.confluent.ksql.query.QueryId;
import io.confluent.ksql.query.id.QueryIdGenerator;
import io.confluent.ksql.schema.ksql.LogicalSchema;
import io.confluent.ksql.schema.ksql.types.SqlTypes;
import io.confluent.ksql.serde.FormatFactory;
import io.confluent.ksql.serde.FormatInfo;
import io.confluent.ksql.serde.SerdeOption;
import io.confluent.ksql.serde.ValueFormat;
import io.confluent.ksql.serde.avro.AvroFormat;
import io.confluent.ksql.structured.SchemaKStream;
import io.confluent.ksql.util.KsqlException;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KsqlStructuredDataOutputNodeTest {

  private static final String QUERY_ID_VALUE = "output-test";

  private static final String SINK_KAFKA_TOPIC_NAME = "output_kafka";

  private static final LogicalSchema SCHEMA = LogicalSchema.builder()
      .keyColumn(ColumnName.of("k0"), SqlTypes.STRING)
      .valueColumn(ColumnName.of("field1"), SqlTypes.STRING)
      .valueColumn(ColumnName.of("field2"), SqlTypes.STRING)
      .valueColumn(ColumnName.of("field3"), SqlTypes.STRING)
      .valueColumn(ColumnName.of("timestamp"), SqlTypes.BIGINT)
      .valueColumn(ColumnName.of("key"), SqlTypes.STRING)
      .build();

  private static final KeyField KEY_FIELD = KeyField
      .of(ColumnName.of("key"));

  private static final PlanNodeId PLAN_NODE_ID = new PlanNodeId("0");
  private static final ValueFormat JSON_FORMAT = ValueFormat.of(FormatInfo.of(FormatFactory.JSON.name()));

  @Mock
  private QueryIdGenerator queryIdGenerator;
  @Mock
  private KsqlQueryBuilder ksqlStreamBuilder;
  @Mock
  private PlanNode sourceNode;
  @Mock
  private SchemaKStream<String> sourceStream;
  @Mock
  private SchemaKStream<?> sinkStream;
  @Mock
  private KsqlTopic ksqlTopic;
  @Captor
  private ArgumentCaptor<QueryContext.Stacker> stackerCaptor;

  private KsqlStructuredDataOutputNode outputNode;
  private boolean createInto;

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Before
  public void before() {
    createInto = true;

    when(queryIdGenerator.getNext()).thenReturn(QUERY_ID_VALUE);

    when(sourceNode.getSchema()).thenReturn(LogicalSchema.builder().build());
    when(sourceNode.getNodeOutputType()).thenReturn(DataSourceType.KSTREAM);
    when(sourceNode.buildStream(ksqlStreamBuilder)).thenReturn((SchemaKStream) sourceStream);

    when(sourceStream.into(any(), any(), any(), any(), any()))
        .thenReturn((SchemaKStream) sinkStream);

    when(ksqlStreamBuilder.buildNodeContext(any())).thenAnswer(inv ->
        new QueryContext.Stacker()
            .push(inv.getArgument(0).toString()));
    when(ksqlTopic.getKafkaTopicName()).thenReturn(SINK_KAFKA_TOPIC_NAME);
    when(ksqlTopic.getValueFormat()).thenReturn(JSON_FORMAT);

    buildNode();
  }

  @Test
  public void shouldThrowIfSelectExpressionsHaveSameNameAsAnyKeyColumn() {
    // Given:
    givenSourceSchema(
        LogicalSchema.builder()
            .keyColumn(ColumnName.of("k0"), SqlTypes.INTEGER)
            .valueColumn(ColumnName.of("field1"), SqlTypes.STRING)
            .valueColumn(ColumnName.of("k0"), SqlTypes.STRING)
            .valueColumn(ColumnName.of("field2"), SqlTypes.STRING)
            .build()
    );

    // When:
    final Exception e = assertThrows(
        KsqlException.class,
        () -> buildNode()
    );

    // Then:
    assertThat(e.getMessage(), containsString(
        "Value column name(s) `k0` "
            + "clashes with key column name(s)."));
  }

  @Test
  public void shouldBuildSourceNode() {
    // When:
    outputNode.buildStream(ksqlStreamBuilder);

    // Then:
    verify(sourceNode).buildStream(ksqlStreamBuilder);
  }

  @Test
  public void shouldComputeQueryIdCorrectlyForStream() {
    // When:
    final QueryId queryId = outputNode.getQueryId(queryIdGenerator);

    // Then:
    verify(queryIdGenerator, times(1)).getNext();
    assertThat(queryId, equalTo(new QueryId("CSAS_0_" + QUERY_ID_VALUE)));
  }

  @Test
  public void shouldComputeQueryIdCorrectlyForTable() {
    // Given:
    when(sourceNode.getNodeOutputType()).thenReturn(DataSourceType.KTABLE);
    buildNode();

    // When:
    final QueryId queryId = outputNode.getQueryId(queryIdGenerator);

    // Then:
    verify(queryIdGenerator, times(1)).getNext();
    assertThat(queryId, equalTo(new QueryId("CTAS_0_" + QUERY_ID_VALUE)));
  }

  @Test
  public void shouldComputeQueryIdCorrectlyForInsertInto() {
    // Given:
    givenInsertIntoNode();

    // When:
    final QueryId queryId = outputNode.getQueryId(queryIdGenerator);

    // Then:
    verify(queryIdGenerator, times(1)).getNext();
    assertThat(queryId, equalTo(new QueryId("INSERTQUERY_" + QUERY_ID_VALUE)));
  }

  @Test
  public void shouldBuildOutputNodeForInsertIntoAvroFromNonAvro() {
    // Given:
    givenInsertIntoNode();

    final ValueFormat valueFormat = ValueFormat.of(FormatInfo.of(FormatFactory.AVRO.name(), ImmutableMap
        .of(AvroFormat.FULL_SCHEMA_NAME, "name")));

    when(ksqlTopic.getValueFormat()).thenReturn(valueFormat);

    // When/Then (should not throw):
    outputNode.buildStream(ksqlStreamBuilder);

    // Then:
    verify(sourceStream).into(any(), eq(valueFormat), any(), any(), any());
  }

  @Test
  public void shouldCallInto() {
    // When:
    final SchemaKStream<?> result = outputNode.buildStream(ksqlStreamBuilder);

    // Then:
    verify(sourceStream).into(
        eq(SINK_KAFKA_TOPIC_NAME),
        eq(JSON_FORMAT),
        eq(SerdeOption.none()),
        stackerCaptor.capture(),
        eq(outputNode.getTimestampColumn())
    );
    assertThat(
        stackerCaptor.getValue().getQueryContext().getContext(),
        equalTo(ImmutableList.of("0"))
    );
    assertThat(result, sameInstance(sinkStream));
  }

  private void givenInsertIntoNode() {
    this.createInto = false;
    buildNode();
  }

  private void buildNode() {
    outputNode = new KsqlStructuredDataOutputNode(
        PLAN_NODE_ID,
        sourceNode,
        SCHEMA,
        Optional.empty(),
        KEY_FIELD,
        ksqlTopic,
        OptionalInt.empty(),
        createInto,
        SerdeOption.none(),
        SourceName.of(PLAN_NODE_ID.toString()));
  }

  private void givenSourceSchema(final LogicalSchema schema) {
    when(sourceNode.getSchema())
        .thenReturn(schema);
  }

  private static SelectExpression selectExpression(final String alias) {
    final SelectExpression mock = mock(SelectExpression.class);
    when(mock.getAlias()).thenReturn(ColumnName.of(alias));
    return mock;
  }
}
