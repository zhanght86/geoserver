/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * Copyright (C) 2007-2008-2009 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.sldservice.utils.classifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.filter.function.Classifier;
import org.geotools.filter.function.ExplicitClassifier;
import org.geotools.filter.function.RangedClassifier;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.styling.Mark;
import org.geotools.styling.Rule;
import org.geotools.styling.StyleBuilder;
import org.geotools.styling.StyleFactory;
import org.geotools.styling.Symbolizer;
import org.geotools.util.factory.GeoTools;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Function;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;

/**
 * Creates a List of Rule using different classification methods Sets up only filter not Symbolyzer
 * Available Classification: Quantile,Unique Interval & Equal Interval
 *
 * @author kappu
 */
public class RulesBuilder {

    private static final Logger LOGGER = Logger.getLogger(RulesBuilder.class.toString());

    private FilterFactory2 ff;

    private StyleFactory styleFactory;

    private StyleBuilder sb;

    private double strokeWeight = 1;
    private Color strokeColor = Color.BLACK;
    private int pointSize = 15;
    private boolean includeStrokeForPoints = false;

    public RulesBuilder() {
        ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
        styleFactory = CommonFactoryFinder.getStyleFactory(GeoTools.getDefaultHints());
        sb = new StyleBuilder();
    }

    public void setStrokeWeight(double strokeWeight) {
        this.strokeWeight = strokeWeight;
    }

    public void setStrokeColor(Color strokeColor) {
        if (strokeColor != null) {
            this.strokeColor = strokeColor;
        }
    }

    public void setPointSize(int pointSize) {
        this.pointSize = pointSize;
    }

    public void setIncludeStrokeForPoints(boolean includeStrokeForPoints) {
        this.includeStrokeForPoints = includeStrokeForPoints;
    }

    private List<Rule> getRules(
            FeatureCollection features,
            String property,
            Class<?> propertyType,
            int classNumber,
            boolean open,
            boolean normalize,
            String functionName) {
        try {
            final Function classify =
                    ff.function(functionName, ff.property(property), ff.literal(classNumber));
            Classifier groups = (Classifier) classify.evaluate(features);
            if (groups instanceof RangedClassifier)
                if (open)
                    return openRangedRules(
                            (RangedClassifier) groups, property, propertyType, normalize);
                else
                    return closedRangedRules(
                            (RangedClassifier) groups, property, propertyType, normalize);
            else if (groups instanceof ExplicitClassifier)
                return this.explicitRules((ExplicitClassifier) groups, property, propertyType);

        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.log(
                        Level.INFO,
                        "Failed to build "
                                + functionName
                                + " Classification"
                                + e.getLocalizedMessage(),
                        e);
        }
        return null;
    }

    /** Generate a List of rules using quantile classification Sets up only filter not symbolizer */
    public List<Rule> quantileClassification(
            FeatureCollection features,
            String property,
            Class<?> propertyType,
            int classNumber,
            boolean open,
            boolean normalize) {
        return getRules(features, property, propertyType, classNumber, open, normalize, "Quantile");
    }

    /**
     * Generate a List of rules using equal interval classification Sets up only filter not
     * symbolizer
     *
     * @param features
     * @param property
     * @param classNumber
     */
    public List<Rule> equalIntervalClassification(
            FeatureCollection features,
            String property,
            Class<?> propertyType,
            int intervals,
            boolean open,
            boolean normalize) {
        return getRules(
                features, property, propertyType, intervals, open, normalize, "EqualInterval");
    }

    /**
     * Generate a List of rules using unique interval classification Sets up only filter not
     * symbolizer
     *
     * @param features
     * @param property
     */
    public List<Rule> uniqueIntervalClassification(
            FeatureCollection features,
            String property,
            Class<?> propertyType,
            int intervals,
            boolean normalize)
            throws IllegalArgumentException {
        List<Rule> rules =
                getRules(
                        features,
                        property,
                        propertyType,
                        features.size(),
                        false,
                        normalize,
                        "UniqueInterval");
        if (intervals > 0 && rules.size() > intervals) {
            throw new IllegalArgumentException("Intervals: " + rules.size());
        }
        return rules;
    }

    /**
     * Generate a List of rules using Jenks Natural Breaks classification Sets up only filter not
     * symbolizer
     *
     * @param features
     * @param property
     * @param classNumber
     */
    public List<Rule> jenksClassification(
            FeatureCollection features,
            String property,
            Class<?> propertyType,
            int classNumber,
            boolean open,
            boolean normalize) {
        return getRules(features, property, propertyType, classNumber, open, normalize, "Jenks");
    }

    /**
     * Generate a List of rules using Equal Area classification. Sets up only filter not symbolizer.
     *
     * @param features
     * @param property
     * @param classNumber
     */
    public List<Rule> equalAreaClassification(
            FeatureCollection features,
            String property,
            Class<?> propertyType,
            int classNumber,
            boolean open,
            boolean normalize) {
        return getRules(
                features, property, propertyType, classNumber, open, normalize, "EqualArea");
    }

    /**
     * Generate Polygon Symbolyzer for each rule in list Fill color is choose from rampcolor
     *
     * @param rules
     * @param fillRamp
     * @param reverseColors
     */
    public void polygonStyle(List<Rule> rules, ColorRamp fillRamp, boolean reverseColors) {

        Iterator<Rule> it;
        Rule rule;
        Iterator<Color> colors;
        Color color;

        try {
            // adjust the colorRamp with the correct number of classes
            fillRamp.setNumClasses(rules.size());
            if (reverseColors) {
                fillRamp.revert();
            }
            colors = fillRamp.getRamp().iterator();

            it = rules.iterator();
            while (it.hasNext() && colors.hasNext()) {
                color = colors.next();
                rule = it.next();
                rule.setSymbolizers(
                        new Symbolizer[] {
                            sb.createPolygonSymbolizer(
                                    strokeWeight < 0
                                            ? null
                                            : sb.createStroke(strokeColor, strokeWeight),
                                    sb.createFill(color))
                        });
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.log(
                        Level.INFO,
                        "Failed to build polygon Symbolizer" + e.getLocalizedMessage(),
                        e);
        }
    }

    /**
     * Generate Polygon Symbolyzer for each rule in list Fill color is choose from rampcolor
     *
     * @param rules
     * @param ramp
     */
    public void pointStyle(List<Rule> rules, ColorRamp fillRamp, boolean reverseColors) {

        Iterator<Rule> it;
        Rule rule;
        Iterator<Color> colors;
        Color color;

        try {
            // adjust the colorRamp with the correct number of classes
            fillRamp.setNumClasses(rules.size());
            if (reverseColors) {
                fillRamp.revert();
            }
            colors = fillRamp.getRamp().iterator();

            it = rules.iterator();
            while (it.hasNext() && colors.hasNext()) {
                color = colors.next();
                rule = it.next();
                // sb.createStroke(Color.BLACK,1),

                Mark mark =
                        sb.createMark(
                                StyleBuilder.MARK_CIRCLE,
                                sb.createFill(color),
                                includeStrokeForPoints && strokeWeight >= 0
                                        ? sb.createStroke(strokeColor, strokeWeight)
                                        : null);
                rule.setSymbolizers(
                        new Symbolizer[] {
                            sb.createPointSymbolizer(
                                    sb.createGraphic(null, mark, null, 1.0, pointSize, 0.0))
                        });
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.log(
                        Level.INFO,
                        "Failed to build polygon Symbolizer" + e.getLocalizedMessage(),
                        e);
        }
    }

    /**
     * Generate Line Symbolyzer for each rule in list Stroke color is choose from rampcolor
     *
     * @param rules
     * @param fillRamp
     * @param reverseColors
     */
    public void lineStyle(List<Rule> rules, ColorRamp fillRamp, boolean reverseColors) {

        Iterator<Rule> it;
        Rule rule;
        Iterator<Color> colors;
        Color color;

        try {
            // adjust the colorRamp with the correct number of classes
            fillRamp.setNumClasses(rules.size());
            if (reverseColors) {
                fillRamp.revert();
            }
            colors = fillRamp.getRamp().iterator();

            it = rules.iterator();
            while (it.hasNext() && colors.hasNext()) {
                color = colors.next();
                rule = it.next();
                rule.setSymbolizers(new Symbolizer[] {sb.createLineSymbolizer(color)});
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.log(
                        Level.INFO, "Failed to build Line Symbolizer" + e.getLocalizedMessage(), e);
        }
    }

    public StyleFactory getStyleFactory() {
        return this.styleFactory;
    }

    /**
     * Generate Rules from Rangedclassifier groups build a List of rules
     *
     * @param groups
     * @param property
     */
    public List<Rule> openRangedRules(
            RangedClassifier groups, String property, Class<?> propertyType, boolean normalize) {

        Rule r;
        Filter f;
        List<Rule> list = new ArrayList();
        Expression att = normalizeProperty(ff.property(property), propertyType, normalize);

        try {
            /* First class */
            r = styleFactory.createRule();
            /*if (groups.getMin(0).equals(groups.getMax(0))) {
                f = ff.equals(att, ff.literal(groups.getMax(0)));
                r.setFilter(f);
                r.setTitle(ff.literal(groups.getMax(0)).toString());
                list.add(r);
            } else {
                f = ff.lessOrEqual(att, ff.literal(groups.getMax(0)));
                r.setFilter(f);
                r.setTitle(" <= " + ff.literal(groups.getMax(0)));
                list.add(r);
            }*/
            f = ff.lessOrEqual(att, ff.literal(groups.getMax(0)));
            r.setFilter(f);
            r.setTitle(" <= " + ff.literal(groups.getMax(0)));
            list.add(r);
            for (int i = 1; i < groups.getSize() - 1; i++) {
                r = styleFactory.createRule();
                if (groups.getMin(i).equals(groups.getMax(i))) {
                    f = ff.equals(att, ff.literal(groups.getMax(i)));
                    r.setTitle(ff.literal(groups.getMin(i)).toString());
                    r.setFilter(f);
                    list.add(r);
                } else {
                    f =
                            ff.and(
                                    ff.greater(att, ff.literal(groups.getMin(i))),
                                    ff.lessOrEqual(att, ff.literal(groups.getMax(i))));
                    r.setTitle(
                            " > "
                                    + ff.literal(groups.getMin(i))
                                    + " AND <= "
                                    + ff.literal(groups.getMax(i)));
                    r.setFilter(f);
                    list.add(r);
                }
            }
            /* Last class */
            r = styleFactory.createRule();
            /*if (groups.getMin(groups.getSize() - 1).equals(groups.getMax(groups.getSize() - 1))) {
                f = ff.equals(att, ff.literal(groups.getMin(groups.getSize() - 1)));
                r.setFilter(f);
                r.setTitle(ff.literal(groups.getMin(groups.getSize() - 1)).toString());
                list.add(r);
            } else {
                f = ff.greater(att, ff.literal(groups.getMin(groups.getSize() - 1)));
                r.setFilter(f);
                r.setTitle(" > " + ff.literal(groups.getMin(groups.getSize() - 1)));
                list.add(r);
            }*/
            f = ff.greater(att, ff.literal(groups.getMin(groups.getSize() - 1)));
            r.setFilter(f);
            r.setTitle(" > " + ff.literal(groups.getMin(groups.getSize() - 1)));
            list.add(r);
            return list;
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.log(
                        Level.INFO,
                        "Failed to build Open Ranged rules" + e.getLocalizedMessage(),
                        e);
        }
        return null;
    }

    private Expression normalizeProperty(
            PropertyName property, Class<?> propertyType, boolean normalize) {
        if (normalize
                && (Integer.class.isAssignableFrom(propertyType)
                        || Long.class.isAssignableFrom(propertyType))) {
            return ff.function("parseDouble", property);
        }
        return property;
    }

    /**
     * Generate Rules from Rangedclassifier groups build a List of rules
     *
     * @param groups
     * @param property
     */
    public List<Rule> closedRangedRules(
            RangedClassifier groups, String property, Class<?> propertyType, boolean normalize) {

        Rule r;
        Filter f;
        List<Rule> list = new ArrayList();
        Expression att = normalizeProperty(ff.property(property), propertyType, normalize);
        try {
            /* First class */
            r = styleFactory.createRule();
            for (int i = 0; i < groups.getSize(); i++) {
                r = styleFactory.createRule();
                if (i > 0 && groups.getMax(i).equals(groups.getMax(i - 1))) continue;
                if (groups.getMin(i).equals(groups.getMax(i))) {
                    f = ff.equals(att, ff.literal(groups.getMin(i)));
                    r.setTitle(ff.literal(groups.getMin(i)).toString());
                    r.setFilter(f);
                    list.add(r);
                } else {
                    f =
                            ff.and(
                                    i == 0
                                            ? ff.greaterOrEqual(att, ff.literal(groups.getMin(i)))
                                            : ff.greater(att, ff.literal(groups.getMin(i))),
                                    ff.lessOrEqual(att, ff.literal(groups.getMax(i))));
                    r.setTitle(
                            " >= "
                                    + ff.literal(groups.getMin(i))
                                    + " AND <= "
                                    + ff.literal(groups.getMax(i)));
                    r.setFilter(f);
                    list.add(r);
                }
            }
            return list;
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.log(
                        Level.INFO,
                        "Failed to build closed Ranged Rules" + e.getLocalizedMessage(),
                        e);
        }
        return null;
    }

    /**
     * Generate Rules from Explicit classifier groups build a List of rules
     *
     * @param groups
     * @param property
     */
    public List<Rule> explicitRules(
            ExplicitClassifier groups, String property, Class<?> propertyType) {

        Rule r;
        Filter f;
        List<Rule> list = new ArrayList();
        PropertyName att = ff.property(property);
        String szFilter = "";
        String szTitle = "";
        Literal val;

        try {
            for (int i = 0; i < groups.getSize(); i++) {
                r = styleFactory.createRule();
                Set ls = groups.getValues(i);
                Iterator it = ls.iterator();
                val = ff.literal(it.next());
                szFilter = att + "=\'" + val + "\'";
                szTitle = "" + val;

                while (it.hasNext()) {
                    val = ff.literal(it.next());
                    szFilter += " OR " + att + "=\'" + val + "\'";
                    szTitle += " OR " + val;
                }
                f = CQL.toFilter(szFilter);
                r.setTitle(szTitle);
                r.setFilter(f);
                list.add(r);
            }
            return list;
        } catch (CQLException e) {
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.log(
                        Level.INFO, "Failed to build explicit Rules" + e.getLocalizedMessage(), e);
        }
        return null;
    }
}
