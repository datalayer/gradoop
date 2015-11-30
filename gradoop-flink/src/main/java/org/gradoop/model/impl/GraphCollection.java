/*
 * This file is part of gradoop.
 *
 * gradoop is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * gradoop is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with gradoop. If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * This file is part of Gradoop.
 *
 * Gradoop is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Gradoop is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Gradoop.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.gradoop.model.impl;

import com.google.common.collect.Lists;
import org.apache.commons.lang.NotImplementedException;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple1;
import org.gradoop.io.json.JsonWriter;
import org.gradoop.model.api.EPGMEdge;
import org.gradoop.model.api.EPGMGraphHead;
import org.gradoop.model.api.EPGMVertex;
import org.gradoop.model.api.operators.BinaryCollectionToCollectionOperator;
import org.gradoop.model.api.operators.BinaryGraphToGraphOperator;
import org.gradoop.model.api.operators.GraphCollectionOperators;
import org.gradoop.model.api.operators.UnaryCollectionToCollectionOperator;
import org.gradoop.model.api.operators.UnaryCollectionToGraphOperator;
import org.gradoop.model.api.operators.UnaryGraphToGraphOperator;
import org.gradoop.model.impl.functions.api.Predicate;
import org.gradoop.model.impl.functions.epgm.Id;
import org.gradoop.model.impl.functions.graphcontainment.GraphsContainmentFilterBroadcast;


import org.gradoop.model.impl.functions.graphcontainment.InAnyGraph;
import org.gradoop.model.impl.functions.graphcontainment.InAnyGraphBroadcast;
import org.gradoop.model.impl.functions.graphcontainment.InGraph;
import org.gradoop.model.impl.functions.epgm.ById;

import org.gradoop.model.impl.id.GradoopId;
import org.gradoop.model.impl.id.GradoopIdSet;
import org.gradoop.model.impl.operators.difference.Difference;
import org.gradoop.model.impl.operators.difference.DifferenceBroadcast;
import org.gradoop.model.impl.operators.intersection.Intersection;
import org.gradoop.model.impl.operators.intersection.IntersectionBroadcast;
import org.gradoop.model.impl.operators.union.Union;
import org.gradoop.model.impl.operators.equality.EqualityByGraphElementIds;
import org.gradoop.model.impl.operators.equality.EqualityByGraphIds;
import org.gradoop.util.GradoopFlinkConfig;
import org.gradoop.util.Order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents a collection of graphs inside the EPGM. As graphs may share
 * vertices and edges, the collections contains a single gelly graph
 * representing all subgraphs. Graph data is stored in an additional dataset.
 *
 * @param <G> EPGM graph head type
 * @param <V> EPGM vertex type
 * @param <E> EPGM edge type
 */
public class GraphCollection<
  G extends EPGMGraphHead,
  V extends EPGMVertex,
  E extends EPGMEdge>
  extends GraphBase<G, V, E>
  implements GraphCollectionOperators<G, V, E> {

  /**
   * Creates a graph collection from the given arguments.
   *
   * @param graphHeads  graph heads
   * @param vertices    vertices
   * @param edges       edges
   * @param config      Gradoop Flink configuration
   */
  private GraphCollection(DataSet<G> graphHeads,
    DataSet<V> vertices,
    DataSet<E> edges,
    GradoopFlinkConfig<G, V, E> config) {
    super(graphHeads, vertices, edges, config);
  }

  /**
   * Creates an empty graph collection.
   *
   * @param config  Gradoop Flink configuration
   * @param <G>     EPGM graph head type
   * @param <V>     EPGM vertex type
   * @param <E>     EPGM edge type
   * @return empty graph collection
   */
  public static <
    G extends EPGMGraphHead,
    V extends EPGMVertex,
    E extends EPGMEdge> GraphCollection<G, V, E> createEmptyCollection(
    GradoopFlinkConfig<G, V, E> config) {
    Collection<G> graphHeads = new ArrayList<>();
    Collection<V> vertices = new ArrayList<>();
    Collection<E> edges = new ArrayList<>();

    return GraphCollection.fromCollections(graphHeads, vertices, edges, config);
  }

  /**
   * Creates a graph collection from the given arguments.
   *
   * @param <G>         EPGM graph head type
   * @param <V>         EPGM vertex type
   * @param <E>         EPGM edge type
   * @param graphHeads  GraphHead DataSet
   * @param vertices    Vertex DataSet
   * @param edges       Edge DataSet
   * @param config      Gradoop Flink configuration
   * @return Graph collection
   */
  public static
  <G extends EPGMGraphHead, V extends EPGMVertex, E extends EPGMEdge>

  GraphCollection<G, V, E>
  fromDataSets(DataSet<G> graphHeads, DataSet<V> vertices, DataSet<E> edges,
    GradoopFlinkConfig<G, V, E> config) {

    return new GraphCollection<>(graphHeads, vertices, edges, config);
  }

  /**
   * Creates a new graph collection from the given collection.
   *
   * @param graphHeads  Graph Head collection
   * @param vertices    Vertex collection
   * @param edges       Edge collection
   * @param config      Gradoop Flink configuration
   * @param <G>         EPGM graph type
   * @param <V>         EPGM vertex type
   * @param <E>         EPGM edge type
   * @return Graph collection
   */
  public static
  <G extends EPGMGraphHead, V extends EPGMVertex, E extends EPGMEdge>

  GraphCollection<G, V, E> fromCollections(
    Collection<G> graphHeads,
    Collection<V> vertices,
    Collection<E> edges,
    GradoopFlinkConfig<G, V, E> config) {

    return fromDataSets(
      createGraphHeadDataSet(graphHeads, config),
      createVertexDataSet(vertices, config),
      createEdgeDataSet(edges, config),
      config
    );
  }

  /**
   * Returns the graph heads associated with the logical graphs in that
   * collection.
   *
   * @return graph heads
   */
  public DataSet<G> getGraphHeads() {
    return this.graphHeads;
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  @Override
  public LogicalGraph<G, V, E> getGraph(final GradoopId graphID) throws
    Exception {
    // filter vertices and edges based on given graph id
    DataSet<G> graphHead = getGraphHeads()
      .filter(new ById<G>(graphID));

    DataSet<V> vertices = getVertices()
      .filter(new InGraph<V>(graphID));
    DataSet<E> edges = getEdges()
      .filter(new InGraph<E>(graphID));

    DataSet<Tuple1<GradoopId>> graphIDDataSet = getConfig()
      .getExecutionEnvironment()
      .fromCollection(Lists.newArrayList(new Tuple1<>(graphID)));

    // get graph data based on graph id
    List<G> graphData = this.graphHeads
      .joinWithTiny(graphIDDataSet)
      .where(new Id<G>())
      .equalTo(0)
      .with(new JoinFunction<G, Tuple1<GradoopId>, G>() {
        @Override
        public G join(G g, Tuple1<GradoopId> gID) throws Exception {
          return g;
        }
      }).first(1).collect();

    return (graphData.size() > 0) ? LogicalGraph
      .fromDataSets(graphHead, vertices, edges, getConfig()) : null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E>
  getGraphs(final GradoopId... identifiers) throws Exception {

    GradoopIdSet graphIds = new GradoopIdSet();

    for (GradoopId id : identifiers) {
      graphIds.add(id);
    }

    return getGraphs(graphIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> getGraphs(
    final GradoopIdSet identifiers) throws Exception {

    DataSet<G> newGraphHeads =
      this.graphHeads.filter(new FilterFunction<G>() {

        @Override
        public boolean filter(G graphHead) throws Exception {
          return identifiers.contains(graphHead.getId());

        }
      });

    // build new vertex set
    DataSet<V> vertices = getVertices()
      .filter(new InAnyGraph<V>(identifiers));

    // build new edge set
    DataSet<E> edges = getEdges()
      .filter(new InAnyGraph<E>(identifiers));

    return new GraphCollection<>(newGraphHeads, vertices, edges, getConfig());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getGraphCount() throws Exception {
    return this.graphHeads.count();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> filter(
    final Predicate<G> predicateFunction) throws Exception {
    // find graph heads matching the predicate
    DataSet<G> filteredGraphHeads =
      this.graphHeads.filter(new FilterFunction<G>() {
        @Override
        public boolean filter(G g) throws Exception {
          return predicateFunction.filter(g);
        }
      });

    // get the identifiers of these subgraphs
    DataSet<GradoopId> graphIDs =
      filteredGraphHeads.map(new MapFunction<G, GradoopId>() {
        @Override
        public GradoopId map(G g) throws Exception {
          return g.getId();
        }
      });

    // use graph ids to filter vertices from the actual graph structure
    DataSet<V> vertices = getVertices()
      .filter(new InAnyGraphBroadcast<V>())
      .withBroadcastSet(graphIDs,
        GraphsContainmentFilterBroadcast.GRAPH_IDS);

    DataSet<E> edges = getEdges()
      .filter(new InAnyGraphBroadcast<E>())
      .withBroadcastSet(graphIDs,
        GraphsContainmentFilterBroadcast.GRAPH_IDS);

    return new GraphCollection<>(filteredGraphHeads, vertices, edges,
      getConfig());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> select(
    Predicate<LogicalGraph<G, V, E>> predicateFunction) throws Exception {
    throw new NotImplementedException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> union(
    GraphCollection<G, V, E> otherCollection) throws Exception {
    return callForCollection(new Union<G, V, E>(), otherCollection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> intersect(
    GraphCollection<G, V, E> otherCollection) throws Exception {
    return callForCollection(new Intersection<G, V, E>(), otherCollection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> intersectWithSmallResult(
    GraphCollection<G, V, E> otherCollection) throws Exception {
    return callForCollection(new IntersectionBroadcast<G, V, E>(),
      otherCollection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> difference(
    GraphCollection<G, V, E> otherCollection) throws Exception {
    return callForCollection(new Difference<G, V, E>(), otherCollection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> differenceWithSmallResult(
    GraphCollection<G, V, E> otherCollection) throws Exception {
    return callForCollection(new DifferenceBroadcast<G, V, E>(),
      otherCollection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> distinct() {
    throw new NotImplementedException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> sortBy(String propertyKey, Order order) {
    throw new NotImplementedException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> top(int limit) {
    throw new NotImplementedException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> apply(
    UnaryGraphToGraphOperator<G, V, E> op) {
    throw new NotImplementedException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LogicalGraph<G, V, E> reduce(
    BinaryGraphToGraphOperator<G, V, E> op) {
    throw new NotImplementedException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> callForCollection(
    UnaryCollectionToCollectionOperator<V, E, G> op) {
    return op.execute(this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GraphCollection<G, V, E> callForCollection(
    BinaryCollectionToCollectionOperator<G, V, E> op,
    GraphCollection<G, V, E> otherCollection) throws Exception {
    return op.execute(this, otherCollection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LogicalGraph<G, V, E> callForGraph(
    UnaryCollectionToGraphOperator<G, V, E> op) {
    return op.execute(this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writeAsJson(String vertexFile, String edgeFile,
    String graphFile) throws Exception {
    getVertices().writeAsFormattedText(vertexFile,
      new JsonWriter.VertexTextFormatter<V>());
    getEdges().writeAsFormattedText(edgeFile,
      new JsonWriter.EdgeTextFormatter<E>());
    getGraphHeads().writeAsFormattedText(graphFile,
      new JsonWriter.GraphTextFormatter<G>());
    getConfig().getExecutionEnvironment().execute();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DataSet<Boolean> equalsByGraphIds(GraphCollection<G, V, E> other) {
    return new EqualityByGraphIds<G, V, E>().execute(this, other);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Boolean equalsByGraphIdsCollected(
    GraphCollection<G, V, E> other) throws Exception {
    return collectEquals(equalsByGraphIds(other));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DataSet<Boolean> equalsByGraphElementIds(
    GraphCollection<G, V, E> other) {
    return new EqualityByGraphElementIds<G, V, E>().execute(this, other);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Boolean equalsByGraphElementIdsCollected(
    GraphCollection<G, V, E> other) throws Exception {
    return collectEquals(equalsByGraphElementIds(other));
  }
}
