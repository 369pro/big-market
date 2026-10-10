package com.pro.test.domain.strategy;

import com.pro.domain.strategy.model.entity.StrategyRuleEntity;
import com.pro.domain.strategy.model.valobj.WeightRuleConfig;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WeightRuleConfigTest {

    @Test
    public void explicitIntervalsPreserveRawGroupsAndBounds() {
        List<WeightRuleConfig> groups = WeightRuleConfig.parse(
                "[100,+∞):1111,1112 [0,20):101,1110 [20,100):1110,1111");

        assertEquals(3, groups.size());
        WeightRuleConfig first = groups.get(0);
        assertEquals("[100,+∞):1111,1112", first.getRawGroup());
        assertEquals(100, first.getLowerBound());
        assertNull(first.getUpperBound());
        assertTrue(first.isExplicit());
        assertFalse(first.matches(99));
        assertTrue(first.matches(Integer.MAX_VALUE));

        WeightRuleConfig middle = groups.get(2);
        assertEquals(20, middle.getLowerBound());
        assertEquals(Integer.valueOf(100), middle.getUpperBound());
        assertTrue(middle.matches(20));
        assertFalse(middle.matches(100));
    }

    @Test
    public void legacyThresholdsRemainCompatibleAndKeepRawKeys() {
        StrategyRuleEntity entity = StrategyRuleEntity.builder()
                .ruleModel("rule_weight")
                .ruleValue("100:1111,1112 0:101,1110 20:1110,1111")
                .build();

        Map<String, List<Integer>> values = entity.getRuleWeightValues();

        assertEquals(3, values.size());
        assertEquals(List.of(1111, 1112), values.get("100:1111,1112"));
        assertEquals(List.of(101, 1110), values.get("0:101,1110"));
        assertEquals(List.of(1110, 1111), values.get("20:1110,1111"));
    }

    @Test
    public void invalidWeightConfigurationsAreRejected() {
        assertInvalid("[-1,20):101");
        assertInvalid("[20,20):101");
        assertInvalid("[20,10):101");
        assertInvalid("[0,20):101 [19,30):102");
        assertInvalid("[0,20):101 20:102");
        assertInvalid("[0,20):");
        assertInvalid("[0,20):101,,102");
        assertInvalid("20:101 20:102");
    }

    private void assertInvalid(String ruleValue) {
        try {
            WeightRuleConfig.parse(ruleValue);
            fail("Expected invalid rule_weight configuration: " + ruleValue);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
