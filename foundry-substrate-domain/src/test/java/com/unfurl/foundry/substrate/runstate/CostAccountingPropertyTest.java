package com.unfurl.foundry.substrate.runstate;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CostAccountingPropertyTest {

    @Property
    void costAccountingIsAssociative(@ForAll("usages") List<Usage> usages, @ForAll("splitPoints") int splitPoint) {
        int split = usages.isEmpty() ? 0 : Math.floorMod(splitPoint, usages.size() + 1);
        CostAccounting allAtOnce = fold(CostAccounting.empty(Map.of()), usages);
        CostAccounting inTwoChunks = fold(fold(CostAccounting.empty(Map.of()), usages.subList(0, split)),
                usages.subList(split, usages.size()));

        assertThat(inTwoChunks.promptTokens()).isEqualTo(allAtOnce.promptTokens());
        assertThat(inTwoChunks.completionTokens()).isEqualTo(allAtOnce.completionTokens());
        assertThat(inTwoChunks.tokensByModel()).isEqualTo(allAtOnce.tokensByModel());
        assertThat(inTwoChunks.tokensByProvider()).isEqualTo(allAtOnce.tokensByProvider());
        assertThat(inTwoChunks.estimatedCostUsd()).isEqualByComparingTo(allAtOnce.estimatedCostUsd());
    }

    @Provide
    Arbitrary<List<Usage>> usages() {
        return Arbitraries.integers().between(0, 100)
                .flatMap(prompt -> Arbitraries.integers().between(0, 100)
                        .flatMap(completion -> Arbitraries.integers().between(0, 100)
                                .map(cents -> new Usage(prompt, completion, BigDecimal.valueOf(cents, 2)))))
                .list().ofMaxSize(25);
    }

    @Provide
    Arbitrary<Integer> splitPoints() {
        return Arbitraries.integers().between(-100, 100);
    }

    private CostAccounting fold(CostAccounting accounting, List<Usage> usages) {
        CostAccounting current = accounting;
        for (Usage usage : usages) {
            current = current.add(usage.promptTokens(), usage.completionTokens(), "model", "provider", usage.cost());
        }
        return current;
    }

    private record Usage(long promptTokens, long completionTokens, BigDecimal cost) {
    }
}
