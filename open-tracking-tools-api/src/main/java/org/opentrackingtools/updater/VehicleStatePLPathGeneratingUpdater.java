package org.opentrackingtools.updater;

import gov.sandia.cognition.math.LogMath;
import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.mtj.DenseMatrix;
import gov.sandia.cognition.statistics.DataDistribution;
import gov.sandia.cognition.statistics.bayesian.BayesianCredibleInterval;
import gov.sandia.cognition.statistics.bayesian.ParticleFilter;
import gov.sandia.cognition.statistics.distribution.MultivariateGaussian;
import gov.sandia.cognition.statistics.distribution.UnivariateGaussian;
import gov.sandia.cognition.statistics.method.ConfidenceInterval;
import gov.sandia.cognition.util.AbstractCloneableSerializable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import org.opentrackingtools.VehicleStateInitialParameters;
import org.opentrackingtools.distributions.CountedDataDistribution;
import org.opentrackingtools.distributions.PathStateDistribution;
import org.opentrackingtools.distributions.PathStateMixtureDensityModel;
import org.opentrackingtools.distributions.TruncatedRoadGaussian;
import org.opentrackingtools.estimators.MotionStateEstimatorPredictor;
import org.opentrackingtools.estimators.PathStateEstimatorPredictor;
import org.opentrackingtools.graph.InferenceGraph;
import org.opentrackingtools.graph.InferenceGraphEdge;
import org.opentrackingtools.graph.InferenceGraphSegment;
import org.opentrackingtools.model.GpsObservation;
import org.opentrackingtools.model.SimpleBayesianParameter;
import org.opentrackingtools.model.VehicleStateDistribution;
import org.opentrackingtools.model.VehicleStateDistribution.VehicleStateDistributionFactory;
import org.opentrackingtools.paths.Path;
import org.opentrackingtools.paths.PathEdge;
import org.opentrackingtools.util.PathEdgeNode;
import org.opentrackingtools.util.PathUtils;
import org.opentrackingtools.util.StatisticsUtil;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Queues;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.vividsolutions.jts.geom.Coordinate;

public class VehicleStatePLPathGeneratingUpdater<O extends GpsObservation, G extends InferenceGraph>
    extends AbstractCloneableSerializable implements
    ParticleFilter.Updater<O, VehicleStateDistribution<O>> {

  public static double MAX_DISTANCE_SPEED = 53.6448; // ~120 mph

  /*
   * Maximum radius we're willing to search around a given
   * observation when snapping (for path search destination edges)
   */
  public static double MAX_OBS_SNAP_RADIUS = 200d;
  public static double MIN_OBS_SNAP_RADIUS = 10d;

  /*
   * Maximum radius we're willing to search around a given
   * state when snapping (for path search off -> on-road edges)
   */
  public static double MAX_STATE_SNAP_RADIUS = 350d;

  private static final long serialVersionUID = 7567157323292175525L;

  protected G inferenceGraph;

  protected O initialObservation;

  protected VehicleStateInitialParameters parameters;

  protected Random random;

  public long seed;

  protected VehicleStateDistributionFactory<O, G> vehicleStateFactory;

  public VehicleStatePLPathGeneratingUpdater(O obs, G inferencedGraph, VehicleStateDistributionFactory<O,G> vehicleStateFactory,
    VehicleStateInitialParameters parameters, Random rng) {
    this.vehicleStateFactory = vehicleStateFactory;
    this.initialObservation = obs;
    this.inferenceGraph = inferencedGraph;
    if (rng == null) {
      this.random = new Random();
    } else {
      this.random = rng;
    }
    this.parameters = parameters;

  }

  @Override
  public VehicleStatePLPathGeneratingUpdater<O, G> clone() {
    final VehicleStatePLPathGeneratingUpdater<O, G> clone =
        (VehicleStatePLPathGeneratingUpdater<O, G>) super.clone();
    clone.seed = this.seed;
    clone.inferenceGraph = this.inferenceGraph;
    clone.initialObservation = this.initialObservation;
    clone.parameters = this.parameters;
    clone.random = this.random;
    return clone;
  }

  @Override
  public double computeLogLikelihood(VehicleStateDistribution<O> particle,
    O observation) {
    double logLikelihood = 0d;
    logLikelihood +=
        particle.getMotionStateParam().getConditionalDistribution()
            .getProbabilityFunction()
            .logEvaluate(observation.getProjectedPoint());
    return logLikelihood;
  }

  /**
   * Create vehicle states from the nearby edges.
   */
  @Override
  public DataDistribution<VehicleStateDistribution<O>> createInitialParticles(
    int numParticles) {
    final DataDistribution<VehicleStateDistribution<O>> retDist =
        new CountedDataDistribution<VehicleStateDistribution<O>>(true);

    /*
     * Start by creating an off-road vehicle state with which we can obtain the surrounding
     * edges.
     */
    final VehicleStateDistribution<O> nullState =
        vehicleStateFactory.createInitialVehicleState(
            this.parameters, this.inferenceGraph, 
            this.initialObservation, this.random,
            PathEdge.nullPathEdge);
    final MultivariateGaussian initialMotionStateDist =
        nullState.getMotionStateParam().getParameterPrior();
    final Collection<InferenceGraphSegment> edges =
        this.inferenceGraph.getNearbyEdges(initialMotionStateDist,
            initialMotionStateDist.getCovariance());

    for (int i = 0; i < numParticles; i++) {
      /*
       * From the surrounding edges, we create states on those edges.
       */
      final DataDistribution<VehicleStateDistribution<O>> statesOnEdgeDistribution =
          new CountedDataDistribution<VehicleStateDistribution<O>>(true);
      
      final double nullLogLikelihood =
          nullState.getEdgeTransitionParam()
              .getConditionalDistribution()
              .getProbabilityFunction().logEvaluate(InferenceGraphEdge.nullGraphEdge)
              + this.computeLogLikelihood(nullState,
                  this.initialObservation);

      statesOnEdgeDistribution
          .increment(nullState, nullLogLikelihood);

      for (final InferenceGraphSegment segment : edges) {
        
        final PathEdge pathEdge = new PathEdge(segment, 0d, false);
        
        VehicleStateDistribution<O> stateOnEdge = vehicleStateFactory.createInitialVehicleState(
            parameters, inferenceGraph, initialObservation, random, pathEdge);

        final double logLikelihood =
            stateOnEdge.getEdgeTransitionParam()
                .getConditionalDistribution()
                .getProbabilityFunction().logEvaluate(pathEdge.getInferenceGraphEdge())
                + this.computeLogLikelihood(stateOnEdge,
                    this.initialObservation);

        statesOnEdgeDistribution
            .increment(stateOnEdge, logLikelihood);
      }

      retDist.increment(statesOnEdgeDistribution.sample(this.random));
    }

    Preconditions.checkState(retDist.getDomainSize() > 0);
    return retDist;
  }

  public InferenceGraph getInferredGraph() {
    return this.inferenceGraph;
  }

  public GpsObservation getInitialObservation() {
    return this.initialObservation;
  }

  public VehicleStateInitialParameters getParameters() {
    return this.parameters;
  }

  public Random getRandom() {
    return this.random;
  }

  public long getSeed() {
    return this.seed;
  }

  public void setRandom(Random random) {
    this.random = random;
  }

  public void setSeed(long seed) {
    this.seed = seed;
  }

  @Override
  public VehicleStateDistribution<O> update(VehicleStateDistribution<O> state) {
    final VehicleStateDistribution<O> predictedState = state.clone();
    
    final List<PathStateDistribution> distributions =
        Lists.newArrayList();
    int numberOfNonZeroPaths = 0;
    int numberOfOffRoadPaths = 0;
    List<Double> weights = Lists.newArrayList();
    /*
     * Predict/project the motion state forward.
     */
    final MotionStateEstimatorPredictor motionStateEstimatorPredictor =
        new MotionStateEstimatorPredictor(state, this.random, this.parameters.getInitialObsFreq());

    predictedState
        .setMotionStateEstimatorPredictor(motionStateEstimatorPredictor);

    final GpsObservation obs = state.getObservation();
    final PathStateDistribution priorPathStateDist = predictedState.getPathStateParam().getParameterPrior();
    
    double onRoadPathsTotalLogLikelihood = Double.NEGATIVE_INFINITY;
      
    if (priorPathStateDist.getPathState().isOnRoad()) {
      
      /*
       * We always consider going off road, so handle it first.
       */
      final MultivariateGaussian offRoadPriorMotionState = priorPathStateDist.getMotionDistribution().clone();
      PathUtils.convertToGroundBelief(offRoadPriorMotionState, priorPathStateDist.getPathState().getEdge(), 
          true, false, true);
      final MultivariateGaussian offRoadPriorPredictiveMotionState = motionStateEstimatorPredictor
              .createPredictiveDistribution(offRoadPriorMotionState);
      final PathStateDistribution offRoadPathStateDist = new PathStateDistribution(Path.nullPath, 
          offRoadPriorPredictiveMotionState);
      MultivariateGaussian offRoadEdgeObsDist = motionStateEstimatorPredictor.getObservationDistribution(
          offRoadPriorPredictiveMotionState, PathEdge.nullPathEdge);
      final double offRoadObsLogLikelihood = offRoadEdgeObsDist.getProbabilityFunction().logEvaluate(
          state.getObservation().getProjectedPoint());
      final double offRoadTransitionLogLikelihood =
          predictedState
              .getEdgeTransitionParam()
              .getConditionalDistribution()
              .getProbabilityFunction()
              .logEvaluate(
                  InferenceGraphEdge.nullGraphEdge);
      distributions.add(offRoadPathStateDist);
      weights.add(offRoadObsLogLikelihood + offRoadTransitionLogLikelihood);
      
      /*
       * Now, perform a search over the transferable edges.
       */
      final MultivariateGaussian onRoadPriorMotionState = 
          predictedState.getPathStateParam().getParameterPrior().getEdgeDistribution();
      final MultivariateGaussian onRoadPriorPredictiveMotionState = motionStateEstimatorPredictor
              .createPredictiveDistribution(onRoadPriorMotionState);
      
      Set<PathEdgeNode> closedPathEdgeSet = Sets.newHashSet();
      PriorityQueue<PathEdgeNode> openPathEdgeQueue = Queues.newPriorityQueue();
      
      PathEdgeNode currentPathEdgeNode = new PathEdgeNode(
         new PathEdge(priorPathStateDist.getPathState().getEdge().getSegment(), 0d, false), null);
      openPathEdgeQueue.add(currentPathEdgeNode);
      MultivariateGaussian initialEdgePathState = PathStateEstimatorPredictor.getPathEdgePredictive(
          onRoadPriorPredictiveMotionState, currentPathEdgeNode.getPathEdge(), 
          obs.getObsProjected(), onRoadPriorMotionState.getMean().getElement(0), this.parameters.getInitialObsFreq());
      MultivariateGaussian initialEdgeObsDist;
      final double initialObsLikelihood;
      final double initialEdgeLikelihood;
      if (initialEdgePathState != null) {
        initialEdgeObsDist = motionStateEstimatorPredictor.getObservationDistribution(
            initialEdgePathState, currentPathEdgeNode.getPathEdge());
        initialObsLikelihood = initialEdgeObsDist.getProbabilityFunction().logEvaluate(
          state.getObservation().getProjectedPoint());
        initialEdgeLikelihood = PathStateEstimatorPredictor.marginalPredictiveLogLikInternal(
          onRoadPriorPredictiveMotionState, currentPathEdgeNode.getPathEdge(), 
          obs.getObsProjected(), onRoadPriorMotionState.getMean().getElement(0), this.parameters.getInitialObsFreq());
      } else {
        initialEdgeObsDist = null;
        initialObsLikelihood = Double.NEGATIVE_INFINITY;
        initialEdgeLikelihood = Double.NEGATIVE_INFINITY;
      }
      final double initialTransitionLogLikelihood =
          predictedState
              .getEdgeTransitionParam()
              .getConditionalDistribution()
              .getProbabilityFunction()
              .logEvaluate(
                  currentPathEdgeNode.getPathEdge().getInferenceGraphEdge());
      
      currentPathEdgeNode.setTransitionLogLikelihood(initialTransitionLogLikelihood);
      currentPathEdgeNode.setObsLogLikelihood(initialObsLikelihood);
      currentPathEdgeNode.setEdgeLogLikelihood(initialEdgeLikelihood);
      currentPathEdgeNode.setEdgeDistribution(initialEdgePathState);
      currentPathEdgeNode.setEdgeObsDistribution(initialEdgeObsDist);
      
      /*
       * We're going to evaluate all paths up to some bayesian credible interval of
       * the distance component in the path state.
       */
      BayesianCredibleInterval bciDist = BayesianCredibleInterval.compute(
          new UnivariateGaussian(onRoadPriorPredictiveMotionState.getMean().getElement(0),
              onRoadPriorPredictiveMotionState.getCovariance().getElement(0, 0)), 
              0.90d);
      Range<Double> bciRangeDist = Ranges.closed(bciDist.getLowerBound(), bciDist.getUpperBound());
      
      final double obsErrorMagnitude = 3d * Math.sqrt(predictedState.getObservationCovarianceParam().getValue().normFrobenius());
      
      while (!openPathEdgeQueue.isEmpty()) {
        currentPathEdgeNode = openPathEdgeQueue.poll();
        
        MultivariateGaussian currentObsDist = currentPathEdgeNode.getEdgeObsDistribution();
        final Range<Double> currentEdgeRange = Ranges.closed(
            currentPathEdgeNode.getPathEdge().getDistToStartOfEdge(), 
            currentPathEdgeNode.getPathEdge().getDistToStartOfEdge() + currentPathEdgeNode.getPathEdge().getLength());
        final boolean isWithinBci = currentObsDist == null ? false :
            bciRangeDist.isConnected(currentEdgeRange);
        final double distFromObs = currentObsDist == null ? Double.POSITIVE_INFINITY :
            currentObsDist.getMean().minus(obs.getProjectedPoint()).norm2();
        if (isWithinBci || distFromObs <= obsErrorMagnitude) {
          final PathStateDistribution pathStateDist = new PathStateDistribution(
              currentPathEdgeNode.getPath(), currentPathEdgeNode.getEdgeDistribution());
          distributions.add(pathStateDist);
          final double pathStateLogLikelihood = currentPathEdgeNode.getEdgeLogLikelihood() 
              + currentPathEdgeNode.getObsLogLikelihood()
              + currentPathEdgeNode.getTransitionLogLikelihood();
          weights.add(pathStateLogLikelihood);
          onRoadPathsTotalLogLikelihood = LogMath.add(onRoadPathsTotalLogLikelihood, 
              currentPathEdgeNode.getEdgeLogLikelihood());
          numberOfNonZeroPaths++;
        } 
        
        closedPathEdgeSet.add(currentPathEdgeNode);
        
        List<PathEdge> neighborPathEdges;
        if (currentPathEdgeNode.getPathEdge().getSegment().getNextSegment() == null) {
          neighborPathEdges = Lists.newArrayList();
          for (InferenceGraphEdge transitionEdge : this.inferenceGraph.getOutgoingTransferableEdges(
              currentPathEdgeNode.getPathEdge().getInferenceGraphEdge())) {
            InferenceGraphSegment firstSegment = Iterables.getFirst(transitionEdge.getSegments(), null);
            Preconditions.checkState(firstSegment.getLine().p0.equals(currentPathEdgeNode.getPathEdge().getLine().p1));
            neighborPathEdges.add(
              new PathEdge(firstSegment,
                  currentPathEdgeNode.getPathEdge().getDistToStartOfEdge() +
                  currentPathEdgeNode.getPathEdge().getLength(), false));
          }
        } else {
          neighborPathEdges = Collections.singletonList(
              new PathEdge(
                  currentPathEdgeNode.getPathEdge().getSegment().getNextSegment(), 
                  currentPathEdgeNode.getPathEdge().getDistToStartOfEdge() +
                  currentPathEdgeNode.getPathEdge().getLength(), false));
          
        }
        
        for (PathEdge neighborPathEdge : neighborPathEdges) {
          
          final PathEdgeNode neighborPathEdgeNode = new PathEdgeNode(neighborPathEdge, currentPathEdgeNode);
          
          MultivariateGaussian neighborEdgePathState = PathStateEstimatorPredictor.getPathEdgePredictive(
              onRoadPriorPredictiveMotionState, neighborPathEdge, obs.getObsProjected(), 
              null, this.parameters.getInitialObsFreq());
          
          MultivariateGaussian neighborEdgeObsDist = motionStateEstimatorPredictor.getObservationDistribution(
              neighborEdgePathState, neighborPathEdge);
          final double neighborObsLikelihood = neighborEdgeObsDist.getProbabilityFunction().logEvaluate(
              state.getObservation().getProjectedPoint());
          final double neighborEdgeLikelihood = PathStateEstimatorPredictor.marginalPredictiveLogLikInternal(
              onRoadPriorPredictiveMotionState, neighborPathEdge, obs.getObsProjected(), 
              null, this.parameters.getInitialObsFreq());
          final double neighborTransitionLogLikelihood =
              predictedState
                  .getEdgeTransitionParam()
                  .getConditionalDistribution()
                  .getProbabilityFunction()
                  .logEvaluate(neighborPathEdge.getInferenceGraphEdge());
          
//          if (neighborEdgePathState == null
//              || neighborEdgePathState.getMean().getElement(0) > bciDist.getUpperBound()) {
//            continue;
//          }
          
          neighborPathEdgeNode.setObsLogLikelihood(neighborObsLikelihood);
          neighborPathEdgeNode.setEdgeLogLikelihood(neighborEdgeLikelihood);
          neighborPathEdgeNode.setTransitionLogLikelihood(neighborTransitionLogLikelihood);
          neighborPathEdgeNode.setEdgeDistribution(neighborEdgePathState);
          neighborPathEdgeNode.setEdgeObsDistribution(neighborEdgeObsDist);
          neighborPathEdgeNode.setPathToEdgeTotalLogLikelihood(currentPathEdgeNode.getEdgeTotalLogLikelihood());
          
          if (closedPathEdgeSet.contains(neighborPathEdgeNode)) {
            
            /*
             * Don't revisit if we're going down in likelihood
             * Scratch that.  No loops.
             */
            continue;
//            if (neighborPathEdgeNode.getEdgeTotalLogLikelihood() < currentPathEdgeNode.getEdgeTotalLogLikelihood())
//              continue;
          } 
          
          if (!openPathEdgeQueue.contains(neighborPathEdgeNode)) {
            
            /*
             * OK, now this is a little crazy, but there may be some room to justify
             * monotonicity.
             */
            if (neighborPathEdgeNode.getEdgeTotalLogLikelihood() >= currentPathEdgeNode.getEdgeTotalLogLikelihood())
              openPathEdgeQueue.add(neighborPathEdgeNode);
          }
          
        }
      }
      
      // DEBUG REMOVE
      if (distributions.size() == 1)
        System.out.println("no on-road transitions");
  
    } else {

      MultivariateGaussian projectedDist = 
          motionStateEstimatorPredictor.createPredictiveDistribution(
            state.getMotionStateParam().getParameterPrior());
      MultivariateGaussian obsDist = 
          motionStateEstimatorPredictor.getObservationDistribution(projectedDist,
          PathEdge.nullPathEdge);
      final double beliefDistance =
          Math.min(
              StatisticsUtil
                  .getLargeNormalCovRadius((DenseMatrix) obsDist
                      .getCovariance()),
              MAX_STATE_SNAP_RADIUS);
      
      /*
       * Simply stay off-road and move forward
       */
      final PathStateDistribution offRoadPathStateDist = new PathStateDistribution(Path.nullPath, projectedDist.clone());
      distributions.add(offRoadPathStateDist);
      MultivariateGaussian offRoadObsDist = motionStateEstimatorPredictor.getObservationDistribution(
          projectedDist, PathEdge.nullPathEdge);
      final double offRoadObsLogLikelihood = offRoadObsDist.getProbabilityFunction().logEvaluate(
          state.getObservation().getProjectedPoint());
      final double offRoadTransitionLogLikelihood =
          predictedState
              .getEdgeTransitionParam()
              .getConditionalDistribution()
              .getProbabilityFunction()
              .logEvaluate(
                  InferenceGraphEdge.nullGraphEdge);
      weights.add(offRoadObsLogLikelihood + offRoadTransitionLogLikelihood);

      /*
       * Now, consider that we moved onto a road.  
       */
      for (InferenceGraphSegment segment : this.inferenceGraph.getNearbyEdges(obsDist.getMean(), beliefDistance)) {
        final Path path = new Path(Collections.singletonList(new PathEdge(segment, 0d, false)), false);
        final MultivariateGaussian roadState =  PathUtils.getRoadBeliefFromGround(projectedDist, path, true, 
            null, null);
//            sourceLocation, this.parameters.getInitialObsFreq());
        final PathStateDistribution newDist = new PathStateDistribution(path, roadState);
        MultivariateGaussian onRoadObsDist = motionStateEstimatorPredictor.getObservationDistribution(
            newDist.getGroundDistribution(), newDist.getPathState().getEdge());
        /*
         * If we projected onto a path that goes backward, then forget it.
         */
        final boolean goesBackward;
//        if (newDist.getMotionDistribution() instanceof TruncatedRoadGaussian) {
//          goesBackward = ((TruncatedRoadGaussian)newDist.getMotionDistribution()).
//              getUnTruncatedDist().getMean().getElement(1) <= 0d;
//        } else {
          goesBackward = newDist.getMotionDistribution().getMean().getElement(1) <= 0d;
//        }
        if (goesBackward)
          continue;
        final double onRoadObsLogLikelihood = onRoadObsDist.getProbabilityFunction().logEvaluate(
            state.getObservation().getProjectedPoint());
        final double onRoadTransitionLogLikelihood =
            predictedState
                .getEdgeTransitionParam()
                .getConditionalDistribution()
                .getProbabilityFunction()
                .logEvaluate(
                    newDist.getPathState().getEdge().getInferenceGraphEdge());
        
        distributions.add(newDist);
        weights.add(onRoadObsLogLikelihood + onRoadTransitionLogLikelihood);
      }
    }

    
    /*
     * Normalize over on/off-road and edge count
     */
    if (numberOfNonZeroPaths > 0 || numberOfOffRoadPaths > 0) {
      for (int i = 0; i < weights.size(); i++) {
        /*
         * Normalized within categories (off and on-road)
         */
        if (distributions.get(i).getPathState().isOnRoad()) {
//          weights.set(i, weights.get(i) - Math.log(numberOfNonZeroPaths));
          weights.set(i, weights.get(i) - onRoadPathsTotalLogLikelihood);
        } //else {
//          weights.set(i, weights.get(i) - Math.log(numberOfOffRoadPaths));
//        }
        /*
         * Normalize over categories
         */
//        if (numberOfOffRoadPaths > 0)
//          weights.set(i, weights.get(i) - Math.log(2));
      }
    }
    
    final PathStateMixtureDensityModel predictedPathStateDist =
        new PathStateMixtureDensityModel(
            distributions, Doubles.toArray(weights));
    predictedState.setPathStateParam(SimpleBayesianParameter.create(
        state.getPathStateParam().getParameterPrior().getPathState(),
        predictedPathStateDist, state.getPathStateParam().getParameterPrior()));
    
    return predictedState;
  }

  public G getInferenceGraph() {
    return inferenceGraph;
  }

  public void setInferenceGraph(G inferenceGraph) {
    this.inferenceGraph = inferenceGraph;
  }

  public VehicleStateDistributionFactory<O, G> getVehicleStateFactory() {
    return vehicleStateFactory;
  }

  public void setVehicleStateFactory(
      VehicleStateDistributionFactory<O, G> vehicleStateFactory) {
    this.vehicleStateFactory = vehicleStateFactory;
  }

  public void setInitialObservation(O initialObservation) {
    this.initialObservation = initialObservation;
  }

  public void setParameters(VehicleStateInitialParameters parameters) {
    this.parameters = parameters;
  }

}
