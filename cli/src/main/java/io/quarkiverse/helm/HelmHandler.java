package io.quarkiverse.helm;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.quarkiverse.helm.spi.HelmChartBuildItem;
import io.quarkus.builder.BuildResult;

public class HelmHandler implements BiConsumer<Object, BuildResult> {

    @Override
    public void accept(Object context, BuildResult buildResult) {
        List<HelmChartBuildItem> charts = buildResult.consumeMulti(HelmChartBuildItem.class);
        Consumer<List<HelmChartBuildItem>> consumer = (Consumer<List<HelmChartBuildItem>>) context;
        consumer.accept(charts);
    }
}
