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
 * along with Gradoop. If not, see <http://www.gnu.org/licenses/>.
 */
package org.gradoop.flink.model.impl.operators.tostring.functions;

import org.gradoop.common.model.impl.pojo.GraphHead;
import org.gradoop.flink.model.impl.operators.tostring.api.GraphHeadToString;
import org.gradoop.flink.model.impl.operators.tostring.tuples.GraphHeadString;

/**
 * represents a graph head by an id string
 */
public class GraphHeadToIdString implements GraphHeadToString<GraphHead> {

  @Override
  public GraphHeadString map(GraphHead graphHead) throws Exception {
    return new GraphHeadString(graphHead.getId(), graphHead.getId().toString());
  }
}
