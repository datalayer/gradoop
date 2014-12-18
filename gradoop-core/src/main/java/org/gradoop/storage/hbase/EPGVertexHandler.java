package org.gradoop.storage.hbase;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;
import org.gradoop.GConstants;
import org.gradoop.model.Edge;
import org.gradoop.model.GraphElement;
import org.gradoop.model.Vertex;
import org.gradoop.model.inmemory.MemoryEdge;
import org.gradoop.model.inmemory.MemoryVertex;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Used to read and write an EPG vertex from/to a HBase table.
 */
public class EPGVertexHandler extends BasicHandler
  implements VertexHandler {
  private static Logger LOG =
    Logger.getLogger(EPGVertexHandler.class);

  /**
   * Byte array representation of the outgoing edges column family.
   */
  private static final byte[] CF_OUT_EDGES_BYTES =
    Bytes.toBytes(GConstants.CF_OUT_EDGES);
  /**
   * Byte array representation of the incoming edges column family.
   */
  private static final byte[] CF_IN_EDGES_BYTES =
    Bytes.toBytes(GConstants.CF_IN_EDGES);
  /**
   * Byte array representation of the graphs column family.
   */
  private static final byte[] CF_GRAPHS_BYTES =
    Bytes.toBytes(GConstants.CF_GRAPHS);

  /**
   * Separates a property string into tokens.
   */
  private static final String PROPERTY_TOKEN_SEPARATOR_STRING = " ";
  /**
   * Separates a property string into tokens.
   */
  private static final Pattern PROPERTY_TOKEN_SEPARATOR_PATTERN =
    Pattern.compile(" ");
  /**
   * Separates an edge key into tokens.
   */
  private static final String EDGE_KEY_TOKEN_SEPARATOR_STRING = ".";
  /**
   * Separates an edge key into tokens.
   */
  private static final Pattern EDGE_KEY_TOKEN_SEPARATOR_PATTERN =
    Pattern.compile("\\.");

  /**
   * {@inheritDoc}
   */
  @Override
  public void createVerticesTable(final HBaseAdmin admin,
                                  final HTableDescriptor tableDescriptor)
    throws IOException {
    LOG.info("creating table " + tableDescriptor.getNameAsString());
    tableDescriptor.addFamily(new HColumnDescriptor(GConstants.CF_LABELS));
    tableDescriptor
      .addFamily(new HColumnDescriptor(GConstants.CF_PROPERTIES));
    tableDescriptor
      .addFamily(new HColumnDescriptor(GConstants.CF_OUT_EDGES));
    tableDescriptor
      .addFamily(new HColumnDescriptor(GConstants.CF_IN_EDGES));
    tableDescriptor.addFamily(new HColumnDescriptor(GConstants.CF_GRAPHS));
    admin.createTable(tableDescriptor);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getRowKey(final Long vertexID) {
    if (vertexID == null) {
      throw new IllegalArgumentException("vertexID must not be null");
    }
    return Bytes.toBytes(vertexID.toString());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getVertexID(final byte[] rowKey) {
    if (rowKey == null) {
      throw new IllegalArgumentException("rowKey must not be null");
    }
    return Long.valueOf(Bytes.toString(rowKey));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Put writeOutgoingEdges(final Put put, final Iterable<? extends Edge>
    edges) {
    return writeEdges(put, CF_OUT_EDGES_BYTES, edges);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Put writeIncomingEdges(final Put put,
                                final Iterable<? extends Edge> edges) {
    return writeEdges(put, CF_IN_EDGES_BYTES, edges);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Put writeGraphs(final Put put, final GraphElement graphElement) {
    for (Long graphID : graphElement.getGraphs()) {
      put.add(CF_GRAPHS_BYTES, Bytes.toBytes(graphID), null);
    }
    return put;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Put writeVertex(final Put put, final Vertex vertex) {
    writeLabels(put, vertex);
    writeProperties(put, vertex);
    writeOutgoingEdges(put, vertex.getOutgoingEdges());
    writeIncomingEdges(put, vertex.getIncomingEdges());
    writeGraphs(put, vertex);
    return put;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterable<Edge> readOutgoingEdges(final Result res) {
    return readEdges(res, CF_OUT_EDGES_BYTES);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterable<Edge> readIncomingEdges(final Result res) {
    return readEdges(res, CF_IN_EDGES_BYTES);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterable<Long> readGraphs(final Result res) {
    return getColumnKeysFromFamiliy(res, CF_GRAPHS_BYTES);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Vertex readVertex(final Result res) {
    return new MemoryVertex(
      Long.valueOf(Bytes.toString(res.getRow())),
      readLabels(res),
      readProperties(res),
      readOutgoingEdges(res),
      readIncomingEdges(res), readGraphs(res));
  }

  /**
   * Adds edges to the the given HBase put.
   *
   * @param put          {@link org.apache.hadoop.hbase.client.Put} to write the
   *                     edges to
   * @param columnFamily CF where the edges shall be stored
   * @param edges        edges to store
   * @return the updated put
   */
  private Put writeEdges(Put put, final byte[] columnFamily,
                         final Iterable<? extends Edge> edges) {
    if (edges != null) {
      for (Edge edge : edges) {
        put = writeEdge(put, columnFamily, edge);
      }
    }
    return put;
  }

  /**
   * Writes a single edge to a given put.
   *
   * @param put          {@link org.apache.hadoop.hbase.client.Put} to write the
   *                     edge to
   * @param columnFamily CF where the edges shall be stored
   * @param edge         edge to store
   * @return the updated put
   */
  private Put writeEdge(final Put put, final byte[] columnFamily,
                        final Edge edge) {
    String edgeKey = createEdgeIdentifier(edge);
    byte[] edgeKeyBytes = Bytes.toBytes(edgeKey);
    String properties = createEdgePropertiesString(edge);
    byte[] propertiesBytes = Bytes.toBytes(properties);
    put.add(columnFamily, edgeKeyBytes, propertiesBytes);
    return put;
  }

  /**
   * Creates a vertex centric edge identifier from the given edge. Edge keys
   * have the following format:
   * <p/>
   * <edge-identifier> ::= <label>.<otherID>.<index>
   *
   * @param edge edge to create identifier for
   * @return string representation of the edge identifier
   */
  private String createEdgeIdentifier(final Edge edge) {
    return String.format("%s%s%d%s%d",
      edge.getLabel(),
      EDGE_KEY_TOKEN_SEPARATOR_STRING,
      edge.getOtherID(),
      EDGE_KEY_TOKEN_SEPARATOR_STRING,
      edge.getIndex());
  }

  /**
   * Creates a string representation of edge properties which are stored as
   * column value for the edge identifier.
   *
   * @param edge edge to create property string for
   * @return string representation of the edge properties
   */
  private String createEdgePropertiesString(final Edge edge) {
    String result = "";
    Iterable<String> propertyKeys = edge.getPropertyKeys();
    if (propertyKeys != null) {
      final List<String> propertyStrings = Lists.newArrayList();
      for (String propertyKey : propertyKeys) {
        Object propertyValue = edge.getProperty(propertyKey);
        String propertyString = String.format("%s%s%d%s%s",
          propertyKey,
          PROPERTY_TOKEN_SEPARATOR_STRING,
          getType(propertyValue),
          PROPERTY_TOKEN_SEPARATOR_STRING,
          propertyValue);
        propertyStrings.add(propertyString);
      }
      result = Joiner.on(PROPERTY_TOKEN_SEPARATOR_STRING).join
        (propertyStrings);
    }
    return result;
  }

  /**
   * Reads edges from a given HBase row result.
   *
   * @param res          {@link org.apache.hadoop.hbase.client.Result} to read
   *                     edges from
   * @param columnFamily column family where the edges are stored
   * @return edges
   */
  private Iterable<Edge> readEdges(final Result res,
                                   final byte[] columnFamily) {
    final List<Edge> edges = Lists.newArrayList();
    for (Map.Entry<byte[], byte[]> edgeColumn : res.getFamilyMap(columnFamily)
      .entrySet()) {
      String edgeKey = Bytes.toString(edgeColumn.getKey());
      Map<String, Object> edgeProperties = new HashMap<>();
      String propertyString = Bytes.toString(edgeColumn.getValue());
      if (propertyString.length() > 0) {
        String[] tokens =
          PROPERTY_TOKEN_SEPARATOR_PATTERN.split(propertyString);
        for (int i = 0; i < tokens.length; i += 3) {
          String propertyKey = tokens[i];
          byte propertyType = Byte.parseByte(tokens[i + 1]);
          Object propertyValue =
            decodeValueFromString(propertyType, tokens[i + 2]);
          edgeProperties.put(propertyKey, propertyValue);
        }
      }
      edges.add(readEdge(edgeKey, edgeProperties));
    }
    return edges;
  }

  /**
   * Creates an edge object based on the given key and properties. The given
   * edge key is separated into tokens and used to create a new {@link
   * org.gradoop.model.inmemory.MemoryEdge} instance.
   *
   * @param edgeKey    string representation of edge key
   * @param properties key-value-map
   * @return Edge object
   */
  private Edge readEdge(final String edgeKey, final Map<String,
    Object> properties) {
    String[] keyTokens = EDGE_KEY_TOKEN_SEPARATOR_PATTERN.split(edgeKey);
    String edgeLabel = keyTokens[0];
    Long otherID = Long.valueOf(keyTokens[1]);
    Long edgeIndex = Long.valueOf(keyTokens[2]);
    return new MemoryEdge(otherID, edgeLabel, edgeIndex, properties);
  }
}
