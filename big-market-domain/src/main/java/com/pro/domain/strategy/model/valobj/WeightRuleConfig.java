package com.pro.domain.strategy.model.valobj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed rule_weight group.
 *
 * <p>New configurations use left-closed, right-open intervals such as
 * {@code [0,20):101,1110} and {@code [100,+∞):1111}. Legacy configurations
 * use a lower-bound prefix such as {@code 20:1110,1111}; legacy groups keep
 * their historical highest-lower-bound match semantics.</p>
 */
public final class WeightRuleConfig {

    private static final String INFINITY = "+∞";
    private static final Pattern EXPLICIT_GROUP = Pattern.compile("^\\[(\\d+),(\\d+|\\+∞)\\):(.+)$");
    private static final Pattern LEGACY_GROUP = Pattern.compile("^(\\d+):(.+)$");

    private final String rawGroup;
    private final int lowerBound;
    private final Integer upperBound;
    private final List<Integer> awardIds;
    private final boolean explicit;

    private WeightRuleConfig(String rawGroup,
                             int lowerBound,
                             Integer upperBound,
                             List<Integer> awardIds,
                             boolean explicit) {
        this.rawGroup = rawGroup;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.awardIds = Collections.unmodifiableList(new ArrayList<>(awardIds));
        this.explicit = explicit;
    }

    /**
     * Parse the complete rule_value. Blank values are treated as no configured
     * groups so the caller can continue to its default chain.
     */
    public static List<WeightRuleConfig> parse(String ruleValue) {
        if (ruleValue == null || ruleValue.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] rawGroups = ruleValue.trim().split("\\s+");
        List<WeightRuleConfig> groups = new ArrayList<>(rawGroups.length);
        boolean explicitSeen = false;
        boolean legacySeen = false;
        for (String rawGroup : rawGroups) {
            WeightRuleConfig group = parseGroup(rawGroup);
            groups.add(group);
            explicitSeen |= group.explicit;
            legacySeen |= !group.explicit;
        }

        if (explicitSeen && legacySeen) {
            throw new IllegalArgumentException("rule_weight cannot mix explicit intervals and legacy thresholds");
        }

        validateRanges(groups, explicitSeen);
        return Collections.unmodifiableList(groups);
    }

    /**
     * Parse one raw group while preserving the group text used by the armory
     * and dispatch cache key.
     */
    public static WeightRuleConfig parseGroup(String rawGroup) {
        if (rawGroup == null || rawGroup.trim().isEmpty()) {
            throw new IllegalArgumentException("rule_weight group cannot be empty");
        }
        String normalizedGroup = rawGroup.trim();

        Matcher explicitMatcher = EXPLICIT_GROUP.matcher(normalizedGroup);
        if (explicitMatcher.matches()) {
            int lowerBound = parseBound(explicitMatcher.group(1), normalizedGroup);
            Integer upperBound = INFINITY.equals(explicitMatcher.group(2))
                    ? null
                    : parseBound(explicitMatcher.group(2), normalizedGroup);
            if (lowerBound < 0) {
                throw new IllegalArgumentException("rule_weight lower bound must be >= 0: " + normalizedGroup);
            }
            if (upperBound != null && upperBound <= lowerBound) {
                throw new IllegalArgumentException("rule_weight upper bound must be greater than lower bound: " + normalizedGroup);
            }
            return new WeightRuleConfig(normalizedGroup, lowerBound, upperBound,
                    parseAwardIds(explicitMatcher.group(3), normalizedGroup), true);
        }

        Matcher legacyMatcher = LEGACY_GROUP.matcher(normalizedGroup);
        if (legacyMatcher.matches()) {
            int lowerBound = parseBound(legacyMatcher.group(1), normalizedGroup);
            if (lowerBound < 0) {
                throw new IllegalArgumentException("rule_weight lower bound must be >= 0: " + normalizedGroup);
            }
            return new WeightRuleConfig(normalizedGroup, lowerBound, null,
                    parseAwardIds(legacyMatcher.group(2), normalizedGroup), false);
        }

        throw new IllegalArgumentException("invalid rule_weight group: " + normalizedGroup);
    }

    private static int parseBound(String value, String rawGroup) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid rule_weight bound in group: " + rawGroup, e);
        }
    }

    private static List<Integer> parseAwardIds(String rawAwardIds, String rawGroup) {
        if (rawAwardIds == null || rawAwardIds.trim().isEmpty()) {
            throw new IllegalArgumentException("rule_weight award pool cannot be empty: " + rawGroup);
        }

        String[] awardIdValues = rawAwardIds.split(",", -1);
        List<Integer> awardIds = new ArrayList<>(awardIdValues.length);
        for (String awardIdValue : awardIdValues) {
            String normalizedAwardId = awardIdValue.trim();
            if (normalizedAwardId.isEmpty()) {
                throw new IllegalArgumentException("rule_weight award pool cannot be empty: " + rawGroup);
            }
            try {
                awardIds.add(Integer.parseInt(normalizedAwardId));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid rule_weight award id in group: " + rawGroup, e);
            }
        }
        if (awardIds.isEmpty()) {
            throw new IllegalArgumentException("rule_weight award pool cannot be empty: " + rawGroup);
        }
        return awardIds;
    }

    private static void validateRanges(List<WeightRuleConfig> groups, boolean explicit) {
        if (!explicit) {
            Set<Integer> lowerBounds = new HashSet<>();
            for (WeightRuleConfig group : groups) {
                if (!lowerBounds.add(group.lowerBound)) {
                    throw new IllegalArgumentException("duplicate rule_weight threshold: " + group.lowerBound);
                }
            }
            return;
        }

        List<WeightRuleConfig> sortedGroups = new ArrayList<>(groups);
        sortedGroups.sort(Comparator.comparingInt(WeightRuleConfig::getLowerBound));
        for (int i = 1; i < sortedGroups.size(); i++) {
            WeightRuleConfig previous = sortedGroups.get(i - 1);
            WeightRuleConfig current = sortedGroups.get(i);
            if (previous.upperBound == null || previous.upperBound > current.lowerBound) {
                throw new IllegalArgumentException("overlapping rule_weight intervals: "
                        + previous.rawGroup + " and " + current.rawGroup);
            }
        }
    }

    /**
     * Whether this group accepts the supplied score. Legacy groups are lower
     * bounds; explicit groups additionally enforce their exclusive upper bound.
     */
    public boolean matches(int userScore) {
        if (userScore < lowerBound) {
            return false;
        }
        return !explicit || upperBound == null || userScore < upperBound;
    }

    public String getRawGroup() {
        return rawGroup;
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public Integer getUpperBound() {
        return upperBound;
    }

    public List<Integer> getAwardIds() {
        return awardIds;
    }

    public boolean isExplicit() {
        return explicit;
    }
}
