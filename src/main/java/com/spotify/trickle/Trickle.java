package com.spotify.trickle;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.*;
import com.spotify.trickle.graph.DagNode;
import com.spotify.trickle.graph.SimpleDagChecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.spotify.trickle.DagBuilder.buildDag;


/**
 * TODO: document!
 */
public class Trickle {
  static final Object DEPENDENCY_NOT_INITIALISED = new Object();

  public static <R> GraphBuilder<R> graph(Class<R> returnClass) {
    return new GraphBuilder<>();
  }

  public static <R> GraphBuilder<R> in(Name input) {
    return new GraphBuilder<R>().inputs(input);
  }

  public static class GraphBuilder<R> {
    private final Set<Name> inputs;
    @VisibleForTesting
    final Set<NodeBuilder<?, R>> nodes;
    private final SimpleDagChecker checker = new SimpleDagChecker();


    private GraphBuilder(Set<Name> inputs, Set<NodeBuilder<?, R>> nodes) {
      this.inputs = ImmutableSet.copyOf(inputs);
      this.nodes = Sets.newHashSet(nodes);
    }

    public GraphBuilder() {
      this (ImmutableSet.<Name>of(), ImmutableSet.<NodeBuilder<?, R>>of());
    }

    public <T> GraphBuilder<R> inputs(Name... dependencies) {
      ImmutableSet.Builder<Name> builder = ImmutableSet.builder();

      builder.addAll(inputs);
      builder.addAll(Arrays.asList(dependencies));

      return new GraphBuilder<>(builder.build(), nodes);
    }

    public <N> NodeBuilder<N, R> call(Node0<N> node) {
      NodeBuilder<N, R> nodeBuilder = new NodeBuilder<>(this, node);
      nodes.add(nodeBuilder);

      return nodeBuilder;
    }

    public <A1, N> NodeBuilder1<A1,N, R> call(Node1<A1, N> node) {
      NodeBuilder1<A1, N, R> nodeBuilder = new NodeBuilder1<>(this, node);
      nodes.add(nodeBuilder);

      return nodeBuilder;
    }

    public <A1,A2,N> NodeBuilder2<A1,A2,N, R> call(Node2<A1, A2, N> node) {
      NodeBuilder2<A1,A2,N, R> nodeBuilder = new NodeBuilder2<>(this, node);
      nodes.add(nodeBuilder);

      return nodeBuilder;
    }

    public <A1,A2,A3,N> NodeBuilder3<A1, A2, A3, N, R> call(Node3<A1, A2, A3, N> node) {
      NodeBuilder3<A1, A2, A3, N, R> nodeBuilder = new NodeBuilder3<>(this, node);
      nodes.add(nodeBuilder);

      return nodeBuilder;
    }

    public TrickleGraph<R> build() {
      Map<Name,Object> inputDependencies =
          Maps.asMap(inputs, new Function<Name, Object>() {
            @Override
            public Object apply(Name input) {
              return DEPENDENCY_NOT_INITIALISED;
            }
          });

      Node<R> result1 = findSink(nodes);

      return new TrickleGraph<>(inputDependencies, result1, buildNodes(nodes));
    }

    private Node<R> findSink(Set<NodeBuilder<?, R>> nodes) {
      Set<DagNode<NodeBuilder<?, R>>> dag = buildDag(nodes);

      Set<DagNode<NodeBuilder<?, R>>> sinks = checker.findSinks(dag);

      if (sinks.size() != 1) {
        throw new TrickleException("There must be only a single sink node which is the one that returns the result, found: " + sinks);
      }

      // TODO: check type safety, somehow...
      return (Node<R>) sinks.iterator().next().getData().node;
    }


    private Map<Node<?>, ConnectedNode> buildNodes(Iterable<NodeBuilder<?, R>> nodeBuilders) {
      ImmutableMap.Builder<Node<?>, ConnectedNode> builder = ImmutableMap.builder();

      for (NodeBuilder<?, R> nodeBuilder : nodeBuilders) {
        builder.put(nodeBuilder.node, nodeBuilder.connect());
      }

      return builder.build();
    }
  }

  public static class NodeBuilder1<A1,N, R> extends NodeBuilder<N, R> {
    private NodeBuilder1(GraphBuilder<R> graphBuilder, Node<N> node) {
      super(graphBuilder, node);
    }

    public NodeBuilder1<A1, N, R> with(Value<A1> arg1) {
      return (NodeBuilder1<A1, N, R>) super.with(arg1);
    }
  }

  public static class NodeBuilder2<A1,A2,N, R> extends NodeBuilder<N, R> {
    private NodeBuilder2(GraphBuilder<R> graphBuilder, Node<N> node) {
      super(graphBuilder, node);
    }

    public NodeBuilder2<A1,A2,N, R> with(Value<A1> arg1, Value<A2> arg2) {
      return (NodeBuilder2<A1, A2, N, R>) super.with(arg1, arg2);
    }
  }

  public static class NodeBuilder3<A1,A2,A3,N, R> extends NodeBuilder<N, R> {
    private NodeBuilder3(GraphBuilder<R> graphBuilder, Node<N> node) {
      super(graphBuilder, node);
    }

    public NodeBuilder3<A1,A2,A3,N, R> with(Value<A1> arg1, Value<A2> arg2, Value<A3> arg3) {
      return (NodeBuilder3<A1, A2, A3, N, R>) super.with(arg1, arg2, arg3);
    }
  }


  public static class NodeBuilder<N, R> {
    final GraphBuilder<R> graphBuilder;
    private final Node<N> node;
    private final List<Object> inputs;
    private final List<Node<?>> predecessors;
    private N defaultValue = null;

    private NodeBuilder(GraphBuilder<R> graphBuilder, Node<N> node) {
      this.graphBuilder = graphBuilder;
      this.node = node;
      inputs = new ArrayList<>();
      predecessors = new ArrayList<>();
    }

    protected NodeBuilder<N, R> with(Object... inputs) {
      this.inputs.addAll(Arrays.asList(inputs));
      return this;
    }

    public NodeBuilder<N, R> fallback(N value) {
      defaultValue = value;
      return this;
    }

    public <N1> NodeBuilder<N1, R> call(Node0<N1> put1) {
      return graphBuilder.call(put1);
    }

    public <A1, N1> NodeBuilder1<A1, N1, R> call(Node1<A1, N1> put1) {
      return graphBuilder.call(put1);
    }

    public <A1, A2, N1> NodeBuilder2<A1, A2, N1, R> call(Node2<A1, A2, N1> put1) {
      return graphBuilder.call(put1);
    }

    public <A1, A2, A3, N1> NodeBuilder3<A1, A2, A3, N1, R> call(Node3<A1, A2, A3, N1> put1) {
      return graphBuilder.call(put1);
    }

    public NodeBuilder<N, R> after(Node<?>... predecessors) {
      this.predecessors.addAll(Arrays.asList(predecessors));
      return this;
    }

    public TrickleGraph<R> output(Node<R> result1) {
      return graphBuilder.build();
    }

    private ConnectedNode connect() {
      return new ConnectedNode(node, asDeps(inputs), predecessors, Optional.fromNullable(defaultValue));
    }

    private List<Dep<?>> asDeps(List<Object> inputs) {
      List<Dep<?>> result = Lists.newArrayList();

      for (Object input : inputs) {
        if (input instanceof Name) {
          result.add(new BindingDep<>((Name) input, Object.class));
        }
        else if (input instanceof Node<?>) {
          result.add(new NodeDep((Node<?>) input, Object.class));
        }
        else {
          throw new RuntimeException("illegal input object: " + input);
        }
      }

      return result;
    }
  }
}
