package azkaban.serialization.de;

import azkaban.flow.*;
import azkaban.serialization.MultipleDependencyEFSerializer;
import azkaban.serialization.Verifier;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.*;

/**
 *
 */
public class ExecutableFlowDeserializer implements Function<Map<String, Object>, ExecutableFlow>
{
    private final Function<Map<String, Object>, ExecutableFlow> jobDeserializer;

    public ExecutableFlowDeserializer(
            Function<Map<String, Object>, ExecutableFlow> jobDeserializer
    )
    {
        this.jobDeserializer = jobDeserializer;
    }

    @Override
    public ExecutableFlow apply(Map<String, Object> descriptor)
    {
        Map<String, ExecutableFlow> jobs = Maps.uniqueIndex(
                Iterables.<Map<String, Object>, ExecutableFlow>transform(
                        Verifier.getVerifiedObject(descriptor, "jobs", Map.class).values(),
                        jobDeserializer
                        ),
                new Function<ExecutableFlow, String>()
                {
                    @Override
                    public String apply(ExecutableFlow flow)
                    {
                        return flow.getName();
                    }
                }
        );

        Map<String, List<String>> dependencies = Verifier.getVerifiedObject(descriptor, "dependencies", Map.class);
        List<String> roots = Verifier.getVerifiedObject(descriptor, "root", List.class);
        String id = Verifier.getString(descriptor, "id");
        
        return buildFlow(id, roots, dependencies, jobs);
    }

    private ExecutableFlow buildFlow(
            final String id,
            Iterable<String> roots,
            final Map<String, List<String>> dependencies,
            final Map<String, ExecutableFlow> jobs
    )
    {
        final ArrayList<ExecutableFlow> executableFlows = Lists.newArrayList(
                Iterables.transform(
                        roots,
                        new Function<String, ExecutableFlow>()
                        {
                            @Override
                            public ExecutableFlow apply(String root)
                            {
                                if (dependencies.containsKey(root)) {
                                    final ExecutableFlow dependeeFlow = buildFlow(id, dependencies.get(root), dependencies, jobs);

                                    if (dependeeFlow instanceof GroupedExecutableFlow) {
                                        return new MultipleDependencyExecutableFlow(
                                                id,
                                                buildFlow(id, Arrays.asList(root), Collections.<String, List<String>>emptyMap(), jobs),
                                                (ExecutableFlow[]) dependeeFlow.getChildren().toArray()
                                        );
                                    }
                                    else {
                                        return new ComposedExecutableFlow(
                                                id,
                                                buildFlow(id, Arrays.asList(root), Collections.<String, List<String>>emptyMap(), jobs),
                                                dependeeFlow
                                        );
                                    }

                                }
                                else {
                                    if (! jobs.containsKey(root)) {
                                        throw new IllegalStateException(String.format(
                                                "Expected job[%s] in jobs list[%s]",
                                                root,
                                                jobs
                                        ));
                                    }

                                    return jobs.get(root);
                                }
                            }
                        }
                )
        );

        if (executableFlows.size() == 1) {
            return executableFlows.get(0);
        }
        else {
            return new GroupedExecutableFlow(id, executableFlows.toArray(new ExecutableFlow[executableFlows.size()]));
        }
    }
}
