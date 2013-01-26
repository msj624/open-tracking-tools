package org.opentrackingtools.graph.paths.states.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.VectorFactory;

import java.util.List;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opentrackingtools.graph.edges.InferredEdge;
import org.opentrackingtools.graph.edges.impl.SimpleInferredEdge;
import org.opentrackingtools.graph.otp.impl.OtpGraph;
import org.opentrackingtools.graph.paths.InferredPath;
import org.opentrackingtools.graph.paths.edges.impl.SimplePathEdge;
import org.opentrackingtools.graph.paths.impl.SimpleInferredPath;
import org.opentrackingtools.graph.paths.impl.TrackingTestUtils;
import org.opentrackingtools.graph.paths.states.AbstractPathState;
import org.opentrackingtools.graph.paths.states.AbstractPathState.PathMergeResults;
import org.opentrackingtools.graph.paths.states.impl.SimplePathState;
import org.opentripplanner.routing.edgetype.PlainStreetEdge;
import org.opentripplanner.routing.edgetype.StreetTraversalPermission;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.StreetVertex;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

public class PathStateTest {

  private SimpleInferredPath p1;
  private SimpleInferredPath p1_neg;
  private SimpleInferredPath p2;
  private InferredPath p2_neg;
  private InferredPath p2_rev;
  private SimpleInferredEdge ie2_rev;
  private SimpleInferredEdge ie3_rev;
  private final double _numericError = 1e-4;
  private SimpleInferredEdge ie3;
  private SimpleInferredEdge ie2;
  private SimpleInferredEdge ie1;
  private Graph graph;
  private OtpGraph otpGraph;
  private InferredEdge ie1_rev;

  private SimpleInferredPath makeTmpPath(boolean isBackward,
    Coordinate... coords) {
    return TrackingTestUtils.makeTmpPath(this.otpGraph,
        isBackward, coords);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBadPathError() {
    final List<SimplePathEdge> edges2_rev =
        Lists.newArrayList(SimplePathEdge.getEdge(ie2_rev, 0,
            false), SimplePathEdge.getEdge(ie3_rev,
            ie2_rev.getLength(), false));

    SimpleInferredPath.getInferredPath(edges2_rev, false);

  }

  @Test
  public void testDistanceBetween1() {
    /*
     * States on the same path.
     */
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(7d, 2d));
    final Vector diff = y.minus(x);
    assertEquals("distance", 2d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", 1d, diff.getElement(1),
        _numericError);

    /*
     * Check for symmetry
     */
    final Vector diff2 = x.minus(y);
    assertEquals("rev diff", diff.scale(-1), diff2);
  }

  @Ignore
  @Test
  public void testDistanceBetween10() {
    /* FIXME!
     * Test double overlaps: overlaps on the first
     * edge, then doesn't overlap, then overlaps 
     * at the end.
     */

    final SimpleInferredPath path1 =
        makeTmpPath(false, new Coordinate(0, 10),
            new Coordinate(0, 0), new Coordinate(10, 0),
            new Coordinate(10, 10), new Coordinate(20, 10));

    final SimpleInferredPath path2 =
        makeTmpPath(false, new Coordinate(20, 10),
            new Coordinate(10, 10), new Coordinate(0, 10),
            new Coordinate(0, 0));

    final SimplePathState x =
        SimplePathState.getPathState(path1, VectorFactory
            .getDenseDefault().createVector2D(35d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(path2, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", 0d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", 2d, diff.getElement(1),
        _numericError);

    final Vector diff2 = x.minus(y);
    assertEquals("rev distance", 50d, diff2.getElement(0),
        _numericError);
    assertEquals("rev velocity", 0d, diff2.getElement(1),
        _numericError);

    final SimplePathState x2 =
        SimplePathState.getPathState(path1, VectorFactory
            .getDenseDefault().createVector2D(22.5d, 1d));
    final SimplePathState y2 =
        SimplePathState.getPathState(path2, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final Vector diff3 = x2.minus(y2);
    assertEquals("distance", 32.5d, diff3.getElement(0),
        _numericError);
    assertEquals("velocity", 0d, diff3.getElement(1),
        _numericError);
  }

  @Ignore
  @Test
  public void testDistanceBetween11() {
    /*
     * FIXME!
     */

    final SimpleInferredPath path1 =
        makeTmpPath(false, new Coordinate(0, 0),
            new Coordinate(0, 10));

    final SimpleInferredPath path2 =
        makeTmpPath(false, new Coordinate(0, 10),
            new Coordinate(0, 0), new Coordinate(-10, 0));

    final SimplePathState x =
        SimplePathState.getPathState(path2, VectorFactory
            .getDenseDefault().createVector2D(15d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(path1, VectorFactory
            .getDenseDefault().createVector2D(8.5d, 1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", -13.5d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", -2d, diff.getElement(1),
        _numericError);

    final Vector diff2 = x.minus(y);
    assertEquals("rev distance", -13.5d,
        diff2.getElement(0), _numericError);
    assertEquals("rev velocity", -2d, diff2.getElement(1),
        _numericError);
  }

  @Test
  public void testDistanceBetween12() {
    final SimpleInferredPath startPath =
        makeTmpPath(false, new Coordinate(0, 0),
            new Coordinate(0, 60));

    final SimplePathState startState =
        SimplePathState.getPathState(startPath, VectorFactory
            .getDefault().createVector2D(12d, -1));

    final SimpleInferredPath newPath =
        makeTmpPath(true, new Coordinate(0, 60),
            new Coordinate(0, 0));

    final Vector result =
        newPath.getStateOnPath(startState).getRawState();

    final SimplePathState stateOnNewPath =
        SimplePathState.getPathState(newPath, result);
    final Vector difference =
        stateOnNewPath.minus(startState);

    assertTrue(difference.isZero(1e-7));
  }

  @Test
  public void testDistanceBetween13() {
    final SimpleInferredPath startPath =
        makeTmpPath(false, new Coordinate(0, 0),
            new Coordinate(0, 60), new Coordinate(0, 199));

    final SimplePathState startState =
        SimplePathState.getPathState(startPath, VectorFactory
            .getDefault().createVector2D(104d, -1));

    final SimpleInferredPath newPath =
        makeTmpPath(false, new Coordinate(0, 199),
            new Coordinate(0, 60));

    final Vector result =
        newPath.getStateOnPath(startState).getRawState();

    final SimplePathState stateOnNewPath =
        SimplePathState.getPathState(newPath, result);
    final Vector difference =
        stateOnNewPath.minus(startState);

    assertTrue(difference.isZero(1e-7));
  }

  @Test
  public void testDistanceBetween14() {
    final SimpleInferredPath startPath =
        makeTmpPath(true, new Coordinate(0, 199),
            new Coordinate(0, 60), new Coordinate(0, 0));

    final SimplePathState startState =
        SimplePathState.getPathState(startPath, VectorFactory
            .getDefault().createVector2D(-104d, -2.79));

    final SimpleInferredPath newPath =
        makeTmpPath(true, new Coordinate(0, 60),
            new Coordinate(0, 199));

    final Vector result =
        newPath.getStateOnPath(startState).getRawState();

    final SimplePathState stateOnNewPath =
        SimplePathState.getPathState(newPath, result);
    final Vector difference =
        stateOnNewPath.minus(startState);

    assertTrue(difference.isZero(1e-7));
  }

  @Test
  public void testDistanceBetween15() {
    final SimpleInferredPath startPath =
        makeTmpPath(true, new Coordinate(0,
            178.89095954422947), new Coordinate(0, 0),
            new Coordinate(0, 139.9919743536118d));

    final SimplePathState startState =
        SimplePathState.getPathState(
            startPath,
            VectorFactory.getDefault().createVector2D(
                -139.9919743536118d, -3.39));

    final SimpleInferredPath newPath =
        makeTmpPath(false, new Coordinate(0, 0),
            new Coordinate(0, 178.89095954422947));

    final Vector result =
        newPath.getStateOnPath(startState).getRawState();

    final SimplePathState stateOnNewPath =
        SimplePathState.getPathState(newPath, result);
    final Vector difference =
        stateOnNewPath.minus(startState);

    assertTrue(difference.isZero(1e-7));
  }

  @Test
  public void testDistanceBetween16() {
    final SimpleInferredPath otherPath =
        makeTmpPath(false, new Coordinate(0d, 0d),
            new Coordinate(-85d, 0d), new Coordinate(
                -150.37d, 0d));

    final SimplePathState otherState =
        SimplePathState.getPathState(otherPath, VectorFactory
            .getDefault().createVector2D(121.15d, 2.59d));

    final SimpleInferredPath thisPath =
        makeTmpPath(true, new Coordinate(0d, 0d),
            new Coordinate(-85d, 0d), new Coordinate(
                -150.37d, 0d));
    final SimplePathState thisState =
        SimplePathState
            .getPathState(
                thisPath,
                VectorFactory.getDefault().createVector2D(
                    -150.37d, -12.06d));

    final Vector difference = thisState.minus(otherState);

    assertEquals("dist", -121d, difference.getElement(0),
        1d);
    assertEquals("vel", -14.65d, difference.getElement(1),
        1d);
  }

  @Test
  public void testDistanceBetween17() {
    final SimpleInferredPath startPath =
        makeTmpPath(true, new Coordinate(0, -262.21d),
            new Coordinate(0, 0),
            new Coordinate(0, 792.85d));

    final SimplePathState startState =
        SimplePathState.getPathState(
            startPath,
            VectorFactory.getDefault().createVector2D(
                -808.4862233549034, -26.2880263830998));

    final SimpleInferredPath newPath =
        makeTmpPath(true, new Coordinate(0, 792.85d),
            new Coordinate(0, 0), new Coordinate(0,
                -262.21d));

    final Vector result =
        newPath.getStateOnPath(startState).getRawState();

    final SimplePathState stateOnNewPath =
        SimplePathState.getPathState(newPath, result);
    final Vector difference =
        stateOnNewPath.minus(startState);

    assertTrue(difference.isZero(1e-7));
  }

  @Test
  public void testDistanceBetween18() {
    final SimpleInferredPath otherPath =
        makeTmpPath(true, new Coordinate(-90d, 0d),
            new Coordinate(0d, 0d), new Coordinate(0d,
                -(517d - 90d) / 2d),
            new Coordinate(0d, 0d),
            new Coordinate(-90d, 0d));

    final SimplePathState otherState =
        SimplePathState.getPathState(otherPath, VectorFactory
            .getDefault().createVector2D(-564d, -8d));

    final SimpleInferredPath thisPath =
        makeTmpPath(false, new Coordinate(0d, 0d),
            new Coordinate(-90d, 0d), new Coordinate(-90d,
                607d - 90d));
    final SimplePathState thisState =
        SimplePathState.getPathState(thisPath, VectorFactory
            .getDefault().createVector2D(47, 8d));

    final Vector difference = thisState.minus(otherState);

    assertEquals("dist", 0d, difference.getElement(0), 0d);
    assertEquals("vel", 0d, difference.getElement(1), 0d);
  }

  @Test
  public void testDistanceBetween19() {
    /*
     * This path ends at its start (a loop).
     */
    final SimpleInferredPath otherPath =
        makeTmpPath(true, new Coordinate(-90d, 0d),
            new Coordinate(0d, 0d), new Coordinate(0d,
                -(517d - 90d) / 2d),
            new Coordinate(0d, 0d),
            new Coordinate(-90d, 0d));

    final SimplePathState otherState =
        SimplePathState.getPathState(otherPath, VectorFactory
            .getDefault().createVector2D(-564d, -8d));

    /*
     * This path starts at the same start as
     * the other. 
     */
    final SimpleInferredPath thisPath =
        makeTmpPath(true, new Coordinate(0d, 607d - 90d),
            new Coordinate(0d, 0d),
            new Coordinate(-90d, 0d));

    final Vector result =
        thisPath.getStateOnPath(otherState).getRawState();

    final SimplePathState stateOnThis =
        SimplePathState.getPathState(thisPath, result);
    final Vector difference = stateOnThis.minus(otherState);

    assertTrue(difference.isZero(1e-7));
  }

  @Test
  public void testDistanceBetween2() {
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(
            p1_neg,
            VectorFactory.getDenseDefault().createVector2D(
                p1_neg.getTotalPathDistance() + 7d, -2d));
    final Vector diff2 = x.minus(y);
    assertEquals("distance", -2d, diff2.getElement(0),
        _numericError);
    assertEquals("velocity", 3d, diff2.getElement(1),
        _numericError);
  }

  @Test
  public void testDistanceBetween20() {
    final SimpleInferredPath otherPath =
        makeTmpPath(true, new Coordinate(0d, 0d),
            new Coordinate(0d, 179d), new Coordinate(10d,
                179d), new Coordinate(56d, 179d));

    final SimplePathState otherState =
        SimplePathState.getPathState(otherPath, VectorFactory
            .getDefault().createVector2D(-41d, 4d));

    final SimpleInferredPath thisPath =
        makeTmpPath(false, new Coordinate(0d, 0d),
            new Coordinate(0d, 179d));
    final SimplePathState thisState =
        SimplePathState.getPathState(thisPath, VectorFactory
            .getDefault().createVector2D(157d, 7.5d));

    final Vector difference = thisState.minus(otherState);

    assertEquals("dist", -37d, difference.getElement(0), 0d);
    assertEquals("vel", 3.5d, difference.getElement(1), 0d);
  }

  @Test
  public void testDistanceBetween21() {
    final SimpleInferredPath otherPath =
        makeTmpPath(false, new Coordinate(0d, 0d),
            new Coordinate(100d, 0d));

    final SimplePathState otherState =
        SimplePathState.getPathState(otherPath, VectorFactory
            .getDefault().createVector2D(49d, 1.6d));

    final SimpleInferredPath thisPath =
        makeTmpPath(false, new Coordinate(0d, 0d),
            new Coordinate(100d, 0d), new Coordinate(100d,
                100d), new Coordinate(100d, 0d),
            new Coordinate(0d, 0d));
    final SimplePathState thisState =
        SimplePathState.getPathState(thisPath, VectorFactory
            .getDefault().createVector2D(0d, 0d));

    final Vector difference = thisState.minus(otherState);

    assertEquals("dist", -49d, difference.getElement(0), 0d);
    assertEquals("vel", -1.6d, difference.getElement(1), 0d);
  }

  @Test
  public void testDistanceBetween22() {
    final SimpleInferredPath otherPath =
        makeTmpPath(false, new Coordinate(0d, 0d),
            new Coordinate(100d, 0d), new Coordinate(100d,
                100d), new Coordinate(100d, 0d),
            new Coordinate(0d, 0d));

    final SimplePathState otherState =
        SimplePathState.getPathState(otherPath, VectorFactory
            .getDefault().createVector2D(49d, 1.6d));

    final SimpleInferredPath thisPath =
        makeTmpPath(false, new Coordinate(0d, 0d),
            new Coordinate(100d, 0d));
    final SimplePathState thisState =
        SimplePathState.getPathState(thisPath, VectorFactory
            .getDefault().createVector2D(0d, 0d));

    final Vector difference = thisState.minus(otherState);

    assertEquals("dist", -49d, difference.getElement(0), 0d);
    assertEquals("vel", -1.6d, difference.getElement(1), 0d);
  }

  @Ignore
  @Test(expected = IllegalStateException.class)
  public void testDistanceBetween2exp() {
    /*
     * TODO: remove or make relevant again.
     * Same paths, different directions.
     */
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(
            p1_neg,
            VectorFactory.getDenseDefault().createVector2D(
                p1_neg.getTotalPathDistance() + 7d, -2d));
    y.minus(x);
  }

  @Test
  public void testDistanceBetween3() {
    /*
     * Both paths are in the same direction, overlapping.
     */
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(12.5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(p2, VectorFactory
            .getDenseDefault().createVector2D(17.5d, -1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", 15d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", -2d, diff.getElement(1),
        _numericError);
  }

  @Test
  public void testDistanceBetween4() {
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(12.5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(TrackingTestUtils
            .makeTmpPath(this.otpGraph, true,
                new Coordinate(0, 40),
                new Coordinate(0, 30),
                new Coordinate(0, 20)), VectorFactory
            .getDenseDefault().createVector2D(-17.5d, -1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", -15d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", 0d, diff.getElement(1),
        _numericError);
  }

  @Test
  public void testDistanceBetween4_2() {
    final SimplePathState x2 =
        SimplePathState.getPathState(TrackingTestUtils
            .makeTmpPath(this.otpGraph, false,
                new Coordinate(0, 30),
                new Coordinate(0, 20),
                new Coordinate(0, 10)), VectorFactory
            .getDenseDefault().createVector2D(12.5d, 1d));
    final SimplePathState y2 =
        SimplePathState.getPathState(p2, VectorFactory
            .getDenseDefault().createVector2D(17.5d, 1d));
    final Vector diff2 = y2.minus(x2);
    assertEquals("distance", 20d, diff2.getElement(0),
        _numericError);
    assertEquals("velocity", 2d, diff2.getElement(1),
        _numericError);
  }

  @Test
  public void testDistanceBetween4_3() {
    final SimplePathState x2 =
        SimplePathState.getPathState(TrackingTestUtils
            .makeTmpPath(this.otpGraph, false,
                new Coordinate(0, 30),
                new Coordinate(0, 20),
                new Coordinate(0, 10)), VectorFactory
            .getDenseDefault().createVector2D(12.5d, 1d));
    final SimplePathState y2 =
        SimplePathState.getPathState(TrackingTestUtils
            .makeTmpPath(this.otpGraph, true,
                new Coordinate(0, 40),
                new Coordinate(0, 30),
                new Coordinate(0, 20)), VectorFactory
            .getDenseDefault().createVector2D(-17.5d, 1d));
    final Vector diff2 = y2.minus(x2);
    assertEquals("distance", -20d, diff2.getElement(0),
        _numericError);
    assertEquals("velocity", 0d, diff2.getElement(1),
        _numericError);
  }

  @Ignore
  @Test
  public void testDistanceBetween5() {
    /*
     * FIXME!
     * Both paths are in the same direction, 
     * path2 is the second edge in path1.
     */
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(
            SimpleInferredPath.getInferredPath(SimplePathEdge.getEdge(
                p1.getPathEdges().get(1).getInferredEdge(), 0d,
                false)),
            VectorFactory.getDenseDefault().createVector2D(
                5d, -1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", 10d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", -2d, diff.getElement(1),
        _numericError);

    /*
     * Check for symmetry
     */
    final Vector diff2 = x.minus(y);
    assertTrue("rev diff", diff2.scale(-1).equals(diff));
  }

  @Ignore
  @Test
  public void testDistanceBetween6() {
    /*
     * FIXME!
     * Path2 is the second edge in path1, but
     * opposite direction.
     */
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(
            SimpleInferredPath.getInferredPath(SimplePathEdge.getEdge(
                p1.getPathEdges().get(1).getInferredEdge(), 0d,
                true)),
            VectorFactory.getDenseDefault().createVector2D(
                0d, -1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", -15d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", 0d, diff.getElement(1),
        _numericError);

    /*
     * Check for symmetry
     */
    final Vector diff2 = x.minus(y);
    assertEquals("rev distance", -15d, diff2.getElement(0),
        _numericError);
    assertEquals("rev velocity", 0d, diff2.getElement(1),
        _numericError);
  }

  /**
   * Deferring these non-overlapping tests until we decide to implement this.
   */
  @Ignore
  @Test
  public void testDistanceBetween7() {
    /*
     * Test that edges touching the start or end, same direction, 
     * but not overlapping, are correctly assessed.
     */

    /*
     * Touches end
     */
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(SimpleInferredPath
            .getInferredPath(SimplePathEdge.getEdge(ie3, 0d,
                false)), VectorFactory.getDenseDefault()
            .createVector2D(5d, 1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", 20d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", 0d, diff.getElement(1),
        _numericError);

    /*
     * Check for symmetry, and that 
     * when something touches the beginning it works
     */
    final Vector diff2 = x.minus(y);
    assertTrue("rev diff", diff2.scale(-1).equals(diff));
  }

  /**
   * Deferring these non-overlapping tests until we decide to implement this.
   */
  @Ignore
  @Test
  public void testDistanceBetween8() {
    /*
     * Test that edges touching the start or end, but
     * not overlapping, are correctly assessed, when
     * the paths are opposite directions.
     * 
     * Again, due to the opposite directions, we
     * get the same answer in reverse.
     * (Now commutative)
     */

    /*
     * Touches end
     */
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(SimpleInferredPath
            .getInferredPath(SimplePathEdge.getEdge(ie3_rev, 0d,
                false)), VectorFactory.getDenseDefault()
            .createVector2D(5d, -1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", -20d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", 0d, diff.getElement(1),
        _numericError);

    /*
     * Check for symmetry, and that 
     * when something touches the beginning it works
     */
    final Vector diff2 = x.minus(y);
    assertEquals("rev distance", -20d, diff2.getElement(0),
        _numericError);
    assertEquals("rev velocity", 0d, diff2.getElement(1),
        _numericError);
  }

  /**
   * Deferring this test until we implement the functionality.
   */
  @Ignore
  @Test
  public void testDistanceBetween9() {
    /*
     * Test large overlaps (one edge
     * covers two small edges), 
     * and opposite directions.
     */

    final List<Coordinate> p1RevGeomCoords =
        Lists.newArrayList(p1.getGeometry().reverse()
            .getCoordinates());
    final SimpleInferredPath pOther =
        makeTmpPath(false,
            Iterables.getFirst(p1RevGeomCoords, null),
            Iterables.getLast(p1RevGeomCoords, null));
    final SimplePathState x =
        SimplePathState.getPathState(p1, VectorFactory
            .getDenseDefault().createVector2D(5d, 1d));
    final SimplePathState y =
        SimplePathState.getPathState(pOther, VectorFactory
            .getDenseDefault().createVector2D(5d, -1d));
    final Vector diff = y.minus(x);
    assertEquals("distance", -10d, diff.getElement(0),
        _numericError);
    assertEquals("velocity", 0d, diff.getElement(1),
        _numericError);

    final Vector diff2 = x.minus(y);
    assertEquals("rev distance", -10d, diff2.getElement(0),
        _numericError);
    assertEquals("rev velocity", 0d, diff2.getElement(1),
        _numericError);
  }

  @Test
  public void testNonSimplePathCombine1() {

    /* 
     *         +--+->  path 1 
     *         |  |  
     *  path 2 v  |
     *         ----
     */
    final InferredPath path1 =
        makeTmpPath(false, new Coordinate(0, 10),
            new Coordinate(0, 0), new Coordinate(10, 0),
            new Coordinate(10, 10), new Coordinate(20, 10));

    final InferredPath path2 =
        makeTmpPath(false, new Coordinate(20, 10),
            new Coordinate(10, 10), new Coordinate(0, 10),
            new Coordinate(0, 0));

    final Geometry from = path1.getGeometry();
    final Geometry to = path2.getGeometry();

    final PathMergeResults res1 =
        AbstractPathState.mergePaths(from, 35d, to, 35d);

    final Geometry expRes1 =
        JTSFactoryFinder.getGeometryFactory()
            .createLineString(
                new Coordinate[] { new Coordinate(0, 10),
                    new Coordinate(0, 0),
                    new Coordinate(10, 0),
                    new Coordinate(10, 10),
                    new Coordinate(20, 10),
                    new Coordinate(10, 10),
                    new Coordinate(0, 10),
                    new Coordinate(0, 0) });

    assertTrue(res1.getPath().equalsExact(expRes1));
    assertTrue(!res1.isToIsReversed());

    final PathMergeResults res2 =
        AbstractPathState.mergePaths(from, 35d,
            to.reverse(), 5d);

    assertTrue(res2.getPath().equalsExact(expRes1));
    assertTrue(res2.isToIsReversed());

    final PathMergeResults res3 =
        AbstractPathState.mergePaths(from, 2.5d, to, 5d);

    final Geometry expRes3 =
        JTSFactoryFinder.getGeometryFactory()
            .createLineString(
                new Coordinate[] { new Coordinate(0, 10),
                    new Coordinate(0, 0),
                    new Coordinate(10, 0),
                    new Coordinate(10, 10),
                    new Coordinate(20, 10), });

    assertTrue(res3.getPath().equalsExact(expRes3));
    assertTrue(res3.isToIsReversed());

    final InferredPath path11 =
        makeTmpPath(false, new Coordinate(10, 10),
            new Coordinate(20, 10), new Coordinate(20, 20));

    final PathMergeResults res4 =
        AbstractPathState.mergePaths(path11.getGeometry(),
            17.5d, to, 2.5d);

    final Geometry expRes2 =
        JTSFactoryFinder.getGeometryFactory()
            .createLineString(
                new Coordinate[] {
                    //               new Coordinate(0, 0),
                    //               new Coordinate(0, 10),
                    new Coordinate(10, 10),
                    new Coordinate(20, 10),
                    new Coordinate(20, 20) });

    assertTrue(res4.getPath().equalsExact(expRes2));
    assertTrue(res4.isToIsReversed());

    final PathMergeResults res5 =
        AbstractPathState.mergePaths(path11.getGeometry(),
            7.5d, to, 2.5d);

    final Geometry expRes5 =
        JTSFactoryFinder.getGeometryFactory()
            .createLineString(
                new Coordinate[] { new Coordinate(10, 10),
                    new Coordinate(20, 10),
                    new Coordinate(20, 20)
                //               new Coordinate(10, 10),
                //               new Coordinate(0, 10),
                //               new Coordinate(0, 0)
                });

    assertTrue(res5.getPath().equalsExact(expRes5));
    assertTrue(res5.isToIsReversed());

    final Geometry p12 =
        JTSFactoryFinder.getGeometryFactory()
            .createLineString(
                new Coordinate[] { new Coordinate(10, 10),
                    new Coordinate(20, 10),
                    new Coordinate(10, 10),
                    new Coordinate(0, 10),
                    new Coordinate(0, 0) });
    final PathMergeResults res6 =
        AbstractPathState
            .mergePaths(p12, 37.5d, p12, 32.5d);

    assertTrue(res6.getPath().equalsExact(p12));
    assertTrue(!res6.isToIsReversed());

    final PathMergeResults res7 =
        AbstractPathState.mergePaths(p12, 37.5d,
            p12.reverse(), 32.5d);

    assertTrue(res7.getPath().equalsExact(p12));
    assertTrue(res7.isToIsReversed());
  }

  @Before
  public void testSetup() {
    graph = new Graph();
    /*
     * Create three stacked edges facing up the y-axis, spaced
     * by 10 meters.
     */
    final StreetVertex v0 =
        new IntersectionVertex(graph, "v0", 0, 10);
    final StreetVertex v1 =
        new IntersectionVertex(graph, "v1", 0, 20);
    final StreetVertex v2 =
        new IntersectionVertex(graph, "v2", 0, 30);
    final StreetVertex v3 =
        new IntersectionVertex(graph, "v3", 0, 40);

    final Edge e1 =
        new PlainStreetEdge(v0, v1,
            TrackingTestUtils.makeGeometry(v0, v1), "e1",
            10d, StreetTraversalPermission.ALL, false);
    final Edge e2 =
        new PlainStreetEdge(v1, v2,
            TrackingTestUtils.makeGeometry(v1, v2), "e2",
            10d, StreetTraversalPermission.ALL, false);
    final Edge e3 =
        new PlainStreetEdge(v2, v3,
            TrackingTestUtils.makeGeometry(v2, v3), "e3",
            10d, StreetTraversalPermission.ALL, false);

    final Edge e1_rev =
        new PlainStreetEdge(v1, v0,
            TrackingTestUtils.makeGeometry(v1, v0), "-e1",
            10d, StreetTraversalPermission.ALL, false);
    final Edge e2_rev =
        new PlainStreetEdge(v2, v1,
            TrackingTestUtils.makeGeometry(v2, v1), "-e2",
            10d, StreetTraversalPermission.ALL, false);
    final Edge e3_rev =
        new PlainStreetEdge(v3, v2,
            TrackingTestUtils.makeGeometry(v3, v2), "-e3",
            10d, StreetTraversalPermission.ALL, false);

    otpGraph = mock(OtpGraph.class);

    ie1 = SimpleInferredEdge.getInferredEdge(e1.getGeometry(), e1, 1, otpGraph);
    ie2 = SimpleInferredEdge.getInferredEdge(e2.getGeometry(), e2, 2, otpGraph);
    ie3 = SimpleInferredEdge.getInferredEdge(e3.getGeometry(), e3, 3, otpGraph);
    ie1_rev = SimpleInferredEdge.getInferredEdge(e1_rev.getGeometry(), e1_rev, -1, otpGraph);
    ie2_rev = SimpleInferredEdge.getInferredEdge(e2_rev.getGeometry(), e2_rev, -2, otpGraph);
    ie3_rev = SimpleInferredEdge.getInferredEdge(e3_rev.getGeometry(), e3_rev, -3, otpGraph);

    final List<SimplePathEdge> edges1 =
        Lists.newArrayList(SimplePathEdge.getEdge(ie1, 0, false),
            SimplePathEdge.getEdge(ie2, ie1.getLength(), false));

    p1 = SimpleInferredPath.getInferredPath(edges1, false);

    final List<SimplePathEdge> edges2 =
        Lists.newArrayList(SimplePathEdge.getEdge(ie2, 0, false),
            SimplePathEdge.getEdge(ie3, ie2.getLength(), false));

    p2 = SimpleInferredPath.getInferredPath(edges2, false);

    final List<SimplePathEdge> edges1_neg =
        Lists.newArrayList(SimplePathEdge.getEdge(ie2, 0, true),
            SimplePathEdge.getEdge(ie1, -ie1.getLength(), true));

    p1_neg = SimpleInferredPath.getInferredPath(edges1_neg, true);

    final List<SimplePathEdge> edges2_neg =
        Lists.newArrayList(SimplePathEdge.getEdge(ie3, 0, true),
            SimplePathEdge.getEdge(ie2, -ie2.getLength(), true));

    p2_neg = SimpleInferredPath.getInferredPath(edges2_neg, true);

    final List<SimplePathEdge> edges2_rev =
        Lists.newArrayList(SimplePathEdge.getEdge(ie3_rev, 0,
            false), SimplePathEdge.getEdge(ie2_rev,
            ie2_rev.getLength(), false));

    p2_rev =
        SimpleInferredPath.getInferredPath(edges2_rev, false);

  }
}
